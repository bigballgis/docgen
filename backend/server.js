const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const config = require('./config');
const logger = require('./utils/logger');

// 初始化数据库（创建表等）
const db = require('./db/init');
const dbModule = require('./db/init');

// 初始化默认管理员账户
const authService = require('./services/authService');
authService.initDefaultAdmin();

// Kafka 服务
const kafkaService = require('./services/kafkaService');

const app = express();

// ==================== Prometheus 指标收集器 ====================

const metrics = {
  // HTTP 请求计数器: key = "method:path:status" -> count
  requestCounts: {},
  // 文档生成耗时直方图
  generateDurations: [],
  // 生成耗时桶
  generateDurationBuckets: { '0.1': 0, '0.5': 0, '1': 0, '+Inf': 0 },
  generateDurationSum: 0,
  generateDurationCount: 0,
  // 错误计数
  errorCount: 0,
  // 活跃用户集合 (简易实现，记录最近 5 分钟活跃用户)
  activeUsers: new Map(),
};

/**
 * 记录 HTTP 请求指标
 */
function recordRequest(method, path, status) {
  const key = `${method}:${path}:${status}`;
  metrics.requestCounts[key] = (metrics.requestCounts[key] || 0) + 1;
  if (status >= 400) {
    metrics.errorCount++;
  }
}

/**
 * 记录文档生成耗时
 */
function recordGenerateDuration(durationSeconds) {
  metrics.generateDurations.push(durationSeconds);
  metrics.generateDurationSum += durationSeconds;
  metrics.generateDurationCount++;

  // 更新桶计数
  if (durationSeconds <= 0.1) metrics.generateDurationBuckets['0.1']++;
  if (durationSeconds <= 0.5) metrics.generateDurationBuckets['0.5']++;
  if (durationSeconds <= 1) metrics.generateDurationBuckets['1']++;
  metrics.generateDurationBuckets['+Inf']++;
}

/**
 * 记录活跃用户
 */
function recordActiveUser(userId) {
  if (userId) {
    metrics.activeUsers.set(userId, Date.now());
  }
}

/**
 * 清理过期活跃用户（超过 5 分钟）
 */
function cleanupActiveUsers() {
  const now = Date.now();
  for (const [userId, timestamp] of metrics.activeUsers) {
    if (now - timestamp > 5 * 60 * 1000) {
      metrics.activeUsers.delete(userId);
    }
  }
}

// 每 60 秒清理一次过期活跃用户
setInterval(cleanupActiveUsers, 60000);

// 导出 metrics 供其他模块使用
app.locals.metrics = metrics;
app.locals.recordRequest = recordRequest;
app.locals.recordGenerateDuration = recordGenerateDuration;
app.locals.recordActiveUser = recordActiveUser;

// ========== 安全中间件 ==========
// Helmet 安全头（配置 CSP 允许 Euro-Office iframe）
app.use(helmet({
  contentSecurityPolicy: {
    directives: {
      defaultSrc: ["'self'"],
      scriptSrc: ["'self'", "'unsafe-inline'", "'unsafe-eval'"],
      styleSrc: ["'self'", "'unsafe-inline'"],
      imgSrc: ["'self'", "data:", "blob:"],
      fontSrc: ["'self'", "data:"],
      connectSrc: ["'self'"],
      frameSrc: ["'self'", ...getEuroOfficeFrameSrc()],
      frameAncestors: ["'self'", ...getEuroOfficeFrameSrc()],
    },
  },
  crossOriginEmbedderPolicy: false,
}));

// CORS
app.use(cors());

// 解析请求体
app.use(express.json({ limit: '50mb' }));
app.use(express.urlencoded({ extended: true, limit: '50mb' }));

// API 版本头
app.use((req, res, next) => {
  res.setHeader('X-API-Version', '1.0.0');
  next();
});

// 请求日志（结构化）+ 指标收集中间件
app.use((req, res, next) => {
  const start = Date.now();
  res.on('finish', () => {
    const duration = Date.now() - start;
    logger.info('request', {
      method: req.method,
      url: req.originalUrl,
      statusCode: res.statusCode,
      duration: `${duration}ms`,
      ip: req.ip || req.headers['x-forwarded-for'] || '',
      userId: req.user ? req.user.id : null,
      tenantId: req.tenantId || null,
    });
    // 记录 Prometheus 指标
    recordRequest(req.method, req.originalUrl, res.statusCode);
    // 记录活跃用户
    if (req.user && req.user.id) {
      recordActiveUser(req.user.id);
    }
  });
  next();
});

// ========== 静态文件服务（供 Euro-Office 访问临时文件）==========
const path = require('path');
app.use('/uploads', express.static(path.join(__dirname, '..', 'uploads'), { fallthrough: false }));
app.use('/outputs', express.static(path.join(__dirname, '..', 'outputs'), { fallthrough: false }));

// ========== 注册路由 ==========

// v1 路由（主路由）
const templatesRouter = require('./routes/templates');
const documentsRouter = require('./routes/documents');
const authRouter = require('./routes/auth');
const editorRouter = require('./routes/editor');
const tenantsRouter = require('./routes/tenants');
const versionsRouter = require('./routes/versions');
const fragmentsRouter = require('./routes/fragments');
const compositionsRouter = require('./routes/compositions');

app.use('/api/v1/templates', compositionsRouter);  // 编排路由必须在 templates 之前，避免 :id 吞掉 composition 路径
app.use('/api/v1/templates', templatesRouter);
app.use('/api/v1/documents', documentsRouter);
app.use('/api/v1/auth', authRouter);
app.use('/api/v1/editor', editorRouter);
app.use('/api/v1/tenants', tenantsRouter);
app.use('/api/v1/fragments', fragmentsRouter);
app.use('/api/v1', versionsRouter);

// 审计日志查询路由（仅 admin）
const { requireAuth, requireRole } = require('./middleware/auth');
const auditService = require('./services/auditService');
const { success, error } = require('./utils/response');

app.get('/api/v1/audit-logs', requireAuth, requireRole('admin'), (req, res) => {
  try {
    const { page, size, userId, action, startDate, endDate } = req.query;
    const result = auditService.getAuditLogs({ page, size, userId, action, startDate, endDate });
    return success(res, result);
  } catch (err) {
    logger.error('获取审计日志失败', { error: err.message });
    return error(res, 500, err.message || '获取审计日志失败');
  }
});

// ========== 兼容重定向：/api/* -> /api/v1/* ==========
app.use('/api/templates', (req, res) => {
  res.redirect(301, `/api/v1/templates${req.originalUrl.replace('/api/templates', '')}`);
});
app.use('/api/documents', (req, res) => {
  res.redirect(301, `/api/v1/documents${req.originalUrl.replace('/api/documents', '')}`);
});
app.use('/api/auth', (req, res) => {
  res.redirect(301, `/api/v1/auth${req.originalUrl.replace('/api/auth', '')}`);
});
app.use('/api/editor', (req, res) => {
  res.redirect(301, `/api/v1/editor${req.originalUrl.replace('/api/editor', '')}`);
});
app.use('/api/tenants', (req, res) => {
  res.redirect(301, `/api/v1/tenants${req.originalUrl.replace('/api/tenants', '')}`);
});

// 健康检查（同时支持 /api/health 和 /api/v1/health）
function healthHandler(req, res) {
  res.json({
    code: 200,
    message: 'ok',
    data: {
      status: 'running',
      version: '2.0.0',
      apiVersion: '1.0.0',
      timestamp: new Date().toISOString(),
      database: dbModule.getDbType ? dbModule.getDbType() : 'sqlite',
      services: {
        redis: config.redis.enabled ? 'connected' : 'disabled (using local DB)',
        minio: config.minio.enabled ? 'connected' : 'disabled (using local filesystem)',
        kafka: kafkaService.isKafkaConnected() ? 'connected' : 'disabled (sync mode)',
        euroOffice: config.euroOffice.enabled ? 'connected' : 'disabled',
      },
    },
  });
}

app.get('/api/v1/health', healthHandler);
app.get('/api/health', healthHandler);

// ==================== Prometheus Metrics 端点 ====================

app.get('/api/v1/metrics', (req, res) => {
  let templatesTotal = 0;
  let documentsTotal = 0;

  try {
    try {
      const tResult = db.prepare("SELECT COUNT(*) as count FROM templates WHERE deleted_at IS NULL").get();
      templatesTotal = tResult ? tResult.count : 0;
    } catch (e) {
      // 表可能没有 deleted_at 列，回退
      try {
        const tResult = db.prepare("SELECT COUNT(*) as count FROM templates").get();
        templatesTotal = tResult ? tResult.count : 0;
      } catch (e2) { /* ignore */ }
    }
    try {
      const dResult = db.prepare("SELECT COUNT(*) as count FROM documents WHERE deleted_at IS NULL").get();
      documentsTotal = dResult ? dResult.count : 0;
    } catch (e) {
      try {
        const dResult = db.prepare("SELECT COUNT(*) as count FROM documents").get();
        documentsTotal = dResult ? dResult.count : 0;
      } catch (e2) { /* ignore */ }
    }
  } catch (e) {
    // 数据库可能未初始化
  }

  // 清理活跃用户
  cleanupActiveUsers();

  let output = '';

  // 模板总数
  output += '# HELP docgen_templates_total Total number of templates\n';
  output += '# TYPE docgen_templates_total gauge\n';
  output += `docgen_templates_total{tenant="default"} ${templatesTotal}\n\n`;

  // 文档总数
  output += '# HELP docgen_documents_total Total number of documents\n';
  output += '# TYPE docgen_documents_total gauge\n';
  output += `docgen_documents_total{tenant="default"} ${documentsTotal}\n\n`;

  // 文档生成耗时直方图
  output += '# HELP docgen_document_generate_duration_seconds Document generation duration\n';
  output += '# TYPE docgen_document_generate_duration_seconds histogram\n';
  output += `docgen_document_generate_duration_seconds_bucket{le="0.1"} ${metrics.generateDurationBuckets['0.1']}\n`;
  output += `docgen_document_generate_duration_seconds_bucket{le="0.5"} ${metrics.generateDurationBuckets['0.5']}\n`;
  output += `docgen_document_generate_duration_seconds_bucket{le="1"} ${metrics.generateDurationBuckets['1']}\n`;
  output += `docgen_document_generate_duration_seconds_bucket{le="+Inf"} ${metrics.generateDurationBuckets['+Inf']}\n`;
  output += `docgen_document_generate_duration_seconds_sum ${metrics.generateDurationSum.toFixed(3)}\n`;
  output += `docgen_document_generate_duration_seconds_count ${metrics.generateDurationCount}\n\n`;

  // HTTP 请求计数器
  output += '# HELP docgen_http_requests_total Total HTTP requests\n';
  output += '# TYPE docgen_http_requests_total counter\n';
  for (const [key, count] of Object.entries(metrics.requestCounts)) {
    const [method, path, status] = key.split(':');
    output += `docgen_http_requests_total{method="${method}",path="${path}",status="${status}"} ${count}\n`;
  }
  output += '\n';

  // 活跃用户数
  output += '# HELP docgen_active_users Number of active users\n';
  output += '# TYPE docgen_active_users gauge\n';
  output += `docgen_active_users ${metrics.activeUsers.size}\n\n`;

  // 错误总数
  output += '# HELP docgen_http_errors_total Total HTTP errors\n';
  output += '# TYPE docgen_http_errors_total counter\n';
  output += `docgen_http_errors_total ${metrics.errorCount}\n`;

  res.set('Content-Type', 'text/plain; version=0.0.4; charset=utf-8');
  res.send(output);
});

// 404 处理
app.use((req, res) => {
  res.status(404).json({ code: 404, message: '接口不存在' });
});

// 全局错误处理
app.use((err, req, res, next) => {
  logger.error('服务器错误', { error: err.message, stack: err.stack, url: req.originalUrl });

  // Multer 文件大小错误
  if (err.code === 'LIMIT_FILE_SIZE') {
    return res.status(400).json({ code: 400, message: '文件大小超出限制（最大 50MB）' });
  }

  // Multer 文件类型错误
  if (err.message && err.message.includes('仅支持')) {
    return res.status(400).json({ code: 400, message: err.message });
  }

  res.status(500).json({ code: 500, message: '服务器内部错误' });
});

/**
 * 获取 Euro-Office iframe 允许的源地址
 */
function getEuroOfficeFrameSrc() {
  if (config.euroOffice.enabled && config.euroOffice.baseUrl) {
    try {
      const url = new URL(config.euroOffice.baseUrl);
      return [url.origin];
    } catch (e) {
      // URL 解析失败，忽略
    }
  }
  return [];
}

// 启动服务
const server = app.listen(config.port, async () => {
  // 如果数据库需要异步初始化（无 deasync 时的 PG 模式），在这里执行
  if (db._needsAsyncInit && dbModule.asyncReinit) {
    await dbModule.asyncReinit();
  }

  // 尝试初始化 Kafka（如果配置启用）
  if (config.kafka.enabled) {
    try {
      const connected = await kafkaService.initKafka(config.kafka.brokers);
      if (connected) {
        logger.info('Kafka 已连接，文档生成使用异步模式');
      } else {
        logger.warn('Kafka 连接失败，文档生成降级为同步模式');
      }
    } catch (err) {
      logger.warn('Kafka 初始化异常，文档生成降级为同步模式', { error: err.message });
    }
  } else {
    logger.info('Kafka 未启用，文档生成使用同步模式');
  }

  const dbType = dbModule.getDbType ? dbModule.getDbType() : 'sqlite';
  logger.info('服务启动', {
    version: '2.0.0',
    apiVersion: '1.0.0',
    port: config.port,
    database: dbType,
    redis: config.redis.enabled ? 'ON' : 'OFF',
    minio: config.minio.enabled ? 'ON' : 'OFF',
    kafka: kafkaService.isKafkaConnected() ? 'ON' : 'OFF',
    euroOffice: config.euroOffice.enabled ? 'ON' : 'OFF',
  });
  console.log('='.repeat(50));
  console.log(`  文档生成平台后端服务已启动 (企业版 v2.0.0)`);
  console.log(`  地址: http://localhost:${config.port}`);
  console.log(`  API:  http://localhost:${config.port}/api/v1`);
  console.log(`  兼容: http://localhost:${config.port}/api (301 -> /api/v1)`);
  console.log(`  认证: admin / admin123`);
  console.log(`  数据库: ${dbType === 'postgresql' ? 'PostgreSQL' : 'SQLite'}`);
  console.log(`  多租户: 已启用 (默认租户: default)`);
  console.log(`  审批流: 已启用 (draft -> pending -> published)`);
  console.log(`  降级模式: Redis=${config.redis.enabled ? 'ON' : 'OFF'}, MinIO=${config.minio.enabled ? 'ON' : 'OFF'}, Kafka=${kafkaService.isKafkaConnected() ? 'ON' : 'OFF'}, Euro-Office=${config.euroOffice.enabled ? 'ON' : 'OFF'}`);
  console.log('='.repeat(50));
});

// ========== 优雅关闭 ==========
async function gracefulShutdown(signal) {
  console.log(`\n收到 ${signal} 信号，开始优雅关闭...`);

  // 断开 Kafka 连接
  try {
    await kafkaService.disconnectKafka();
  } catch (e) {
    // 忽略
  }

  // 关闭 HTTP 服务器
  server.close(() => {
    console.log('HTTP 服务器已关闭');

    // 关闭数据库连接
    if (db && db.close) {
      try {
        db.close();
      } catch (e) {
        // 忽略
      }
    }

    console.log('服务已优雅关闭');
    process.exit(0);
  });

  // 10 秒后强制退出
  setTimeout(() => {
    console.error('优雅关闭超时，强制退出');
    process.exit(1);
  }, 10000);
}

process.on('SIGTERM', () => gracefulShutdown('SIGTERM'));
process.on('SIGINT', () => gracefulShutdown('SIGINT'));

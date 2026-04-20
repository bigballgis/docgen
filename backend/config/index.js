const path = require('path');

module.exports = {
  // 服务端口
  port: process.env.PORT || 3001,

  // JWT 密钥（生产环境必须通过环境变量设置）
  jwtSecret: process.env.JWT_SECRET || (() => {
    if (process.env.NODE_ENV === 'production') {
      console.error('\x1b[31m[安全错误] 生产环境必须设置 JWT_SECRET 环境变量！服务拒绝启动。\x1b[0m');
      process.exit(1);
    }
    console.warn('\x1b[33m[安全警告] 使用默认 JWT 密钥（仅限开发环境）。生产环境请设置 JWT_SECRET 环境变量。\x1b[0m');
    return 'docgen-dev-only-jwt-secret-do-not-use-in-production';
  })(),

  // JWT 过期时间
  jwtExpiresIn: process.env.JWT_EXPIRES_IN || '24h',

  // 数据库路径（SQLite 降级模式使用）
  dbPath: path.join(__dirname, '..', 'data', 'docgen.db'),

  // 数据库配置（支持 SQLite / PostgreSQL 双模式）
  database: {
    type: process.env.DB_TYPE || 'postgresql', // 'sqlite' | 'postgresql'
    host: process.env.DB_HOST || '127.0.0.1',
    port: parseInt(process.env.DB_PORT, 10) || 5432,
    name: process.env.DB_NAME || 'docgen',
    user: process.env.DB_USER || 'docgen',
    password: process.env.DB_PASSWORD || 'docgen_secret_2026',
    maxPool: parseInt(process.env.DB_POOL_MAX, 10) || 10,
  },

  // 模板文件上传目录
  uploadDir: path.join(__dirname, '..', 'uploads', 'templates'),

  // 文档输出目录
  outputDir: path.join(__dirname, '..', 'outputs'),

  // 最大文件上传大小（50MB）
  maxFileSize: 50 * 1024 * 1024,

  // ========== 外部服务配置（可选，不可用时优雅降级） ==========

  // Redis 配置
  redis: {
    enabled: false,
    host: process.env.REDIS_HOST || '127.0.0.1',
    port: parseInt(process.env.REDIS_PORT, 10) || 6379,
    password: process.env.REDIS_PASSWORD || '',
    db: parseInt(process.env.REDIS_DB, 10) || 0,
  },

  // MinIO 配置
  minio: {
    enabled: false,
    endPoint: process.env.MINIO_ENDPOINT || '127.0.0.1',
    port: parseInt(process.env.MINIO_PORT, 10) || 9000,
    accessKey: process.env.MINIO_ACCESS_KEY || 'minioadmin',
    secretKey: process.env.MINIO_SECRET_KEY || 'minioadmin',
    bucket: process.env.MINIO_BUCKET || 'docgen',
    useSSL: process.env.MINIO_USE_SSL === 'true',
  },

  // Kafka 配置
  kafka: {
    enabled: false,
    brokers: (process.env.KAFKA_BROKERS || '127.0.0.1:9092').split(','),
    clientId: process.env.KAFKA_CLIENT_ID || 'docgen-backend',
    topic: process.env.KAFKA_TOPIC || 'docgen-tasks',
  },

  // Euro-Office 配置
  euroOffice: {
    enabled: false,
    baseUrl: process.env.EURO_OFFICE_BASE_URL || '',
    url: process.env.EURO_OFFICE_URL || process.env.EURO_OFFICE_BASE_URL || '',
    apiKey: process.env.EURO_OFFICE_API_KEY || '',
    callbackUrl: process.env.EURO_OFFICE_CALLBACK_URL || '',
    docBuilderTimeout: parseInt(process.env.EURO_OFFICE_TIMEOUT || '30000', 10),
  },

  // 公共访问 URL（用于 Euro-Office 访问本地文件）
  publicUrl: process.env.PUBLIC_URL || 'http://localhost:3001',
};

const Database = require('better-sqlite3');
const path = require('path');
const fs = require('fs');
const config = require('../config');
const { createPgAdapter, testPgConnection } = require('./pg');

// ========== SQLite 建表语句 ==========
const SQLITE_CREATE_TENANTS = `
  CREATE TABLE IF NOT EXISTS tenants (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,
    code TEXT NOT NULL UNIQUE,
    status TEXT NOT NULL DEFAULT 'active',
    config TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
  );
`;

const SQLITE_CREATE_TABLES = `
  CREATE TABLE IF NOT EXISTS templates (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    description TEXT,
    file_name TEXT NOT NULL,
    original_file_name TEXT NOT NULL,
    fields TEXT,
    category TEXT,
    user_id INTEGER,
    tenant_id TEXT NOT NULL DEFAULT 'default',
    status TEXT NOT NULL DEFAULT 'draft',
    version INTEGER NOT NULL DEFAULT 1,
    published_at DATETIME,
    approved_by INTEGER,
    approved_at DATETIME,
    reject_reason TEXT,
    deleted_at DATETIME,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
  );
`;

const SQLITE_CREATE_USERS = `
  CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    role TEXT NOT NULL DEFAULT 'user',
    tenant_id TEXT NOT NULL DEFAULT 'default',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
  );
`;

const SQLITE_CREATE_AUDIT_LOGS = `
  CREATE TABLE IF NOT EXISTS audit_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER,
    username TEXT,
    action TEXT NOT NULL,
    resource TEXT,
    resource_id TEXT,
    ip TEXT,
    user_agent TEXT,
    details TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
  );
`;

const SQLITE_CREATE_DOCUMENTS = `
  CREATE TABLE IF NOT EXISTS documents (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    template_id INTEGER,
    template_name TEXT,
    file_key TEXT NOT NULL,
    file_name TEXT NOT NULL,
    output_format TEXT NOT NULL DEFAULT 'docx',
    status TEXT NOT NULL DEFAULT 'pending',
    user_id INTEGER,
    tenant_id TEXT NOT NULL DEFAULT 'default',
    deleted_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (template_id) REFERENCES templates(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
  );
`;

const SQLITE_CREATE_TEMPLATE_VERSIONS = `
  CREATE TABLE IF NOT EXISTS template_versions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    template_id INTEGER NOT NULL,
    version INTEGER NOT NULL,
    file_path TEXT NOT NULL,
    file_size INTEGER,
    change_note TEXT,
    created_by INTEGER,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (template_id) REFERENCES templates(id)
  );
  CREATE INDEX IF NOT EXISTS idx_template_versions_template_id ON template_versions(template_id);
`;

const SQLITE_CREATE_TEMPLATE_APPROVALS = `
  CREATE TABLE IF NOT EXISTS template_approvals (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    template_id INTEGER NOT NULL,
    action TEXT NOT NULL,
    reviewer_id INTEGER NOT NULL,
    comment TEXT,
    status TEXT NOT NULL DEFAULT 'pending',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
  );
`;

const SQLITE_CREATE_FRAGMENTS = `
  CREATE TABLE IF NOT EXISTS fragments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    description TEXT DEFAULT '',
    category TEXT DEFAULT '',
    content_html TEXT NOT NULL DEFAULT '',
    content_docx_path TEXT DEFAULT '',
    fields TEXT DEFAULT '[]',
    tags TEXT DEFAULT '[]',
    tenant_id TEXT DEFAULT 'default',
    status TEXT DEFAULT 'draft',
    current_version INTEGER DEFAULT 1,
    created_by INTEGER,
    created_at TEXT DEFAULT (datetime('now')),
    updated_at TEXT DEFAULT (datetime('now')),
    deleted_at TEXT
  );
  CREATE INDEX IF NOT EXISTS idx_fragments_tenant_id ON fragments(tenant_id);
  CREATE INDEX IF NOT EXISTS idx_fragments_category ON fragments(category);
  CREATE INDEX IF NOT EXISTS idx_fragments_status ON fragments(status);
`;

const SQLITE_CREATE_FRAGMENT_VERSIONS = `
  CREATE TABLE IF NOT EXISTS fragment_versions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    fragment_id INTEGER NOT NULL,
    version INTEGER NOT NULL,
    content_html TEXT NOT NULL DEFAULT '',
    content_docx_path TEXT DEFAULT '',
    fields TEXT DEFAULT '[]',
    change_note TEXT DEFAULT '',
    created_by INTEGER,
    created_at TEXT DEFAULT (datetime('now')),
    FOREIGN KEY (fragment_id) REFERENCES fragments(id)
  );
  CREATE INDEX IF NOT EXISTS idx_fragment_versions_fragment_id ON fragment_versions(fragment_id);
`;

const SQLITE_CREATE_TEMPLATE_COMPOSITIONS = `
  CREATE TABLE IF NOT EXISTS template_compositions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    template_id INTEGER NOT NULL,
    fragment_id INTEGER NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    section_title TEXT DEFAULT '',
    enabled INTEGER DEFAULT 1,
    created_at TEXT DEFAULT (datetime('now')),
    FOREIGN KEY (template_id) REFERENCES templates(id),
    FOREIGN KEY (fragment_id) REFERENCES fragments(id)
  );
  CREATE INDEX IF NOT EXISTS idx_template_compositions_template_id ON template_compositions(template_id);
`;

// ========== PostgreSQL 建表语句 ==========

const PG_CREATE_TENANTS = `
  CREATE TABLE IF NOT EXISTS tenants (
    id SERIAL PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    code TEXT NOT NULL UNIQUE,
    status TEXT NOT NULL DEFAULT 'active',
    config TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
  );
`;

const PG_CREATE_FRAGMENTS = `
  CREATE TABLE IF NOT EXISTS fragments (
    id SERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT DEFAULT '',
    category TEXT DEFAULT '',
    content_html TEXT NOT NULL DEFAULT '',
    content_docx_path TEXT DEFAULT '',
    fields TEXT DEFAULT '[]',
    tags TEXT DEFAULT '[]',
    tenant_id TEXT DEFAULT 'default',
    status TEXT DEFAULT 'draft',
    current_version INTEGER DEFAULT 1,
    created_by INTEGER,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    deleted_at TIMESTAMP WITH TIME ZONE
  );
  CREATE INDEX IF NOT EXISTS idx_fragments_tenant_id ON fragments(tenant_id);
  CREATE INDEX IF NOT EXISTS idx_fragments_category ON fragments(category);
  CREATE INDEX IF NOT EXISTS idx_fragments_status ON fragments(status);
`;

const PG_CREATE_FRAGMENT_VERSIONS = `
  CREATE TABLE IF NOT EXISTS fragment_versions (
    id SERIAL PRIMARY KEY,
    fragment_id INTEGER NOT NULL,
    version INTEGER NOT NULL,
    content_html TEXT NOT NULL DEFAULT '',
    content_docx_path TEXT DEFAULT '',
    fields TEXT DEFAULT '[]',
    change_note TEXT DEFAULT '',
    created_by INTEGER,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
  );
  CREATE INDEX IF NOT EXISTS idx_fragment_versions_fragment_id ON fragment_versions(fragment_id);
  DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fragment_versions_fragment_id_fkey') THEN
      ALTER TABLE fragment_versions ADD CONSTRAINT fragment_versions_fragment_id_fkey FOREIGN KEY (fragment_id) REFERENCES fragments(id);
    END IF;
  $$;
`;

const PG_CREATE_TEMPLATE_COMPOSITIONS = `
  CREATE TABLE IF NOT EXISTS template_compositions (
    id SERIAL PRIMARY KEY,
    template_id INTEGER NOT NULL,
    fragment_id INTEGER NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    section_title TEXT DEFAULT '',
    enabled INTEGER DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
  );
  CREATE INDEX IF NOT EXISTS idx_template_compositions_template_id ON template_compositions(template_id);
  DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'template_compositions_template_id_fkey') THEN
      ALTER TABLE template_compositions ADD CONSTRAINT template_compositions_template_id_fkey FOREIGN KEY (template_id) REFERENCES templates(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'template_compositions_fragment_id_fkey') THEN
      ALTER TABLE template_compositions ADD CONSTRAINT template_compositions_fragment_id_fkey FOREIGN KEY (fragment_id) REFERENCES fragments(id);
    END IF;
  $$;
`;

const PG_CREATE_TABLES = `
  CREATE TABLE IF NOT EXISTS templates (
    id SERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT,
    file_name TEXT NOT NULL,
    original_file_name TEXT NOT NULL,
    fields TEXT,
    category TEXT,
    user_id INTEGER,
    tenant_id TEXT NOT NULL DEFAULT 'default',
    status TEXT NOT NULL DEFAULT 'draft',
    version INTEGER NOT NULL DEFAULT 1,
    published_at TIMESTAMP WITH TIME ZONE,
    approved_by INTEGER,
    approved_at TIMESTAMP WITH TIME ZONE,
    reject_reason TEXT,
    deleted_at TIMESTAMP WITH TIME ZONE,
    create_time TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    update_time TIMESTAMP WITH TIME ZONE DEFAULT NOW()
  );
  DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'templates_user_id_fkey') THEN
      ALTER TABLE templates ADD CONSTRAINT templates_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id);
    END IF;
  $$;
`;

const PG_CREATE_USERS = `
  CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    username TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    role TEXT NOT NULL DEFAULT 'user',
    tenant_id TEXT NOT NULL DEFAULT 'default',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
  );
`;

const PG_CREATE_AUDIT_LOGS = `
  CREATE TABLE IF NOT EXISTS audit_logs (
    id SERIAL PRIMARY KEY,
    user_id INTEGER,
    username TEXT,
    action TEXT NOT NULL,
    resource TEXT,
    resource_id TEXT,
    ip TEXT,
    user_agent TEXT,
    details TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
  );
`;

const PG_CREATE_DOCUMENTS = `
  CREATE TABLE IF NOT EXISTS documents (
    id SERIAL PRIMARY KEY,
    template_id INTEGER,
    template_name TEXT,
    file_key TEXT NOT NULL,
    file_name TEXT NOT NULL,
    output_format TEXT NOT NULL DEFAULT 'docx',
    status TEXT NOT NULL DEFAULT 'pending',
    user_id INTEGER,
    tenant_id TEXT NOT NULL DEFAULT 'default',
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
  );
  DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'documents_template_id_fkey') THEN
      ALTER TABLE documents ADD CONSTRAINT documents_template_id_fkey FOREIGN KEY (template_id) REFERENCES templates(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'documents_user_id_fkey') THEN
      ALTER TABLE documents ADD CONSTRAINT documents_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id);
    END IF;
  $$;
`;

const PG_CREATE_TEMPLATE_VERSIONS = `
  CREATE TABLE IF NOT EXISTS template_versions (
    id SERIAL PRIMARY KEY,
    template_id INTEGER NOT NULL,
    version INTEGER NOT NULL,
    file_path TEXT NOT NULL,
    file_size INTEGER,
    change_note TEXT,
    created_by INTEGER,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
  );
  CREATE INDEX IF NOT EXISTS idx_template_versions_template_id ON template_versions(template_id);
  DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'template_versions_template_id_fkey') THEN
      ALTER TABLE template_versions ADD CONSTRAINT template_versions_template_id_fkey FOREIGN KEY (template_id) REFERENCES templates(id);
    END IF;
  $$;
`;

const PG_CREATE_TEMPLATE_APPROVALS = `
  CREATE TABLE IF NOT EXISTS template_approvals (
    id SERIAL PRIMARY KEY,
    template_id INTEGER NOT NULL,
    action TEXT NOT NULL,
    reviewer_id INTEGER NOT NULL,
    comment TEXT,
    status TEXT NOT NULL DEFAULT 'pending',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
  );
`;

// ========== SQLite 初始化 ==========
function initSqlite() {
  // 确保数据目录存在
  const dbDir = path.dirname(config.dbPath);
  if (!fs.existsSync(dbDir)) {
    fs.mkdirSync(dbDir, { recursive: true });
  }

  const db = new Database(config.dbPath);

  // 启用 WAL 模式，提升并发性能
  db.pragma('journal_mode = WAL');
  db.pragma('foreign_keys = ON');

  // 创建表
  db.exec(SQLITE_CREATE_TENANTS);
  db.exec(SQLITE_CREATE_TABLES);
  db.exec(SQLITE_CREATE_USERS);
  db.exec(SQLITE_CREATE_DOCUMENTS);
  db.exec(SQLITE_CREATE_AUDIT_LOGS);
  db.exec(SQLITE_CREATE_TEMPLATE_VERSIONS);
  db.exec(SQLITE_CREATE_TEMPLATE_APPROVALS);
  db.exec(SQLITE_CREATE_FRAGMENTS);
  db.exec(SQLITE_CREATE_FRAGMENT_VERSIONS);
  db.exec(SQLITE_CREATE_TEMPLATE_COMPOSITIONS);

  // 插入默认租户
  try {
    const defaultTenant = db.prepare("SELECT id FROM tenants WHERE code = 'default'").get();
    if (!defaultTenant) {
      db.prepare("INSERT INTO tenants (name, code) VALUES ('默认租户', 'default')").run();
      console.log('[DB/SQLite] 默认租户已创建');
    }
  } catch (e) {
    console.warn('[DB/SQLite] 插入默认租户失败:', e.message);
  }

  // 为已有 templates 表添加 user_id 列（如果不存在）
  try {
    const columns = db.prepare("PRAGMA table_info(templates)").all();
    const hasUserId = columns.some((col) => col.name === 'user_id');
    if (!hasUserId) {
      db.exec('ALTER TABLE templates ADD COLUMN user_id INTEGER');
      console.log('[DB/SQLite] templates 表已添加 user_id 列');
    }
  } catch (e) {
    // 忽略错误，表可能已包含该列
  }

  // 为已有表添加 tenant_id 列（如果不存在，兼容旧数据库）
  const tenantIdMigration = [
    { table: 'templates', defaultVal: "'default'" },
    { table: 'documents', defaultVal: "'default'" },
    { table: 'users', defaultVal: "'default'" },
  ];
  for (const { table, defaultVal } of tenantIdMigration) {
    try {
      const columns = db.prepare(`PRAGMA table_info(${table})`).all();
      const hasTenantId = columns.some((col) => col.name === 'tenant_id');
      if (!hasTenantId) {
        db.exec(`ALTER TABLE ${table} ADD COLUMN tenant_id TEXT NOT NULL DEFAULT ${defaultVal}`);
        console.log(`[DB/SQLite] ${table} 表已添加 tenant_id 列`);
      }
    } catch (e) {
      // 列已存在，忽略
    }
  }

  // 为已有 templates 表添加审批相关列（如果不存在，兼容旧数据库）
  const approvalMigration = [
    { col: 'status', def: "TEXT NOT NULL DEFAULT 'draft'" },
    { col: 'version', def: 'INTEGER NOT NULL DEFAULT 1' },
    { col: 'published_at', def: 'DATETIME' },
    { col: 'approved_by', def: 'INTEGER' },
    { col: 'approved_at', def: 'DATETIME' },
    { col: 'reject_reason', def: 'TEXT' },
  ];
  for (const { col, def } of approvalMigration) {
    try {
      const columns = db.prepare('PRAGMA table_info(templates)').all();
      const hasCol = columns.some((c) => c.name === col);
      if (!hasCol) {
        db.exec(`ALTER TABLE templates ADD COLUMN ${col} ${def}`);
        console.log(`[DB/SQLite] templates 表已添加 ${col} 列`);
      }
    } catch (e) {
      // 列已存在，忽略
    }
  }

  // 为 templates 表添加 current_version 列（版本控制）
  try {
    const columns = db.prepare('PRAGMA table_info(templates)').all();
    const hasCurrentVersion = columns.some((c) => c.name === 'current_version');
    if (!hasCurrentVersion) {
      db.exec("ALTER TABLE templates ADD COLUMN current_version INTEGER NOT NULL DEFAULT 1");
      console.log('[DB/SQLite] templates 表已添加 current_version 列');
    }
  } catch (e) {
    // 列已存在，忽略
  }

  // 创建 tenant_id 索引
  try {
    db.exec('CREATE INDEX IF NOT EXISTS idx_templates_tenant_id ON templates(tenant_id)');
    db.exec('CREATE INDEX IF NOT EXISTS idx_documents_tenant_id ON documents(tenant_id)');
    db.exec('CREATE INDEX IF NOT EXISTS idx_users_tenant_id ON users(tenant_id)');
    db.exec('CREATE INDEX IF NOT EXISTS idx_templates_status ON templates(status)');
    db.exec('CREATE INDEX IF NOT EXISTS idx_template_approvals_template_id ON template_approvals(template_id)');
  } catch (e) {
    console.warn('[DB/SQLite] 创建索引失败:', e.message);
  }

  // 为已有表添加 deleted_at 列（软删除支持，兼容旧数据库）
  const softDeleteMigration = [
    { table: 'templates' },
    { table: 'documents' },
  ];
  for (const { table } of softDeleteMigration) {
    try {
      const columns = db.prepare(`PRAGMA table_info(${table})`).all();
      const hasDeletedAt = columns.some((col) => col.name === 'deleted_at');
      if (!hasDeletedAt) {
        db.exec(`ALTER TABLE ${table} ADD COLUMN deleted_at DATETIME`);
        console.log(`[DB/SQLite] ${table} 表已添加 deleted_at 列（软删除支持）`);
      }
    } catch (e) {
      // 列已存在，忽略
    }
  }

  return db;
}

// ========== PostgreSQL 初始化 ==========
function initPostgresql(pgAdapter) {
  // PostgreSQL 建表顺序：先建 users（被引用），再建 templates 和 documents
  pgAdapter.exec(PG_CREATE_USERS);
  pgAdapter.exec(PG_CREATE_TENANTS);
  pgAdapter.exec(PG_CREATE_TABLES);
  pgAdapter.exec(PG_CREATE_DOCUMENTS);
  pgAdapter.exec(PG_CREATE_AUDIT_LOGS);
  pgAdapter.exec(PG_CREATE_TEMPLATE_VERSIONS);
  pgAdapter.exec(PG_CREATE_TEMPLATE_APPROVALS);
  pgAdapter.exec(PG_CREATE_FRAGMENTS);
  pgAdapter.exec(PG_CREATE_FRAGMENT_VERSIONS);
  pgAdapter.exec(PG_CREATE_TEMPLATE_COMPOSITIONS);

  // 插入默认租户
  try {
    const existing = pgAdapter.prepare("SELECT id FROM tenants WHERE code = 'default'").get();
    if (!existing) {
      pgAdapter.prepare("INSERT INTO tenants (name, code) VALUES ('默认租户', 'default')").run();
    }
  } catch (e) {
    // 忽略
  }
}

// ========== 主初始化逻辑 ==========
let db;
let activeDbType = 'sqlite';

async function initializeDatabase() {
  const requestedType = config.database.type;

  if (requestedType === 'postgresql') {
    console.log('[DB] 正在尝试连接 PostgreSQL...');
    console.log(`[DB]   host=${config.database.host}:${config.database.port}, database=${config.database.name}, user=${config.database.user}`);

    const pgAvailable = await testPgConnection(config.database);

    if (pgAvailable) {
      try {
        const pgAdapter = createPgAdapter(config.database);
        initPostgresql(pgAdapter);
        db = pgAdapter;
        activeDbType = 'postgresql';
        console.log('\x1b[32m[DB] PostgreSQL 连接成功，使用 PostgreSQL 模式\x1b[0m');
        return;
      } catch (err) {
        console.error('\x1b[33m[DB] PostgreSQL 初始化失败:', err.message, '\x1b[0m');
        console.log('\x1b[33m[DB] 降级到 SQLite 模式\x1b[0m');
      }
    } else {
      console.warn('\x1b[33m[DB] PostgreSQL 连接失败，降级到 SQLite 模式\x1b[0m');
    }
  }

  // SQLite 模式（默认 / 降级）
  db = initSqlite();
  activeDbType = 'sqlite';
  console.log('[DB] 使用 SQLite 模式');
  console.log(`[DB]   数据库路径: ${config.dbPath}`);
}

// 同步执行初始化（兼容现有启动流程）
// 使用 IIFE + deasync 或直接同步调用
;(function () {
  // SQLite 初始化是同步的，可以直接执行
  // PostgreSQL 需要异步检测，但我们通过顶层 await 模拟
  // 由于 require() 是同步的，我们在这里先做 SQLite 初始化作为默认
  // 如果后续检测到 PG 可用，会在 server.js 中通过异步方式切换

  const requestedType = config.database.type;

  if (requestedType === 'postgresql') {
    // 尝试同步检测 PG（使用 deasync）
    let deasync;
    try {
      deasync = require('deasync');
    } catch (e) {
      deasync = null;
    }

    if (deasync) {
      console.log('[DB] 正在尝试连接 PostgreSQL...');
      console.log(`[DB]   host=${config.database.host}:${config.database.port}, database=${config.database.name}, user=${config.database.user}`);

      let pgAvailable = false;
      let testDone = false;

      testPgConnection(config.database).then((result) => {
        pgAvailable = result;
        testDone = true;
      }).catch(() => {
        testDone = true;
      });

      while (!testDone) {
        deasync.runLoopOnce();
      }

      if (pgAvailable) {
        try {
          const pgAdapter = createPgAdapter(config.database);
          initPostgresql(pgAdapter);
          db = pgAdapter;
          activeDbType = 'postgresql';
          console.log('\x1b[32m[DB] PostgreSQL 连接成功，使用 PostgreSQL 模式\x1b[0m');
        } catch (err) {
          console.error('\x1b[33m[DB] PostgreSQL 初始化失败:', err.message, '\x1b[0m');
          console.log('\x1b[33m[DB] 降级到 SQLite 模式\x1b[0m');
          db = initSqlite();
          activeDbType = 'sqlite';
          console.log('[DB] 使用 SQLite 模式');
          console.log(`[DB]   数据库路径: ${config.dbPath}`);
        }
      } else {
        console.warn('\x1b[33m[DB] PostgreSQL 连接失败，降级到 SQLite 模式\x1b[0m');
        db = initSqlite();
        activeDbType = 'sqlite';
        console.log('[DB] 使用 SQLite 模式');
        console.log(`[DB]   数据库路径: ${config.dbPath}`);
      }
    } else {
      // 无 deasync，使用异步初始化（需要上层配合）
      console.warn('\x1b[33m[DB] 未安装 deasync，PostgreSQL 同步模式不可用\x1b[0m');
      console.warn('\x1b[33m[DB] 降级到 SQLite 模式（安装 deasync 可启用 PostgreSQL: npm install deasync）\x1b[0m');
      db = initSqlite();
      activeDbType = 'sqlite';
      console.log('[DB] 使用 SQLite 模式');
      console.log(`[DB]   数据库路径: ${config.dbPath}`);

      // 设置异步初始化标记，供 server.js 使用
      db._needsAsyncInit = true;
    }
  } else {
    // 明确指定 SQLite
    db = initSqlite();
    activeDbType = 'sqlite';
    console.log('[DB] 使用 SQLite 模式');
    console.log(`[DB]   数据库路径: ${config.dbPath}`);
  }
})();

/**
 * 异步重新初始化数据库（用于无 deasync 时的 PG 初始化）
 * @returns {Promise<boolean>} 是否成功切换到 PG
 */
async function asyncReinit() {
  if (activeDbType === 'postgresql') return true;

  if (config.database.type !== 'postgresql') return false;

  console.log('[DB] 异步尝试连接 PostgreSQL...');
  const pgAvailable = await testPgConnection(config.database);

  if (pgAvailable) {
    try {
      // 关闭旧的 SQLite 连接
      if (db && db.close) {
        try { db.close(); } catch (e) { /* 忽略 */ }
      }

      const pgAdapter = createPgAdapter(config.database);
      initPostgresql(pgAdapter);
      db = pgAdapter;
      activeDbType = 'postgresql';
      console.log('\x1b[32m[DB] PostgreSQL 异步连接成功，已切换到 PostgreSQL 模式\x1b[0m');
      return true;
    } catch (err) {
      console.error('\x1b[33m[DB] PostgreSQL 异步初始化失败:', err.message, '\x1b[0m');
    }
  } else {
    console.warn('\x1b[33m[DB] PostgreSQL 异步连接失败，保持 SQLite 模式\x1b[0m');
  }

  return false;
}

/**
 * 获取当前数据库类型
 * @returns {string} 'sqlite' | 'postgresql'
 */
function getDbType() {
  return activeDbType;
}

module.exports = db;
module.exports.getDbType = getDbType;
module.exports.asyncReinit = asyncReinit;
module.exports.initializeDatabase = initializeDatabase;

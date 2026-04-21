const { Pool } = require('pg');

/**
 * PostgreSQL 适配器
 *
 * 提供与 better-sqlite3 兼容的同步 API 接口，内部使用 pg 连接池。
 * 通过 deasync 实现同步等待，使上层代码无需修改即可切换数据库。
 *
 * 关键适配点：
 * - ? 占位符 → $1, $2, ... 占位符
 * - lastInsertRowid → 通过 RETURNING id 获取
 * - named params (@name) → $1, $2, ... 按参数名排序
 */

// 尝试加载 deasync 用于同步等待
let deasync;
try {
  deasync = require('deasync');
} catch (e) {
  deasync = null;
}

/**
 * 将 SQLite 的 ? 占位符转换为 PostgreSQL 的 $1, $2, ... 占位符
 * @param {string} sql - SQLite 风格 SQL
 * @returns {string} PostgreSQL 风格 SQL
 */
function convertPlaceholders(sql) {
  let index = 0;
  return sql.replace(/\?/g, () => {
    index++;
    return `$${index}`;
  });
}

/**
 * 将 SQLite 的命名参数 (@name) 转换为 PostgreSQL 的 $1, $2, ... 占位符
 * 同时将参数对象转换为按序排列的数组
 * @param {string} sql - SQLite 风格 SQL（含 @name 占位符）
 * @param {Object} params - 命名参数对象
 * @returns {{ sql: string, params: Array }} 转换后的 SQL 和参数数组
 */
function convertNamedParams(sql, params) {
  if (!params || typeof params !== 'object' || Array.isArray(params)) {
    // 如果不是命名参数对象，走普通占位符转换
    return {
      sql: convertPlaceholders(sql),
      params: params ? (Array.isArray(params) ? params : [params]) : [],
    };
  }

  // 提取所有命名参数名，保持出现顺序
  const paramNames = [];
  const regex = /@(\w+)/g;
  let match;
  while ((match = regex.exec(sql)) !== null) {
    const name = match[1];
    if (!paramNames.includes(name)) {
      paramNames.push(name);
    }
  }

  // 转换 SQL
  let index = 0;
  const convertedSql = sql.replace(/@(\w+)/g, () => {
    index++;
    return `$${index}`;
  });

  // 构建参数数组
  const paramArray = paramNames.map((name) => params[name]);

  return { sql: convertedSql, params: paramArray };
}

/**
 * 同步等待 Promise（使用 deasync）
 * @param {Promise} promise
 * @returns {*} Promise 的结果
 */
function syncAwait(promise) {
  if (deasync) {
    let done = false;
    let result;
    let error;

    promise.then(
      (res) => {
        done = true;
        result = res;
      },
      (err) => {
        done = true;
        error = err;
      }
    );

    while (!done) {
      deasync.runLoopOnce();
    }

    if (error) throw error;
    return result;
  } else {
    // 无 deasync 时，使用 Node.js 的同步循环等待（不推荐，仅作降级）
    // 这种方式在大多数情况下可以工作，但不如 deasync 稳定
    let done = false;
    let result;
    let error;

    promise.then(
      (res) => {
        done = true;
        result = res;
      },
      (err) => {
        done = true;
        error = err;
      }
    );

    // 使用 Atomics 实现同步等待（Node 10+）
    const { performance } = require('perf_hooks');
    const start = performance.now();
    const TIMEOUT = 30000; // 30 秒超时

    while (!done) {
      if (performance.now() - start > TIMEOUT) {
        throw new Error('PostgreSQL 查询超时（30s）。建议安装 deasync 包以获得更好的同步支持：npm install deasync');
      }
      // 让出事件循环
      require('child_process').execSync('echo > /dev/null', { stdio: 'ignore' });
    }

    if (error) throw error;
    return result;
  }
}

/**
 * PgStatement — 模拟 better-sqlite3 的 Statement 接口
 */
class PgStatement {
  constructor(pool, sql) {
    this.pool = pool;
    this.originalSql = sql;
    this._converted = null;
  }

  /**
   * 检测 SQL 是否使用命名参数
   */
  _isNamedParams(params) {
    return params && typeof params === 'object' && !Array.isArray(params);
  }

  /**
   * 获取转换后的 SQL 和参数
   * @param {*} params - 参数，可以是命名参数对象、数组（来自展开运算符）、或单个值
   */
  _prepareParams(params) {
    if (this._isNamedParams(params)) {
      return convertNamedParams(this.originalSql, params);
    }
    // params 可能是数组（来自 ...args 展开）或单个值
    const paramArray = Array.isArray(params) ? params : (params !== undefined ? [params] : []);
    return {
      sql: convertPlaceholders(this.originalSql),
      params: paramArray,
    };
  }

  /**
   * 执行 INSERT/UPDATE/DELETE，返回 { lastInsertRowid, changes }
   * 模拟 better-sqlite3 的 run() 返回值
   */
  run(...args) {
    // run() 通常传入单个对象（命名参数）或单个值
    const params = args.length === 1 ? args[0] : args;
    const { sql, params: pgParams } = this._prepareParams(params);

    // 检查是否是 INSERT 语句，如果是则添加 RETURNING id
    const isInsert = /^\s*INSERT\s/i.test(sql);
    const finalSql = isInsert ? sql.replace(/;?\s*$/, ' RETURNING id') : sql;

    const result = syncAwait(this.pool.query(finalSql, pgParams));

    if (isInsert && result.rows && result.rows.length > 0) {
      return {
        lastInsertRowid: result.rows[0].id,
        changes: result.rowCount,
      };
    }

    return {
      lastInsertRowid: undefined,
      changes: result.rowCount,
    };
  }

  /**
   * 查询单行，返回对象或 undefined
   */
  get(...args) {
    // get() 可能传入单个值、多个值（展开运算符）或命名参数对象
    const params = args.length === 1 && this._isNamedParams(args[0]) ? args[0] : args;
    const { sql, params: pgParams } = this._prepareParams(params);

    const result = syncAwait(this.pool.query(sql, pgParams));

    if (result.rows && result.rows.length > 0) {
      return result.rows[0];
    }
    return undefined;
  }

  /**
   * 查询所有行，返回数组
   */
  all(...args) {
    // all() 可能传入多个值（展开运算符）或命名参数对象
    const params = args.length === 1 && this._isNamedParams(args[0]) ? args[0] : args;
    const { sql, params: pgParams } = this._prepareParams(params);

    const result = syncAwait(this.pool.query(sql, pgParams));

    return result.rows || [];
  }
}

/**
 * 创建 PostgreSQL 数据库适配器
 * @param {Object} dbConfig - 数据库配置
 * @returns {Object} 适配器实例
 */
function createPgAdapter(dbConfig) {
  const pool = new Pool({
    host: dbConfig.host,
    port: dbConfig.port,
    database: dbConfig.name,
    user: dbConfig.user,
    password: dbConfig.password,
    max: dbConfig.maxPool,
    idleTimeoutMillis: 30000,
    connectionTimeoutMillis: 5000,
  });

  // 处理连接池错误
  pool.on('error', (err) => {
    console.error('\x1b[31m[DB/PG] 连接池错误:', err.message, '\x1b[0m');
  });

  const adapter = {
    type: 'postgresql',
    pool,

    /**
     * 准备 SQL 语句（兼容 better-sqlite3 接口）
     */
    prepare(sql) {
      return new PgStatement(pool, sql);
    },

    /**
     * 执行原始 SQL
     */
    exec(sql) {
      syncAwait(pool.query(sql));
    },

    /**
     * 获取数据库类型
     */
    getDbType() {
      return 'postgresql';
    },

    /**
     * 关闭连接池
     */
    async close() {
      await pool.end();
    },
  };

  return adapter;
}

/**
 * 测试 PostgreSQL 连接是否可用
 * @param {Object} dbConfig - 数据库配置
 * @returns {Promise<boolean>} 是否可用
 */
async function testPgConnection(dbConfig) {
  const testPool = new Pool({
    host: dbConfig.host,
    port: dbConfig.port,
    database: dbConfig.name,
    user: dbConfig.user,
    password: dbConfig.password,
    max: 1,
    connectionTimeoutMillis: 3000,
  });

  try {
    const client = await testPool.connect();
    await client.query('SELECT 1');
    client.release();
    await testPool.end();
    return true;
  } catch (e) {
    try {
      await testPool.end();
    } catch (_) {
      // 忽略关闭错误
    }
    return false;
  }
}

module.exports = {
  createPgAdapter,
  testPgConnection,
  PgStatement,
  convertPlaceholders,
  convertNamedParams,
};

const db = require('./init');

/**
 * 数据库操作封装
 */

/**
 * 执行查询并返回结果
 * @param {string} sql - SQL语句
 * @param {Array} params - 参数数组
 * @returns {Array} 查询结果
 */
function query(sql, params = []) {
  try {
    const stmt = db.prepare(sql);
    return stmt.all(...params);
  } catch (error) {
    console.error('Database query error:', error);
    throw error;
  }
}

/**
 * 执行查询并返回单个结果
 * @param {string} sql - SQL语句
 * @param {Array} params - 参数数组
 * @returns {Object|null} 查询结果
 */
function get(sql, params = []) {
  try {
    const stmt = db.prepare(sql);
    return stmt.get(...params);
  } catch (error) {
    console.error('Database get error:', error);
    throw error;
  }
}

/**
 * 执行更新操作
 * @param {string} sql - SQL语句
 * @param {Array} params - 参数数组
 * @returns {Object} 执行结果
 */
function run(sql, params = []) {
  try {
    const stmt = db.prepare(sql);
    return stmt.run(...params);
  } catch (error) {
    console.error('Database run error:', error);
    throw error;
  }
}

/**
 * 执行事务
 * @param {Function} callback - 事务回调函数
 * @returns {any} 回调函数的返回值
 */
function transaction(callback) {
  const tx = db.transaction(callback);
  return tx();
}

/**
 * 获取数据库连接
 * @returns {Object} 数据库连接
 */
function getDb() {
  return db;
}

/**
 * 获取数据库类型
 * @returns {string} 数据库类型
 */
function getDbType() {
  return db.getDbType ? db.getDbType() : 'sqlite';
}

module.exports = {
  query,
  get,
  run,
  transaction,
  getDb,
  getDbType
};
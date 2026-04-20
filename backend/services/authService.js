const crypto = require('crypto');
const db = require('../db/init');
const logger = require('../utils/logger');

/**
 * 用户认证服务
 */

// PBKDF2 参数
const PBKDF2_ITERATIONS = 100000;
const KEY_LENGTH = 64;
const DIGEST = 'sha512';

/**
 * 哈希密码
 * @param {string} password - 明文密码
 * @returns {Promise<string>} password_hash (格式: salt:hash)
 */
function hashPassword(password) {
  return new Promise((resolve, reject) => {
    const salt = crypto.randomBytes(16).toString('hex');
    crypto.pbkdf2(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH, DIGEST, (err, derivedKey) => {
      if (err) return reject(err);
      resolve(`${salt}:${derivedKey.toString('hex')}`);
    });
  });
}

/**
 * 验证密码
 * @param {string} password - 明文密码
 * @param {string} passwordHash - 存储的密码哈希 (格式: salt:hash)
 * @returns {Promise<boolean>}
 */
function verifyPassword(password, passwordHash) {
  return new Promise((resolve, reject) => {
    const [salt, storedHash] = passwordHash.split(':');
    if (!salt || !storedHash) {
      return resolve(false);
    }
    crypto.pbkdf2(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH, DIGEST, (err, derivedKey) => {
      if (err) return reject(err);
      resolve(derivedKey.toString('hex') === storedHash);
    });
  });
}

/**
 * 初始化默认管理员账户
 */
function initDefaultAdmin() {
  // 检查 db 对象是否已经初始化
  if (!db || typeof db.prepare !== 'function') {
    console.warn('[Auth] 数据库尚未初始化，跳过默认管理员账户创建');
    return;
  }
  
  try {
    const existing = db.prepare('SELECT id FROM users WHERE username = ?').get('admin');
    if (!existing) {
      hashPassword('admin123').then((hash) => {
        db.prepare(
          'INSERT INTO users (username, password_hash, role) VALUES (?, ?, ?)'
        ).run('admin', hash, 'admin');
        logger.info('默认管理员账户已创建', { username: 'admin' });
      });
    }
  } catch (error) {
    console.warn('[Auth] 创建默认管理员账户失败:', error.message);
  }
}

/**
 * 用户注册
 * @param {string} username
 * @param {string} password
 * @param {string} [role='user'] - 用户角色
 * @returns {Promise<Object>} 用户信息（不含密码）
 */
async function register(username, password, role = 'user') {
  if (!username || !password) {
    throw new Error('用户名和密码不能为空');
  }
  if (username.length < 3 || username.length > 32) {
    throw new Error('用户名长度应在 3-32 个字符之间');
  }
  if (password.length < 6) {
    throw new Error('密码长度不能少于 6 个字符');
  }

  // 检查用户名是否已存在
  const existing = db.prepare('SELECT id FROM users WHERE username = ?').get(username);
  if (existing) {
    throw new Error('用户名已存在');
  }

  const passwordHash = await hashPassword(password);
  const stmt = db.prepare(
    'INSERT INTO users (username, password_hash, role) VALUES (?, ?, ?)'
  );
  const result = stmt.run(username, passwordHash, role);

  const user = db.prepare('SELECT id, username, role, tenant_id, created_at FROM users WHERE id = ?').get(result.lastInsertRowid);
  return user;
}

/**
 * 用户登录
 * @param {string} username
 * @param {string} password
 * @returns {Promise<Object>} 用户信息 + token
 */
async function login(username, password) {
  if (!username || !password) {
    throw new Error('用户名和密码不能为空');
  }

  const user = db.prepare('SELECT * FROM users WHERE username = ?').get(username);
  if (!user) {
    throw new Error('用户名或密码错误');
  }

  const valid = await verifyPassword(password, user.password_hash);
  if (!valid) {
    throw new Error('用户名或密码错误');
  }

  // 返回用户信息（不含密码）
  const { password_hash, ...userInfo } = user;
  return userInfo;
}

/**
 * 根据用户 ID 获取用户信息
 * @param {number} userId
 * @returns {Object|null}
 */
function getUserById(userId) {
  const user = db.prepare('SELECT id, username, role, tenant_id, created_at FROM users WHERE id = ?').get(userId);
  return user || null;
}

/**
 * 获取用户列表（分页）
 * @param {Object} params
 * @param {number} [params.page=0] - 页码
 * @param {number} [params.size=20] - 每页数量
 * @returns {{ content: Array, totalElements: number, totalPages: number }}
 */
function getUserList({ page, size }) {
  const pageVal = parseInt(page, 10) || 0;
  const sizeVal = parseInt(size, 10) || 20;
  const offset = pageVal * sizeVal;

  const countStmt = db.prepare('SELECT COUNT(*) as total FROM users');
  const { total } = countStmt.get();

  const dataStmt = db.prepare(
    'SELECT id, username, role, tenant_id, created_at FROM users ORDER BY created_at DESC LIMIT ? OFFSET ?'
  );
  const content = dataStmt.all(sizeVal, offset);

  return {
    content,
    totalElements: total,
    totalPages: Math.ceil(total / sizeVal),
  };
}

/**
 * 修改用户角色
 * @param {number} userId - 目标用户 ID
 * @param {string} role - 新角色
 * @returns {Object|null} 更新后的用户信息
 */
function updateUserRole(userId, role) {
  const allowedRoles = ['admin', 'user'];
  if (!allowedRoles.includes(role)) {
    throw new Error(`无效的角色，允许的角色: ${allowedRoles.join(', ')}`);
  }

  const user = db.prepare('SELECT id FROM users WHERE id = ?').get(userId);
  if (!user) {
    throw new Error('用户不存在');
  }

  db.prepare('UPDATE users SET role = ? WHERE id = ?').run(role, userId);
  return db.prepare('SELECT id, username, role, tenant_id, created_at FROM users WHERE id = ?').get(userId);
}

/**
 * 启用/禁用用户（通过在 users 表中添加 status 字段实现）
 * 如果 status 列不存在，则自动添加
 * @param {number} userId - 目标用户 ID
 * @param {string} status - 'active' 或 'disabled'
 * @returns {Object|null} 更新后的用户信息
 */
function updateUserStatus(userId, status) {
  const allowedStatuses = ['active', 'disabled'];
  if (!allowedStatuses.includes(status)) {
    throw new Error(`无效的状态，允许的状态: ${allowedStatuses.join(', ')}`);
  }

  const user = db.prepare('SELECT id FROM users WHERE id = ?').get(userId);
  if (!user) {
    throw new Error('用户不存在');
  }

  // 确保 status 列存在（SQLite ALTER TABLE ADD COLUMN 是幂等的，重复执行会报错，需要 try-catch）
  try {
    db.prepare("ALTER TABLE users ADD COLUMN status TEXT DEFAULT 'active'").run();
  } catch (e) {
    // 列已存在，忽略
  }

  db.prepare('UPDATE users SET status = ? WHERE id = ?').run(status, userId);
  return db.prepare('SELECT id, username, role, tenant_id, status, created_at FROM users WHERE id = ?').get(userId);
}

/**
 * 修改密码（需验证旧密码）
 * @param {number} userId - 用户 ID
 * @param {string} oldPassword - 旧密码
 * @param {string} newPassword - 新密码
 * @returns {Promise<Object>} 操作结果
 */
async function changePassword(userId, oldPassword, newPassword) {
  const user = db.prepare('SELECT id, password_hash FROM users WHERE id = ?').get(userId);
  if (!user) {
    throw new Error('用户不存在');
  }

  // 验证旧密码
  const isValid = await verifyPassword(oldPassword, user.password_hash);
  if (!isValid) {
    throw new Error('旧密码错误');
  }

  // 验证新密码长度
  if (newPassword.length < 6) {
    throw new Error('新密码长度不能少于 6 个字符');
  }

  // 更新密码
  const hashedPassword = await hashPassword(newPassword);
  db.prepare('UPDATE users SET password_hash = ? WHERE id = ?').run(hashedPassword, userId);

  logger.info('用户修改密码成功', { userId });
  return { success: true };
}

module.exports = {
  hashPassword,
  verifyPassword,
  initDefaultAdmin,
  register,
  login,
  getUserById,
  getUserList,
  updateUserRole,
  updateUserStatus,
  changePassword,
};

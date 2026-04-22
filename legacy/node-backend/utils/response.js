/**
 * 统一响应格式工具
 */

function success(res, data, message) {
  const body = { code: 200 };
  if (message) body.message = message;
  if (data !== undefined) body.data = data;
  return res.json(body);
}

function error(res, code, message) {
  return res.status(code).json({ code, message });
}

module.exports = { success, error };

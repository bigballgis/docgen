-- DocGen Platform 数据库初始化脚本
-- 适用于 PostgreSQL
-- 在 Docker 部署时通过 docker-entrypoint-initdb.d 自动执行

-- 创建默认租户
INSERT INTO tenants (name, code, status) VALUES ('默认租户', 'default', 'active')
ON CONFLICT (code) DO NOTHING;

-- 创建默认管理员用户（密码: admin123，使用PBKDF2哈希）
-- 注意：实际生产环境中应通过应用启动时自动创建

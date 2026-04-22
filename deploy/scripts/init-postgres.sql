-- ============================================================
-- DocGen Platform - PostgreSQL 初始化脚本
-- 创建应用所需的基础数据库对象
-- ============================================================

-- 设置时区
SET timezone = 'Asia/Shanghai';

-- 启用必要的扩展
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";  -- 用于模糊搜索

-- ============================================================
-- 授权（docker-entrypoint-initdb.d 以 POSTGRES_USER 执行）
-- ============================================================
-- 注意：此脚本由 PostgreSQL 初始化时以超级用户身份执行
-- 应用用户 docgen 已通过 POSTGRES_USER 环境变量创建

-- ============================================================
-- 创建枚举类型
-- ============================================================
DO $$ BEGIN
    CREATE TYPE user_role AS ENUM ('ADMIN', 'USER');
EXCEPTION WHEN duplicate_object THEN null;
END $$;

DO $$ BEGIN
    CREATE TYPE user_status AS ENUM ('ACTIVE', 'INACTIVE', 'LOCKED');
EXCEPTION WHEN duplicate_object THEN null;
END $$;

DO $$ BEGIN
    CREATE TYPE template_status AS ENUM ('ACTIVE', 'INACTIVE', 'DRAFT');
EXCEPTION WHEN duplicate_object THEN null;
END $$;

DO $$ BEGIN
    CREATE TYPE document_status AS ENUM ('DRAFT', 'GENERATING', 'COMPLETED', 'FAILED', 'APPROVED', 'REJECTED');
EXCEPTION WHEN duplicate_object THEN null;
END $$;

-- ============================================================
-- 注释说明
-- ============================================================
COMMENT ON TYPE user_role IS '用户角色：管理员/普通用户';
COMMENT ON TYPE user_status IS '用户状态：活跃/停用/锁定';
COMMENT ON TYPE template_status IS '模板状态：活跃/停用/草稿';
COMMENT ON TYPE document_status IS '文档状态：草稿/生成中/已完成/失败/已审批/已拒绝';

-- ============================================================
-- 完成提示
-- ============================================================
DO $$
BEGIN
    RAISE NOTICE 'DocGen Platform database initialized successfully';
END $$;

# 银文通 DocGen - 企业级文档生成平台

## 项目结构

```
docgen/
├── backend/           # Java 后端 (Spring Boot 3.2 + JPA + PostgreSQL)
├── frontend/          # Vue 3 前端 (Vite + Element Plus)
├── deploy/            # 部署配置 (Docker/Kong/Nginx/Monitoring)
├── legacy/            # 历史代码归档
│   ├── node-backend/  # 原 Node.js 后端 (已弃用)
│   └── java-backend/  # 原 Java 后端原型 (已弃用)
└── README.md
```

## 快速启动

### 开发模式

```bash
# 后端 (Java)
cd backend && mvn spring-boot:run

# 前端
cd frontend && npm install && npx vite --host
```

### 生产构建

```bash
# 后端打包
cd backend && mvn clean package -DskipTests
java -jar target/docgen-backend-2.0.0.jar

# 前端构建
cd frontend && npm run build
```

### Docker 部署

```bash
cd deploy && docker-compose up -d
```

## 技术栈

- **后端**: Java 17 + Spring Boot 3.2 + Spring Data JPA + PostgreSQL
- **前端**: Vue 3 + Vite + Element Plus + Pinia + vue-i18n
- **文档引擎**: Apache POI + docx4j
- **安全**: Spring Security + JWT (jjwt 0.12.5)
- **基础设施**: Kong + Redis + MinIO + Kafka + Euro-Office
- **监控**: Prometheus + Grafana

## 后端架构

### 分层结构

```
com.docgen/
├── config/          # 配置类 (Security, JWT, CORS, 文件存储)
├── controller/      # 控制器层 (10个Controller, 40+ API接口)
├── service/         # 业务逻辑层 (11个Service)
├── repository/      # 数据访问层 (10个JPA Repository)
├── entity/          # 实体类 (10个Entity)
├── dto/             # 数据传输对象 (16个DTO)
├── middleware/       # 中间件 (JWT过滤器, 多租户, 审计日志)
├── exception/       # 异常处理 (全局异常处理器)
├── util/            # 工具类 (密码, 响应)
└── DocgenApplication.java  # 启动类
```

### API 模块

| 模块 | 路径前缀 | 功能 |
|------|----------|------|
| 认证 | `/api/v1/auth` | 登录/注册/用户管理/密码修改 |
| 模板 | `/api/v1/templates` | 模板CRUD/上传/字段解析/审批/导出 |
| 文档 | `/api/v1/documents` | 文档生成/下载/历史/删除/导出 |
| 片段 | `/api/v1/fragments` | 片段CRUD/版本管理/对比/预览 |
| 编排 | `/api/v1/templates/{id}/composition` | 模板-片段编排/预览/生成 |
| 版本 | `/api/v1/templates/{id}/versions` | 版本列表/对比/预览/回滚 |
| 租户 | `/api/v1/tenants` | 租户CRUD |
| 编辑器 | `/api/v1/editor` | Euro-Office编辑器集成 |
| 仪表盘 | `/api/v1/dashboard` | 统计概览 |
| 健康 | `/api/v1/health` | 健康检查 |

### 数据库表 (10张)

| 表名 | 用途 |
|------|------|
| `users` | 用户表 |
| `tenants` | 租户表 |
| `templates` | 模板表 |
| `documents` | 文档记录表 |
| `audit_logs` | 审计日志表 |
| `template_versions` | 模板版本表 |
| `template_approvals` | 模板审批表 |
| `fragments` | 片段表 |
| `fragment_versions` | 片段版本表 |
| `template_compositions` | 模板编排表 |

## 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `JWT_SECRET` | JWT 密钥 | (内置开发密钥) |
| `SPRING_DATASOURCE_URL` | 数据库连接 | jdbc:postgresql://localhost:5432/docgen_platform |
| `SPRING_DATASOURCE_USERNAME` | 数据库用户 | docgen |
| `SPRING_DATASOURCE_PASSWORD` | 数据库密码 | docgen |
| `EURO_OFFICE_URL` | Euro-Office 地址 | (空, 禁用) |
| `KAFKA_ENABLED` | 启用 Kafka | false |

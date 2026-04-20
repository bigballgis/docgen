# 银文通 DocGen - 企业级文档生成平台

## 项目结构

```
docgen/
├── backend/          # Node.js 后端 (Express + SQLite/PostgreSQL)
├── frontend/         # Vue 3 前端 (Vite + Element Plus)
├── deploy/           # 部署配置 (Docker/Kong/Nginx/Monitoring)
├── legacy/           # 历史代码归档
├── .env.example      # 环境变量模板
└── README.md
```

## 快速启动

### 开发模式

```bash
# 后端
cd backend && npm install && node server.js

# 前端
cd frontend && npm install && npx vite --host
```

### Docker 部署

```bash
cd deploy && docker-compose up -d
```

## 技术栈

- **后端**: Node.js + Express + better-sqlite3 / PostgreSQL
- **前端**: Vue 3 + Vite + Element Plus + Pinia + vue-i18n
- **文档引擎**: docxtemplater + PizZip + mammoth.js
- **基础设施**: Kong + Redis + MinIO + Kafka + Euro-Office
- **监控**: Prometheus + Grafana

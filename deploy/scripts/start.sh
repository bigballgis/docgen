#!/bin/bash
# ============================================================
# DocGen Platform - 一键启动脚本
# 按依赖顺序启动所有服务
# ============================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }
log_step()  { echo -e "${BLUE}[STEP]${NC}  $*"; }

cd "$PROJECT_DIR"

# ----------------------------------------------------------
# 前置检查
# ----------------------------------------------------------
log_step "前置环境检查..."

if ! command -v docker &> /dev/null; then
    log_error "Docker 未安装，请先安装 Docker"
    exit 1
fi

if ! docker compose version &> /dev/null 2>&1; then
    log_error "Docker Compose V2 未安装，请先安装"
    exit 1
fi

if ! docker info &> /dev/null 2>&1; then
    log_error "Docker 守护进程未运行，请先启动 Docker"
    exit 1
fi

# 检查 .env 文件
if [ ! -f ".env" ]; then
    log_error ".env 文件不存在，请先创建 .env 文件"
    exit 1
fi

log_info "环境检查通过"
echo ""

# ----------------------------------------------------------
# 第一步：启动基础设施（数据库、缓存、消息队列）
# ----------------------------------------------------------
log_step "第一步：启动基础设施服务..."
docker compose up -d redis postgres zookeeper kafka minio

log_info "等待基础设施服务就绪..."
sleep 15

# 验证基础设施
log_info "验证基础设施状态..."
for svc in redis postgres zookeeper kafka minio; do
    if docker compose ps "$svc" | grep -q "running\|healthy"; then
        log_info "  $svc: 运行中"
    else
        log_warn "  $svc: 未就绪，请检查日志: docker compose logs $svc"
    fi
done
echo ""

# ----------------------------------------------------------
# 第二步：初始化 MinIO
# ----------------------------------------------------------
log_step "第二步：初始化 MinIO..."
if [ -f "${SCRIPT_DIR}/init-minio.sh" ]; then
    chmod +x "${SCRIPT_DIR}/init-minio.sh"
    bash "${SCRIPT_DIR}/init-minio.sh" || log_warn "MinIO 初始化失败，可稍后手动执行"
else
    log_warn "init-minio.sh 不存在，跳过 MinIO 初始化"
fi
echo ""

# ----------------------------------------------------------
# 第三步：启动 Kong API 网关
# ----------------------------------------------------------
log_step "第三步：启动 Kong API 网关..."
docker compose up -d kong-db kong

log_info "等待 Kong 就绪..."
sleep 15

# 初始化 Kong 配置
if [ -f "${SCRIPT_DIR}/init-kong.sh" ]; then
    chmod +x "${SCRIPT_DIR}/init-kong.sh"
    bash "${SCRIPT_DIR}/init-kong.sh" || log_warn "Kong 初始化失败，可稍后手动执行"
else
    log_warn "init-kong.sh 不存在，跳过 Kong 初始化"
fi
echo ""

# ----------------------------------------------------------
# 第四步：启动 Euro-Office 文档编辑器
# ----------------------------------------------------------
log_step "第四步：启动 Euro-Office 文档编辑器..."
docker compose up -d euro-office

log_info "等待 Euro-Office 就绪..."
sleep 20
echo ""

# ----------------------------------------------------------
# 第五步：启动应用服务（后端 + 前端 + Nginx）
# ----------------------------------------------------------
log_step "第五步：启动应用服务..."
docker compose up -d backend frontend nginx

log_info "等待应用服务就绪..."
sleep 10
echo ""

# ----------------------------------------------------------
# 最终状态检查
# ----------------------------------------------------------
log_step "最终状态检查..."
echo ""
echo "=========================================="
echo "  DocGen Platform 服务状态"
echo "=========================================="

docker compose ps --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}" 2>/dev/null \
    || docker compose ps

echo ""
echo "=========================================="
echo "  访问地址"
echo "=========================================="
echo "  前端应用:        http://localhost"
echo "  API 网关:        http://localhost:8000"
echo "  Kong Admin:      http://localhost:8001"
echo "  Kong Manager:    http://localhost:8002"
echo "  MinIO Console:   http://localhost:9001"
echo "  Euro-Office:     http://localhost:8080"
echo "  PostgreSQL:      localhost:5432"
echo "  Redis:           localhost:6379"
echo "  Kafka:           localhost:9092"
echo "=========================================="
echo ""

log_info "所有服务启动完毕！"
log_info "查看日志: docker compose logs -f"
log_info "停止服务: bash scripts/stop.sh"

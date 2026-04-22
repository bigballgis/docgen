#!/bin/bash
# ============================================================
# DocGen Platform - 一键停止脚本
# 优雅停止所有服务
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
log_step()  { echo -e "${BLUE}[STEP]${NC}  $*"; }

cd "$PROJECT_DIR"

# ----------------------------------------------------------
# 停止应用服务
# ----------------------------------------------------------
log_step "停止应用服务..."
docker compose stop nginx frontend backend 2>/dev/null || true

# ----------------------------------------------------------
# 停止 Euro-Office
# ----------------------------------------------------------
log_step "停止 Euro-Office..."
docker compose stop euro-office 2>/dev/null || true

# ----------------------------------------------------------
# 停止 Kong
# ----------------------------------------------------------
log_step "停止 Kong..."
docker compose stop kong 2>/dev/null || true

# ----------------------------------------------------------
# 停止基础设施
# ----------------------------------------------------------
log_step "停止基础设施服务..."
docker compose stop kafka zookeeper minio redis postgres kong-db 2>/dev/null || true

echo ""
log_info "所有服务已停止"
echo ""

# ----------------------------------------------------------
# 询问是否清理数据卷
# ----------------------------------------------------------
read -p "是否同时删除容器和数据卷？(y/N): " -n 1 -r
echo ""
if [[ $REPLY =~ ^[Yy]$ ]]; then
    log_warn "正在删除所有容器和数据卷..."
    docker compose down -v --remove-orphans
    log_info "容器和数据卷已删除"
else
    log_info "仅停止服务，数据卷已保留"
    log_info "如需彻底清理，请执行: docker compose down -v"
fi

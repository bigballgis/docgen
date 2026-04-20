#!/bin/bash
# ============================================================
# Kong 初始化脚本
# 使用 Kong Admin API 配置路由、服务和插件
# ============================================================

set -euo pipefail

KONG_ADMIN_URL="http://localhost:8001"
MAX_RETRIES=30
RETRY_INTERVAL=5

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

# ----------------------------------------------------------
# 等待 Kong Admin API 就绪
# ----------------------------------------------------------
log_info "等待 Kong Admin API 就绪..."
for i in $(seq 1 $MAX_RETRIES); do
    if curl -sf "${KONG_ADMIN_URL}/status" > /dev/null 2>&1; then
        log_info "Kong Admin API 已就绪"
        break
    fi
    if [ $i -eq $MAX_RETRIES ]; then
        log_error "Kong Admin API 在 ${MAX_RETRIES} 次重试后仍不可用"
        exit 1
    fi
    log_warn "等待 Kong... ($i/$MAX_RETRIES)"
    sleep $RETRY_INTERVAL
done

# ----------------------------------------------------------
# 运行 Kong 数据库迁移
# ----------------------------------------------------------
log_info "执行 Kong 数据库迁移..."
docker exec docgen-kong kong migrations up || true

# ----------------------------------------------------------
# 检查声明式配置是否已加载
# ----------------------------------------------------------
log_info "检查 Kong 配置状态..."
CONFIG_STATUS=$(curl -sf "${KONG_ADMIN_URL}/" 2>/dev/null | python3 -c "import sys,json; print(json.load(sys.stdin).get('configuration',{}).get('database','unknown'))" 2>/dev/null || echo "unknown")

if [ "$CONFIG_STATUS" = "off" ]; then
    log_info "Kong 使用声明式配置（DB-less 模式），配置已通过挂载文件加载"
    log_info "验证服务注册..."

    # 验证服务是否已注册
    SERVICES=$(curl -sf "${KONG_ADMIN_URL}/services" 2>/dev/null | python3 -c "
import sys, json
data = json.load(sys.stdin)
services = data.get('data', [])
for s in services:
    print(f\"  - {s['name']}: {s.get('host', 'N/A')}:{s.get('port', 'N/A')}\")
" 2>/dev/null || echo "  (无法获取服务列表)")

    if [ -n "$SERVICES" ]; then
        log_info "已注册的服务："
        echo "$SERVICES"
    fi

    # 验证路由是否已注册
    ROUTES=$(curl -sf "${KONG_ADMIN_URL}/routes" 2>/dev/null | python3 -c "
import sys, json
data = json.load(sys.stdin)
routes = data.get('data', [])
for r in routes:
    print(f\"  - {r['name']}: {r.get('paths', [])}\")
" 2>/dev/null || echo "  (无法获取路由列表)")

    if [ -n "$ROUTES" ]; then
        log_info "已注册的路由："
        echo "$ROUTES"
    fi

    log_info "Kong 初始化完成（声明式配置模式）"
else
    log_info "Kong 使用数据库模式，通过 Admin API 配置..."

    # ----------------------------------------------------------
    # 注册服务
    # ----------------------------------------------------------
    log_info "注册 docgen-backend 服务..."
    curl -sf -X PUT "${KONG_ADMIN_URL}/services/docgen-backend" \
        --data "name=docgen-backend" \
        --data "url=http://backend:3001" \
        --data "retries=3" \
        --data "read_timeout=300000" \
        --data "write_timeout=300000" \
        --data "connect_timeout=60000" > /dev/null 2>&1 \
        && log_info "docgen-backend 服务已注册" \
        || log_warn "docgen-backend 服务注册失败"

    log_info "注册 euro-office-editor 服务..."
    curl -sf -X PUT "${KONG_ADMIN_URL}/services/euro-office-editor" \
        --data "name=euro-office-editor" \
        --data "url=http://euro-office:8080" \
        --data "retries=3" \
        --data "read_timeout=3600000" \
        --data "write_timeout=3600000" \
        --data "connect_timeout=60000" > /dev/null 2>&1 \
        && log_info "euro-office-editor 服务已注册" \
        || log_warn "euro-office-editor 服务注册失败"

    # ----------------------------------------------------------
    # 注册路由
    # ----------------------------------------------------------
    log_info "注册 API 路由..."
    curl -sf -X POST "${KONG_ADMIN_URL}/services/docgen-backend/routes" \
        --data "name=docgen-api-route" \
        --data "strip_path=false" \
        --data "paths[]=/api" \
        --data "methods[]=GET" \
        --data "methods[]=POST" \
        --data "methods[]=PUT" \
        --data "methods[]=PATCH" \
        --data "methods[]=DELETE" \
        --data "methods[]=OPTIONS" > /dev/null 2>&1 \
        && log_info "API 路由已注册" \
        || log_warn "API 路由注册失败（可能已存在）"

    log_info "注册编辑器路由..."
    curl -sf -X POST "${KONG_ADMIN_URL}/services/euro-office-editor/routes" \
        --data "name=euro-office-route" \
        --data "strip_path=true" \
        --data "paths[]=/editor" > /dev/null 2>&1 \
        && log_info "编辑器路由已注册" \
        || log_warn "编辑器路由注册失败（可能已存在）"

    # ----------------------------------------------------------
    # 配置插件
    # ----------------------------------------------------------
    log_info "配置 CORS 插件..."
    curl -sf -X POST "${KONG_ADMIN_URL}/plugins" \
        --data "name=cors" \
        --data "config.origins[]=*" \
        --data "config.methods[]=GET" \
        --data "config.methods[]=POST" \
        --data "config.methods[]=PUT" \
        --data "config.methods[]=PATCH" \
        --data "config.methods[]=DELETE" \
        --data "config.methods[]=OPTIONS" \
        --data "config.headers[]=Accept" \
        --data "config.headers[]=Authorization" \
        --data "config.headers[]=Content-Type" \
        --data "config.exposed_headers[]=X-Total-Count" \
        --data "config.credentials=true" \
        --data "config.max_age=3600" > /dev/null 2>&1 \
        && log_info "CORS 插件已配置" \
        || log_warn "CORS 插件配置失败（可能已存在）"

    log_info "配置 Rate-Limiting 插件..."
    curl -sf -X POST "${KONG_ADMIN_URL}/plugins" \
        --data "name=rate-limiting" \
        --data "route.docgen-api-route" \
        --data "config.minute=100" \
        --data "config.hour=1000" \
        --data "config.policy=local" > /dev/null 2>&1 \
        && log_info "Rate-Limiting 插件已配置" \
        || log_warn "Rate-Limiting 插件配置失败（可能已存在）"

    log_info "配置 Request-ID 插件..."
    curl -sf -X POST "${KONG_ADMIN_URL}/plugins" \
        --data "name=request-id" \
        --data "config.header_name=X-Request-ID" \
        --data "config.generator=uuid" > /dev/null 2>&1 \
        && log_info "Request-ID 插件已配置" \
        || log_warn "Request-ID 插件配置失败（可能已存在）"

    log_info "Kong 初始化完成（数据库模式）"
fi

# ----------------------------------------------------------
# 验证最终状态
# ----------------------------------------------------------
log_info "验证 Kong 状态..."
echo ""
echo "=========================================="
echo "  Kong 配置摘要"
echo "=========================================="

curl -sf "${KONG_ADMIN_URL}/services" 2>/dev/null | python3 -c "
import sys, json
data = json.load(sys.stdin)
services = data.get('data', [])
print(f'服务数量: {len(services)}')
for s in services:
    print(f'  - {s[\"name\"]}: {s.get(\"host\",\"N/A\")}:{s.get(\"port\",\"N/A\")}')
" 2>/dev/null || echo "  (无法获取服务信息)"

echo ""

curl -sf "${KONG_ADMIN_URL}/routes" 2>/dev/null | python3 -c "
import sys, json
data = json.load(sys.stdin)
routes = data.get('data', [])
print(f'路由数量: {len(routes)}')
for r in routes:
    print(f'  - {r[\"name\"]}: {r.get(\"paths\", [])}')
" 2>/dev/null || echo "  (无法获取路由信息)"

echo ""

curl -sf "${KONG_ADMIN_URL}/plugins" 2>/dev/null | python3 -c "
import sys, json
data = json.load(sys.stdin)
plugins = data.get('data', [])
print(f'插件数量: {len(plugins)}')
for p in plugins:
    print(f'  - {p[\"name\"]} ({p.get(\"enabled\", False)})')
" 2>/dev/null || echo "  (无法获取插件信息)"

echo ""
echo "=========================================="
log_info "Kong 初始化脚本执行完毕"

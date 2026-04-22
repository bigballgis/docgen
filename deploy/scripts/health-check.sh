#!/bin/bash
# ============================================================
# DocGen Platform - 服务健康检查脚本
# 检查所有服务的运行状态和健康情况
# ============================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[OK]${NC}    $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[FAIL]${NC}  $*"; }

cd "$PROJECT_DIR"

echo "=========================================="
echo "  DocGen Platform 健康检查"
echo "=========================================="
echo ""

# 定义检查项
declare -A CHECKS=(
    ["Nginx"]="curl -sf -o /dev/null http://localhost:80/health"
    ["Kong Proxy"]="curl -sf -o /dev/null http://localhost:8000/status"
    ["Kong Admin"]="curl -sf -o /dev/null http://localhost:8001/status"
    ["Backend API"]="curl -sf -o /dev/null http://localhost:3001/health"
    ["PostgreSQL"]="docker exec docgen-postgres pg_isready -U docgen -d docgen_platform"
    ["Redis"]="docker exec docgen-redis redis-cli -a redis_secret_2026 ping"
    ["MinIO"]="curl -sf -o /dev/null http://localhost:9000/minio/health/live"
    ["Kafka"]="docker exec docgen-kafka kafka-broker-api-versions --bootstrap-server localhost:9092"
    ["Zookeeper"]="docker exec docgen-zookeeper echo ruok | nc localhost 2181"
)

FAILED=0
PASSED=0

for name in "${!CHECKS[@]}"; do
    cmd="${CHECKS[$name]}"
    if eval "$cmd" > /dev/null 2>&1; then
        log_info "$name"
        ((PASSED++))
    else
        log_error "$name"
        ((FAILED++))
    fi
done

echo ""
echo "=========================================="
echo "  结果: ${PASSED} 通过, ${FAILED} 失败"
echo "=========================================="

if [ $FAILED -gt 0 ]; then
    echo ""
    log_warn "部分服务异常，请查看日志："
    log_warn "  docker compose logs -f <service_name>"
    exit 1
fi

echo ""
log_info "所有服务运行正常"
exit 0

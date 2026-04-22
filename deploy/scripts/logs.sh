#!/bin/bash
# ============================================================
# DocGen Platform - 查看日志脚本
# 快速查看各服务的实时日志
# ============================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_DIR"

SERVICE="${1:-}"

if [ -z "$SERVICE" ]; then
    echo "用法: bash $0 <service-name>"
    echo ""
    echo "可用服务:"
    echo "  all         - 所有服务日志"
    echo "  nginx       - Nginx 反向代理"
    echo "  kong        - Kong API 网关"
    echo "  backend     - 后端 API"
    echo "  frontend    - 前端服务"
    echo "  postgres    - PostgreSQL 数据库"
    echo "  redis       - Redis 缓存"
    echo "  minio       - MinIO 对象存储"
    echo "  kafka       - Kafka 消息队列"
    echo "  zookeeper   - Zookeeper"
    echo "  euro-office - Euro-Office 编辑器"
    echo ""
    echo "示例:"
    echo "  bash $0 backend     # 查看后端日志"
    echo "  bash $0 all         # 查看所有日志"
    exit 0
fi

if [ "$SERVICE" = "all" ]; then
    docker compose logs -f --tail=100
else
    docker compose logs -f --tail=100 "$SERVICE"
fi

#!/bin/bash
# ============================================================
# MinIO 初始化脚本
# 创建必要的 Bucket、IAM 用户和访问策略
# 安全加固: 移除匿名访问，仅限特定 IAM 用户访问
# ============================================================

set -euo pipefail

MINIO_ALIAS="docgen-minio"
MINIO_ENDPOINT="http://minio:9000"
MINIO_ACCESS_KEY="${MINIO_ROOT_USER:-minioadmin}"
MINIO_SECRET_KEY="${MINIO_ROOT_PASSWORD:-minio_secret_2026}"
BUCKET_NAME="docgen-documents"
TEMP_BUCKET="docgen-temp"

# IAM 用户配置
IAM_USER="docgen-backend"
IAM_ACCESS_KEY="${MINIO_BACKEND_ACCESS_KEY:-docgen_backend_key_2026}"
IAM_SECRET_KEY="${MINIO_BACKEND_SECRET_KEY:-docgen_backend_secret_2026}"

MAX_RETRIES=30
RETRY_INTERVAL=5

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

# ----------------------------------------------------------
# 等待 MinIO 就绪
# ----------------------------------------------------------
log_info "等待 MinIO 服务就绪..."
for i in $(seq 1 $MAX_RETRIES); do
    if curl -sf "${MINIO_ENDPOINT}/minio/health/live" > /dev/null 2>&1; then
        log_info "MinIO 服务已就绪"
        break
    fi
    if [ $i -eq $MAX_RETRIES ]; then
        log_error "MinIO 在 ${MAX_RETRIES} 次重试后仍不可用"
        exit 1
    fi
    log_warn "等待 MinIO... ($i/$MAX_RETRIES)"
    sleep $RETRY_INTERVAL
done

# ----------------------------------------------------------
# 检查 mc 客户端是否可用
# ----------------------------------------------------------
if ! command -v mc &> /dev/null; then
    log_info "在本地安装 MinIO Client (mc)..."
    curl -sL https://dl.min.io/client/mc/release/linux-amd64/mc -o /usr/local/bin/mc 2>/dev/null \
        && chmod +x /usr/local/bin/mc \
        && log_info "mc 客户端已安装" \
        || {
            log_error "无法安装 mc 客户端，尝试使用 docker exec 方式"
            MC_CMD="docker exec docgen-minio mc"
        }
fi

MC_CMD="${MC_CMD:-mc}"

# ----------------------------------------------------------
# 配置 MinIO 别名
# ----------------------------------------------------------
log_info "配置 MinIO 连接别名..."
$MC_CMD alias set "${MINIO_ALIAS}" "${MINIO_ENDPOINT}" "${MINIO_ACCESS_KEY}" "${MINIO_SECRET_KEY}" \
    && log_info "MinIO 别名已配置" \
    || log_error "MinIO 别名配置失败"

# ----------------------------------------------------------
# 创建 Bucket
# ----------------------------------------------------------
log_info "创建 Bucket: ${BUCKET_NAME}..."
if $MC_CMD ls "${MINIO_ALIAS}/${BUCKET_NAME}" > /dev/null 2>&1; then
    log_info "Bucket '${BUCKET_NAME}' 已存在"
else
    $MC_CMD mb "${MINIO_ALIAS}/${BUCKET_NAME}" \
        && log_info "Bucket '${BUCKET_NAME}' 创建成功" \
        || log_error "Bucket '${BUCKET_NAME}' 创建失败"
fi

# ----------------------------------------------------------
# 创建临时文件 Bucket
# ----------------------------------------------------------
log_info "创建临时文件 Bucket: ${TEMP_BUCKET}..."
if $MC_CMD ls "${MINIO_ALIAS}/${TEMP_BUCKET}" > /dev/null 2>&1; then
    log_info "Bucket '${TEMP_BUCKET}' 已存在"
else
    $MC_CMD mb "${MINIO_ALIAS}/${TEMP_BUCKET}" \
        && log_info "Bucket '${TEMP_BUCKET}' 创建成功" \
        || log_error "Bucket '${TEMP_BUCKET}' 创建失败"
fi

# ----------------------------------------------------------
# 移除匿名访问策略（安全加固）
# ----------------------------------------------------------
log_info "移除所有匿名访问策略..."
$MC_CMD anonymous set none "${MINIO_ALIAS}/${BUCKET_NAME}" 2>/dev/null && \
    log_info "已移除 '${BUCKET_NAME}' 的匿名访问" || \
    log_warn "'${BUCKET_NAME}' 无需移除匿名访问"

$MC_CMD anonymous set none "${MINIO_ALIAS}/${TEMP_BUCKET}" 2>/dev/null && \
    log_info "已移除 '${TEMP_BUCKET}' 的匿名访问" || \
    log_warn "'${TEMP_BUCKET}' 无需移除匿名访问"

# ----------------------------------------------------------
# 创建 IAM 用户 docgen-backend
# ----------------------------------------------------------
log_info "创建 IAM 用户: ${IAM_USER}..."
if $MC_CMD admin user info "${MINIO_ALIAS}" "${IAM_USER}" > /dev/null 2>&1; then
    log_info "IAM 用户 '${IAM_USER}' 已存在，更新密钥..."
    $MC_CMD admin user remove "${MINIO_ALIAS}" "${IAM_USER}" 2>/dev/null || true
fi

$MC_CMD admin user add "${MINIO_ALIAS}" "${IAM_USER}" "${IAM_ACCESS_KEY}" "${IAM_SECRET_KEY}" \
    && log_info "IAM 用户 '${IAM_USER}' 创建成功" \
    || log_error "IAM 用户 '${IAM_USER}' 创建失败"

# ----------------------------------------------------------
# 创建 docgen-documents 读写策略（仅 docgen-backend 用户）
# ----------------------------------------------------------
log_info "创建 '${BUCKET_NAME}' 读写策略..."

cat > /tmp/docgen-documents-policy.json << 'POLICY_EOF'
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "DocgenDocumentsReadWrite",
            "Effect": "Allow",
            "Principal": {"AWS": ["docgen-backend"]},
            "Action": [
                "s3:GetObject",
                "s3:PutObject",
                "s3:DeleteObject",
                "s3:GetBucketLocation",
                "s3:ListBucket",
                "s3:ListBucketMultipartUploads",
                "s3:ListMultipartUploadParts",
                "s3:AbortMultipartUpload"
            ],
            "Resource": [
                "arn:aws:s3:::docgen-documents",
                "arn:aws:s3:::docgen-documents/*"
            ]
        }
    ]
}
POLICY_EOF

$MC_CMD anonymous set-json /tmp/docgen-documents-policy.json "${MINIO_ALIAS}/${BUCKET_NAME}" \
    && log_info "Bucket '${BUCKET_NAME}' 策略已设置（仅 ${IAM_USER} 用户可读写）" \
    || log_warn "Bucket '${BUCKET_NAME}' 策略设置失败"

rm -f /tmp/docgen-documents-policy.json

# ----------------------------------------------------------
# 创建 docgen-temp 读写策略 + 7天过期生命周期（仅 docgen-backend 用户）
# ----------------------------------------------------------
log_info "创建 '${TEMP_BUCKET}' 读写策略..."

cat > /tmp/docgen-temp-policy.json << 'POLICY_EOF'
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "DocgenTempReadWrite",
            "Effect": "Allow",
            "Principal": {"AWS": ["docgen-backend"]},
            "Action": [
                "s3:GetObject",
                "s3:PutObject",
                "s3:DeleteObject",
                "s3:GetBucketLocation",
                "s3:ListBucket",
                "s3:ListBucketMultipartUploads",
                "s3:ListMultipartUploadParts",
                "s3:AbortMultipartUpload"
            ],
            "Resource": [
                "arn:aws:s3:::docgen-temp",
                "arn:aws:s3:::docgen-temp/*"
            ]
        }
    ]
}
POLICY_EOF

$MC_CMD anonymous set-json /tmp/docgen-temp-policy.json "${MINIO_ALIAS}/${TEMP_BUCKET}" \
    && log_info "Bucket '${TEMP_BUCKET}' 策略已设置（仅 ${IAM_USER} 用户可读写）" \
    || log_warn "Bucket '${TEMP_BUCKET}' 策略设置失败"

rm -f /tmp/docgen-temp-policy.json

# ----------------------------------------------------------
# 设置 docgen-temp 7天过期生命周期
# ----------------------------------------------------------
log_info "设置 '${TEMP_BUCKET}' 7天过期生命周期规则..."

cat > /tmp/docgen-temp-lifecycle.json << 'LIFECYCLE_EOF'
{
    "Rules": [
        {
            "ID": "DocgenTempExpire7Days",
            "Status": "Enabled",
            "Expiration": {
                "Days": 7
            },
            "Filter": {
                "Prefix": ""
            }
        }
    ]
}
LIFECYCLE_EOF

$MC_CMD ilm import "${MINIO_ALIAS}/${TEMP_BUCKET}" < /tmp/docgen-temp-lifecycle.json \
    && log_info "Bucket '${TEMP_BUCKET}' 生命周期规则已设置（7天自动过期）" \
    || log_warn "Bucket '${TEMP_BUCKET}' 生命周期规则设置失败"

rm -f /tmp/docgen-temp-lifecycle.json

# ----------------------------------------------------------
# 将策略直接附加到 IAM 用户（确保生效）
# ----------------------------------------------------------
log_info "为 IAM 用户 '${IAM_USER}' 附加全局访问策略..."

cat > /tmp/docgen-user-policy.json << 'POLICY_EOF'
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "DocgenBackendFullAccess",
            "Effect": "Allow",
            "Action": [
                "s3:GetObject",
                "s3:PutObject",
                "s3:DeleteObject",
                "s3:GetBucketLocation",
                "s3:ListBucket",
                "s3:ListBucketMultipartUploads",
                "s3:ListMultipartUploadParts",
                "s3:AbortMultipartUpload"
            ],
            "Resource": [
                "arn:aws:s3:::docgen-documents",
                "arn:aws:s3:::docgen-documents/*",
                "arn:aws:s3:::docgen-temp",
                "arn:aws:s3:::docgen-temp/*"
            ]
        }
    ]
}
POLICY_EOF

$MC_CMD admin policy create "${MINIO_ALIAS}" "docgen-backend-policy" /tmp/docgen-user-policy.json 2>/dev/null \
    || $MC_CMD admin policy update "${MINIO_ALIAS}" "docgen-backend-policy" /tmp/docgen-user-policy.json 2>/dev/null

$MC_CMD admin policy attach "${MINIO_ALIAS}" "docgen-backend-policy" --user "${IAM_USER}" \
    && log_info "IAM 用户 '${IAM_USER}' 策略已附加" \
    || log_warn "IAM 用户策略附加失败"

rm -f /tmp/docgen-user-policy.json

# ----------------------------------------------------------
# 验证
# ----------------------------------------------------------
log_info "验证 MinIO 配置..."
echo ""
echo "=========================================="
echo "  MinIO 配置摘要"
echo "=========================================="

$MC_CMD ls "${MINIO_ALIAS}" 2>/dev/null || echo "  (无法列出 Bucket)"

echo ""
echo "  IAM 用户:"
$MC_CMD admin user ls "${MINIO_ALIAS}" 2>/dev/null || echo "  (无法列出用户)"

echo ""
echo "  Bucket 策略:"
echo "  - ${BUCKET_NAME}: 仅 ${IAM_USER} 用户可读写"
echo "  - ${TEMP_BUCKET}: 仅 ${IAM_USER} 用户可读写 + 7天过期"
echo "  - 匿名访问: 已禁用"

echo ""
echo "=========================================="
log_info "MinIO 初始化脚本执行完毕"

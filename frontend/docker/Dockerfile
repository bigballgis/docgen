# ============================================================
# DocGen Platform - 前端 Dockerfile
# 多阶段构建：构建阶段 + Nginx 运行阶段
# ============================================================

# ---- 构建阶段 ----
FROM node:20-alpine AS build

LABEL maintainer="DocGen Platform Team"
LABEL description="DocGen Platform Frontend Build Stage - Vue3 SPA"

# 安装构建依赖
RUN apk add --no-cache git

WORKDIR /app

# 复制依赖文件
COPY package*.json ./

# 安装所有依赖（包括 devDependencies）
RUN npm ci --legacy-peer-deps \
    && npm cache clean --force

# 复制源代码
COPY . .

# 构建生产版本
RUN npm run build \
    && ls -la dist/

# ---- 运行阶段 ----
FROM nginx:1.25-alpine

LABEL maintainer="DocGen Platform Team"
LABEL description="DocGen Platform Frontend - Vue3 SPA with Nginx"
LABEL version="1.0.0"

# 移除默认 Nginx 配置
RUN rm -rf /usr/share/nginx/html/* \
    && rm -f /etc/nginx/conf.d/default.conf

# 从构建阶段复制构建产物
COPY --from=build /app/dist /usr/share/nginx/html

# 复制 Nginx 配置（从 docker 子目录）
COPY docker/frontend-nginx.conf /etc/nginx/conf.d/default.conf

# 创建非 root 用户
RUN addgroup -S docgen && adduser -S docgen -G docgen \
    && chown -R docgen:docgen /usr/share/nginx/html \
    && chown -R docgen:docgen /var/cache/nginx \
    && chown -R docgen:docgen /var/log/nginx \
    && touch /var/run/nginx.pid \
    && chown -R docgen:docgen /var/run/nginx.pid

# 切换到非 root 用户
USER docgen

# 暴露端口
EXPOSE 80

# 健康检查
HEALTHCHECK --interval=20s --timeout=5s --retries=3 --start-period=10s \
  CMD wget --quiet --tries=1 --spider http://localhost:80/ || exit 1

CMD ["nginx", "-g", "daemon off;"]

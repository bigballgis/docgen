FROM node:18-alpine

# 设置工作目录
WORKDIR /app

# 安装 better-sqlite3 编译依赖
RUN apk add --no-cache python3 make g++

# 复制 package.json
COPY package.json ./

# 安装依赖
RUN npm install --production

# 复制源代码
COPY . .

# 创建数据和上传目录
RUN mkdir -p /app/data /app/uploads

# 暴露端口
EXPOSE 3001

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:3001/health || exit 1

# 启动服务
CMD ["node", "server.js"]

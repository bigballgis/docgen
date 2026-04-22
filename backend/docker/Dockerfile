# ============================================================
# DocGen Platform - 后端 Dockerfile
# 多阶段构建：Maven 编译 → JRE 运行
# Spring Boot 3.2 + Java 17
# ============================================================

# ---- 构建阶段 ----
FROM maven:3.9-eclipse-temurin-17 AS build

LABEL maintainer="DocGen Platform Team"
LABEL description="DocGen Platform Backend Build Stage - Spring Boot"

WORKDIR /build

# 先复制 pom.xml 利用 Docker 缓存层加速依赖下载
COPY pom.xml .
RUN mvn dependency:go-offline -B \
    && rm -rf /root/.m2/repository/org/projectlombok

# 复制源代码
COPY src ./src

# 编译打包，跳过测试
RUN mvn package -DskipTests -B \
    && ls -la target/*.jar

# ---- 运行阶段 ----
FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="DocGen Platform Team"
LABEL description="DocGen Platform Backend - Spring Boot"
LABEL version="2.0.0"

# 安装必要工具（wget 用于健康检查，curl 用于 Euro-Office 回调）
RUN apk add --no-cache wget curl tzdata \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone \
    && apk del tzdata

# 创建非 root 用户
RUN addgroup -S docgen && adduser -S docgen -G docgen

# 创建应用目录
WORKDIR /app

# 从构建阶段复制 JAR 包
COPY --from=build /build/target/docgen-backend-2.0.0.jar app.jar

# 创建数据和上传目录
RUN mkdir -p /app/data /app/uploads \
    && chown -R docgen:docgen /app

# 切换到非 root 用户
USER docgen

# 暴露端口
EXPOSE 3001

# JVM 参数通过 JAVA_OPTS 环境变量传入
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:3001/actuator/health || exit 1

# 启动服务
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]

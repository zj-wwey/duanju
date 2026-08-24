# 后端 Spring Boot 多阶段构建
# 构建上下文需为 backed/server/ 目录（docker compose 已配置）

# ====== 阶段 1：Maven 构建 ======
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

# 先复制 pom.xml 预下载依赖（利用 Docker 层缓存）
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 复制源码并打包
COPY src ./src
RUN mvn package -DskipTests -B \
    && mv target/*.jar app.jar

# ====== 阶段 2：运行时 ======
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 安装时区数据 + curl（用于健康检查）
RUN apk add --no-cache tzdata curl \
    && cp /usr/share/zoneinfo/Asia/Singapore /etc/localtime \
    && echo "Asia/Singapore" > /etc/timezone

# 复制构建产物
COPY --from=builder /app/app.jar app.jar

# 创建上传目录（与 application.yml 的 local-upload-dir 对应）
RUN mkdir -p /app/uploads /app/logs

EXPOSE 8080

# JVM 参数：
#   -Xmx2g          最大堆 2GB（按服务器内存调整）
#   -XX:+UseG1GC    使用 G1 收集器
#   prod profile 由 docker-compose 通过 SPRING_PROFILES_ACTIVE 注入
ENTRYPOINT ["java", "-jar", "-Xmx2g", "-XX:+UseG1GC", "app.jar"]

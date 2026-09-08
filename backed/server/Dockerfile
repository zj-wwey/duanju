# 后端 Spring Boot 多阶段构建
# 构建上下文需为 backed/server/ 目录（docker compose 已配置）

# ====== 阶段 1：Maven 构建 ======
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

# 一次性复制 pom.xml + src，不再用 dependency:go-offline 预下载
# 原因：go-offline 有时无法解析 spring-boot-maven-plugin，导致 repackage 被跳过，
# 生成 thin jar（无 BOOT-INF/classes，无业务 class entry），容器启动后所有路由 404
COPY pom.xml .
COPY src ./src

# 构建 fat jar + 验证 jar 内容确保 repackage 成功且业务 class 已打入
RUN mvn clean package -DskipTests -B \
    && jar tf target/*.jar | grep -q "BOOT-INF/classes/" \
        || (echo "FATAL: fat jar 不含 BOOT-INF/classes，spring-boot:repackage 未执行！" && exit 1) \
    && jar tf target/*.jar | grep -q "BOOT-INF/classes/com/duanju/controller/AdminContentController" \
        || (echo "FATAL: AdminContentController 不在 fat jar 中！" && exit 1) \
    && mv target/*.jar app.jar

# ====== 阶段 2：运行时 ======
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 安装时区数据 + curl + ffmpeg（用于HLS转码）
RUN apk add --no-cache tzdata curl ffmpeg \
    && cp /usr/share/zoneinfo/Asia/Singapore /etc/localtime \
    && echo "Asia/Singapore" > /etc/timezone

# 复制构建产物
COPY --from=builder /app/app.jar app.jar

# 创建上传目录（与 application.yml 的 local-upload-dir 对应）
RUN mkdir -p /app/uploads /app/logs

EXPOSE 8080

# JVM 参数：
#   -Xmx512m        最大堆 512MB（$6 VPS 1GB RAM 适配；升级到 $12 时可改为 -Xmx2g）
#   -XX:+UseG1GC    使用 G1 收集器
#   prod profile 由 docker-compose 通过 SPRING_PROFILES_ACTIVE 注入
ENTRYPOINT ["java", "-jar", "-Xms256m", "-Xmx512m", "-XX:+UseG1GC", "app.jar"]

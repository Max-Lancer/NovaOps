# NovaOps 后端镜像：Maven 构建 + JRE 运行
# 构建上下文为 backend/（见 docker-compose.yml），镜像内不含任何本地密钥文件。

# ---------- 构建阶段 ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# 先只拷贝 pom 并预热依赖，改动源码时可复用该层
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -DskipTests package \
    && cp "$(find target -maxdepth 1 -name '*.jar' ! -name '*.original' | head -n1)" /build/app.jar

# ---------- 运行阶段 ----------
FROM eclipse-temurin:17-jre

# curl 仅用于容器健康检查
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
RUN useradd -r -u 10001 -m -d /home/appuser appuser \
    && mkdir -p /app/data/kb /app/data/attachments \
    && chown -R appuser:appuser /app
COPY --from=build /build/app.jar /app/app.jar
RUN chown appuser:appuser /app/app.jar

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Dfile.encoding=UTF-8" \
    NOVAOPS_KB_STORAGE=/app/data/kb \
    NOVAOPS_TICKET_STORAGE=/app/data/attachments

USER appuser
EXPOSE 8090

# /api/auth/roles 是公开接口，适合做存活探针
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=20 \
    CMD curl -fsS http://127.0.0.1:8090/api/auth/roles >/dev/null || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

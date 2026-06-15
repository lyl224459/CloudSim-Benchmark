# syntax=docker/dockerfile:1.7

# --- 阶段 1: 编译阶段 ---
FROM eclipse-temurin:25-jdk AS builder

WORKDIR /build

RUN apt-get update && apt-get install -y --no-install-recommends \
    git \
    maven \
    && rm -rf /var/lib/apt/lists/*

# 复制 Gradle 相关文件
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY gradle.properties .
COPY buildSrc buildSrc
COPY configs configs
COPY .gitmodules .
# Locked source verification needs the parent gitlink and submodule gitdir.
# This metadata remains in the builder stage and is not copied into the runtime image.
COPY .git .git
COPY third_party third_party

# 修复权限问题：确保 gradlew 可执行
RUN chmod +x gradlew

# 预构建 CloudSim Plus 源码依赖并预下载依赖
RUN --mount=type=cache,target=/root/.m2/repository \
    --mount=type=cache,target=/root/.gradle/caches \
    --mount=type=cache,target=/root/.gradle/wrapper \
    ./gradlew sanitizeCloudSimPlusJarManifest --no-daemon --configuration-cache
RUN --mount=type=cache,target=/root/.m2/repository \
    --mount=type=cache,target=/root/.gradle/caches \
    --mount=type=cache,target=/root/.gradle/wrapper \
    ./gradlew dependencies --no-daemon --configuration-cache

# 复制源码并构建 fatJar
COPY src src
RUN --mount=type=cache,target=/root/.m2/repository \
    --mount=type=cache,target=/root/.gradle/caches \
    --mount=type=cache,target=/root/.gradle/wrapper \
    ./gradlew fatJar --no-daemon --configuration-cache -Pcompress=true

# --- 阶段 2: 运行阶段 ---
FROM eclipse-temurin:25-jre

# 安装运行期可能需要的本地库依赖
RUN apt-get update && apt-get install -y --no-install-recommends \
    libgomp1 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# 从编译阶段复制产物
COPY --from=builder /build/build/libs/*-all.jar app.jar
COPY --from=builder /build/configs configs
COPY data data

# 创建结果输出目录
RUN mkdir runs

# 设置 JVM 环境变量 (优化 ZGC 和编码)
ENV JAVA_OPTS="-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -Dlogback.configurationFile=cloudsim-benchmark-logback.xml -XX:+UseZGC -XX:MaxGCPauseMillis=50 --enable-native-access=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/jdk.internal.misc=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED"

# 定义挂载点
VOLUME ["/app/runs", "/app/configs", "/app/data"]

# 默认执行帮助命令
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar \"$@\"", "--"]
CMD ["--help"]

# --- 阶段 1: 编译阶段 ---
FROM eclipse-temurin:23-jdk-jammy AS builder

WORKDIR /build

# 复制 Gradle 相关文件
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY gradle.properties .

# 预下载依赖 (利用镜像缓存)
RUN ./gradlew dependencies --no-daemon

# 复制源码并构建 fatJar
COPY src src
COPY configs configs
RUN ./gradlew fatJar --no-daemon -Pcompress=true

# --- 阶段 2: 运行阶段 ---
FROM eclipse-temurin:23-jre-jammy

# 安装 ND4J 可能需要的本地库依赖 (glibc 等已经在 jammy 中)
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
ENV JAVA_OPTS="-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -XX:+UseZGC -XX:MaxGCPauseMillis=50 --enable-native-access=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/jdk.internal.misc=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED"

# 定义挂载点
VOLUME ["/app/runs", "/app/configs", "/app/data"]

# 默认执行帮助命令
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar \"$@\"", "--"]
CMD ["--help"]

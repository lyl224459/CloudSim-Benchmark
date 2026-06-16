# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:25-jre

RUN apt-get update && apt-get install -y --no-install-recommends \
    libgomp1 \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 cloudsim \
    && useradd --uid 10001 --gid 10001 --home-dir /app --shell /usr/sbin/nologin cloudsim

WORKDIR /app

COPY app.jar app.jar
COPY configs configs
COPY data data
COPY container-provenance.txt container-provenance.txt

RUN mkdir runs && chown 10001:10001 runs

ENV JAVA_OPTS="-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -Dlogback.configurationFile=cloudsim-benchmark-logback.xml -Dcloudsim.log.dir=/app/runs/logs -XX:+UseZGC -XX:MaxGCPauseMillis=50 --enable-native-access=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/jdk.internal.misc=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED"

VOLUME ["/app/runs"]

USER 10001:10001

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar \"$@\"", "--"]
CMD ["--help"]

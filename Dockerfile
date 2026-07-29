# syntax=docker/dockerfile:1

# ---------- 1. フロントエンド ----------
FROM node:26-alpine AS frontend
WORKDIR /build

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build


# ---------- 2. バックエンド ----------
# Vue のビルド成果物を Spring の static に同梱して、単一 jar にする
FROM eclipse-temurin:25-jdk-alpine AS backend
WORKDIR /build

# 依存解決だけ先に済ませてレイヤキャッシュを効かせる
COPY backend/gradle/ gradle/
COPY backend/gradlew backend/settings.gradle.kts backend/build.gradle.kts ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY backend/src/ src/
COPY --from=frontend /build/dist/ src/main/resources/static/
RUN ./gradlew --no-daemon bootJar -x test \
    && cp build/libs/*.jar /build/app.jar


# ---------- 3. 実行 ----------
FROM eclipse-temurin:25-jre-alpine AS runtime

# root で動かさない。/data はボリュームマウント先
RUN addgroup -S app && adduser -S -G app app \
    && mkdir -p /data && chown app:app /data

WORKDIR /app
COPY --from=backend /build/app.jar app.jar
USER app

ENV DB_PATH=/data/cctasks.db \
    SESSION_DIR=/data/sessions \
    PORT=7000 \
    # 非力な本番サーバー向け (仕様書 §9)。コンテナのメモリ上限に対する割合で決める
    JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k"

EXPOSE 7000
VOLUME ["/data"]

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

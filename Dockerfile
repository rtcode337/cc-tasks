# syntax=docker/dockerfile:1

# ---------- 1. フロントエンド ----------
FROM node:26-alpine AS frontend
WORKDIR /build

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
# ビルド番号(フッター表示)用のコミットハッシュ。ビルドコンテキストに .git を
# 含めないため git からは引けず、CI(docker-publish)が build-arg で渡す。未指定なら nogit
ARG GIT_SHA
ENV GIT_SHA=$GIT_SHA
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
    # メモリの少ない環境でも動かす前提 (仕様書 §9)。ヒープはホストの搭載量ではなく
    # コンテナのメモリ上限に対する割合で決める (compose の mem_limit に追従する)
    JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k"

EXPOSE 7000
VOLUME ["/data"]

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

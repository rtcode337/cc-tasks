# cc-tasks

Claude Code に依頼したいタスクをメモ・管理する自分専用 Web アプリ。
仕様は [docs/仕様書_v0.1.md](docs/仕様書_v0.1.md) が正。迷ったらそちらを見る。

- 人間は PWA から出先でタスクを放り込む(Google OAuth、許可メール 1 件)
- Claude Code は MCP (`/mcp`, Streamable HTTP, 静的 API キー) 経由でタスクを読み書きする
- 完成後はこのアプリ自身のタスク管理をこのアプリで行う(ドッグフーディング)

## 構成

```
backend/    Java 21 + Spring Boot 3.5 + Spring Data JDBC + SQLite。REST と MCP が同居
frontend/   Vue 3 + Vite + TypeScript + Pinia + vite-plugin-pwa。SPA
Dockerfile  frontend build → backend build → JRE のマルチステージ。単一コンテナ
```

REST(`dev.cctasks.web`)と MCP(`dev.cctasks.mcp`)は **サービス層を共有する**。
ロジックは `ProjectService` / `TaskService` に置き、コントローラとツール定義は薄く保つ。

## 開発の回し方

### バックエンド単体

```bash
cd backend
./gradlew bootRun --args='--spring.profiles.active=dev'   # :8930
./gradlew test
```

`dev` プロファイルは **認証を通さない**(Google OAuth 無しで curl を叩けるようにするため)。
本番では絶対に有効化しない。DB は `backend/data/cctasks.db` に作られる。

### フロントエンド

```bash
cd frontend
npm install
npm run dev       # :8931 → /api と /mcp を :8930 にプロキシ
npm run build     # vue-tsc の型チェック込み
```

バックエンドを `dev` プロファイルで起動しておくこと。

### 単一コンテナで動かす(ローカルビルド)

```bash
cp .env.example .env   # 値を埋める
docker compose -f compose.build.yaml up --build
```

### 本番(GHCR + pull)

`main` への push で GitHub Actions([.github/workflows/docker-publish.yml](.github/workflows/docker-publish.yml))が
`ghcr.io/<owner>/cc-tasks` をビルド・公開する。本番はビルドせず `compose.yaml`(image を pull)を使い、
`docker compose pull && docker compose up -d` で更新するだけ。タグは `latest` と `sha-xxxxxxx`。
arm64 も出す場合はワークフローの matrix の arm64 行を有効化(無料 arm ランナーは public リポジトリ限定)。

## 動作確認の記録 (v0.1 時点)

| 項目 | 結果 |
|---|---|
| REST API 一式 (curl) | 全エンドポイント疎通。エラー形式も `{"error":{"code","message"}}` で統一 |
| SQLite 永続化 | 再起動後もデータが残る。`created_at` は ISO 8601 の TEXT、`archived` は 0/1 |
| MCP 4 ツール | initialize → tools/list → tools/call まで疎通。認証失敗は 401 |
| PWA | manifest / Service Worker 生成、`/tasks/:id` の直リンクも SPA にフォールバック |
| 認証 | 未認証 `/api` は 401、CSRF 無し POST は 403、ログイン試行は 20 回/分で 429 |
| Docker | イメージ 122 MB。`--memory 256m` で起動 1.8 秒 / RSS 138 MB |
| Web 版 Claude Code からの疎通 (M5) | **未検証**。§7.3 の注意どおり実地確認が必要 |

## ハマりどころ (触る前に読む)

### SQLite × Spring Data JDBC

Spring Data JDBC は SQLite 方言を同梱していない。以下の 2 つが無いと起動すらしない/黙って壊れる。

- `SqliteDialect` + `SqliteDialectProvider` を `META-INF/spring.factories` で登録している。
  消すと起動時に `NoDialectException`。
- `JdbcConfig` のコンバータ。特に **`Instant` は `JdbcValue` を返す書き込みコンバータでないと効かない**。
  素直に `Converter<Instant, String>` を書くと、Spring Data JDBC が先に列型を
  `java.sql.Timestamp` と決めてしまい、SQLite にエポックミリ秒が入る。

DDL は `backend/src/main/resources/schema.sql`。起動のたびに `CREATE TABLE IF NOT EXISTS` で流す。
列を足すときはここを直す(マイグレーションツールは入れていない)。

### OAuth クレデンシャル

`spring.security.oauth2.client.registration.google.client-id` を空文字で置くと
Spring Boot の検証で起動が落ちる。そのため `GoogleOAuthEnvironmentPostProcessor` が
`GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` が揃っているときだけ登録を組み立てる。
未設定でも起動はするが `/api` は 401 のままになる。

`PUBLIC_BASE_URL` は任意。設定すればリダイレクト URI の基点になり、未設定なら
`{baseUrl}`(`ForwardedHeaderFilter` 適用後のリクエスト)から導出する。
セッション Cookie の `Secure` は `application.yml` で **あえて指定していない** ——
Tomcat がリクエストのスキームを見て自動で付けるので、http ローカルでは非 Secure、
https 本番では Secure になり、`PUBLIC_BASE_URL` 無しでも両方でログインできる。
ここを `secure: true` で固定すると http ローカルでセッションが載らずログインループになる。

### フィルタの差し込み位置

ログインのレート制限は `OAuth2AuthorizationRequestRedirectFilter` の **手前** に入れる必要がある。
`UsernamePasswordAuthenticationFilter` の手前だと、認可リクエストのリダイレクトの方が
先に起きて素通りする。

### MCP ツールの description

`McpTaskTools` の description は Claude Code が使い方を判断する唯一の材料。
「何をするか」だけでなく **「いつ呼ぶか」** まで日本語で書く。
特に `add_note` の「セッション終了前に、次のセッションが続きから始められる粒度で書き戻す」は
このアプリの肝なので薄めない。

`get_task` は Markdown をそのまま返したいので `RawTextResultConverter` を指定している
(既定のコンバータだと JSON 文字列としてクォート・エスケープされる)。

## 規約

- コメント・UI 文言・MCP の description はすべて日本語
- ノートは追記オンリー。更新・削除の API は作らない(タイムラインの信頼性を優先)
- タスクの状態遷移に制約は設けない(手戻り・中止を許容)
- PATCH は「null のフィールドは変更しない」部分更新。空文字は「消す」
- タスク(メモ)のプロジェクト紐づけは任意(`project_id` は nullable)。未紐づけで放り込み、後から紐づけられる
- 一覧の並びは作成日時降順で固定(更新で順番が動くと探しづらいため `updated_at` ではなく `created_at`)
- UX は「タスクを素早く放り込む」優先。トップ = タスク入力 + 未着手一覧
- 「完了」は `status=done`(削除ではない)、「削除」は物理削除。完了タスクは `/tasks` の「完了したタスクを表示」で確認(10 件ずつページング。`?done=true&page=&size=`)
- コピーは複製に見えないようクリップボードアイコンでカード左上に置く
- 開発時は `spring-boot-devtools` で自動再起動(bootJar には入らず本番では無効)。反復は `./dev.sh`(backend dev + 継続コンパイル + Vite HMR)で回す。開くのは :8931、dev は認証なし
- セッションはディスク永続化(`server.servlet.session.persistent`、store-dir は `SESSION_DIR`=`/data/sessions`)。再起動・再デプロイでも再ログイン不要。timeout は 30d
- 管理系エンドポイント(Actuator 等)は追加しない

## MCP 接続(ドッグフーディング)

リポジトリ直下の `.mcp.json` は **Claude Code(MCP クライアント)側**の設定。
このディレクトリで Claude Code を起動すると自動で読まれ、`cc-tasks` サーバーに接続する。

接続先とキーは環境変数で展開するので **`.mcp.json` は書き換えない**。
シェルに 2 つ設定するだけ(値は `.env` と同じ):

```bash
export PUBLIC_BASE_URL=$(grep '^PUBLIC_BASE_URL=' .env | cut -d= -f2-)   # url = ${PUBLIC_BASE_URL}/mcp
export CC_TASKS_API_KEY=$(grep '^MCP_API_KEY=' .env | cut -d= -f2-)      # Bearer キー
```

`url` は `${PUBLIC_BASE_URL}/mcp`。OAuth のリダイレクト基点と同じ値を使い回す。

# cc-tasks

Claude Code に依頼したいタスクをメモ・管理する自分専用 Web アプリ。

出先でスマホ(PWA)からタスクを放り込み、帰宅後に Claude Code で消化する。
アプリ自身が MCP サーバーを持つので、Claude Code が直接タスクを読み、
実装中の気づきや進捗をタスクに書き戻せる ── セッション引き継ぎメモの自動化。

- 仕様: [docs/仕様書_v0.1.md](docs/仕様書_v0.1.md)
- 実装メモ・ハマりどころ: [CLAUDE.md](CLAUDE.md)

## 使い方の流れ

出先で思いついたら開いてメモを一言残す → 帰宅後にカードの ✳ ボタンで **Claude Code をそのまま開いて**
消化する(またはメモをコピーして貼る)→ 終わったらメモを **完了(削除)** する、という素早い運用を想定。

- **トップ**: タスク入力(数行)+ プロジェクト選択(任意)+ 保存。その下に未着手一覧をプロジェクトごとに折りたたみ表示(デフォルトは折りたたみ。グループは最新タスク順、グループ内は作成日時降順。開閉状態はブラウザに保存)
- **タスク一覧**: 未完了を作成日時降順で表示。「完了したタスクを表示」で完了分に切替(10 件ずつページング)。あとからプロジェクト紐づけも可
- **プロジェクト**: 作成・編集・アーカイブ
- ヘッダ: トップ / タスク / プロジェクト / ログアウト / テーマ切替(明暗)

画面を下に引っ張ると再読み込みできる(PWA にはリロード手段が無いため)。トップ・タスク・
プロジェクト・タスク詳細ではデータだけを取り直し、それ以外の画面ではページ全体をリロードする。

プロジェクト紐づけは任意。未紐づけのまま放り込み、あとから紐づけられる。
各カード左上の 📋 アイコンで本文をクリップボードへコピー。「完了」で done に、「削除」で物理削除する。
📋 の隣の ✳ アイコンは **Claude Code へのハンドオフ**:タスク内容をプリフィルした
`https://claude.ai/code?prompt=…&repositories=…` を新規タブで開く。Claude モバイルアプリは
クエリのプリフィルを引き継がないため、JS 遷移でユニバーサルリンクを回避し、スマホでも
ブラウザ版 Claude Code が開くようにしている。`repositories` にはプロジェクトの
リポジトリ URL のうち GitHub のものを `owner/repo` に変換して渡す。送信はプリフィルのみで、
実行開始はその場でワンタップ。

## 構成

| レイヤ | 技術 |
|---|---|
| バックエンド | Java 21 / Spring Boot 3.5 / Spring Data JDBC / SQLite |
| MCP | Spring AI MCP Server (WebMVC, Streamable HTTP) |
| フロントエンド | Vue 3 / Vite / TypeScript / Pinia / vite-plugin-pwa |
| 配信 | マルチステージ Docker ビルドで単一コンテナ |

REST(PWA 用)と MCP(Claude Code 用)は同じ Spring Boot アプリに同居し、サービス層を共有する。

```
[スマホ/ブラウザ PWA]          [Claude Code]
   Google OAuth セッション        Bearer: API キー
            \                    /
         リバースプロキシ (TLS 終端)
                   |
      Spring Boot (単一コンテナ)
        /         → Vue SPA
        /api/**   → REST (要セッション)
        /mcp      → MCP  (要 API キー)
        SQLite: /data/cctasks.db
```

## 起動

### ローカル開発(ホットリロード)

```bash
./dev.sh
```

backend(dev プロファイル / **認証なし**)+ 継続コンパイル + フロント(Vite HMR)を
まとめて起動する。ブラウザで開くのは **http://localhost:7001**。

- フロント: 保存で即 HMR
- バックエンド: 保存 → 再コンパイル → devtools が数秒で自動再起動
- dev プロファイルは認証を通さないので**ログイン不要**

個別に起動する場合:

```bash
# backend (:7000)。継続コンパイルを別ターミナルで回すと保存時に自動再起動する
cd backend && ./gradlew bootRun --args='--spring.profiles.active=dev'
cd backend && ./gradlew -t classes          # 別ターミナル

# frontend (:7001)。/api と /mcp を :7000 にプロキシ
cd frontend && npm install && npm run dev
```

> Docker を毎回リビルドして確認すると遅く、ホットリロードも効かない。
> 反復は上記の dev ループで行い、Google ログイン込みで確認したいときだけ
> `docker compose` を使う(セッションは永続化されるので再起動しても再ログイン不要)。

### 本番運用(GHCR から pull するだけ)

イメージは **GitHub Actions が `main` への push でビルドし GHCR へ公開**する
([.github/workflows/docker-publish.yml](.github/workflows/docker-publish.yml))。
本番サーバーはビルドせず、リポジトリ(compose.yaml と .env)だけ置いて pull する。

```bash
cp .env.example .env    # 初回のみ。値を埋める

# リポジトリが非公開(=GHCR イメージも非公開)の間は初回だけ GHCR にログイン。
# read:packages 権限の PAT を使う。リポジトリを公開しパッケージも public にすれば不要。
echo "$GHCR_TOKEN" | docker login ghcr.io -u <github-user> --password-stdin

# 更新はこれだけ
docker compose pull && docker compose up -d
```

- `compose.yaml` は `127.0.0.1:7000` にだけ公開する。HTTPS 終端と外部公開は手前のリバースプロキシの責務
- タグは `latest` と `sha-xxxxxxx`。切り戻しは `CCTASKS_IMAGE=ghcr.io/rtcode337/cc-tasks:sha-xxxxxxx docker compose up -d`
- データ(SQLite・セッション)は名前付きボリューム `cctasks-data` に残るので、pull・再作成しても消えない

### ローカルでイメージをビルドして確認

本番同等(Google ログイン込み)を手元で試すとき:

```bash
cp .env.example .env
docker compose -f compose.build.yaml up -d --build
```

> 普段の反復は `./dev.sh`(ホットリロード)で十分。Docker は本番同等確認のときだけ使う。

### 環境変数

| 変数 | 用途 |
|---|---|
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Google OAuth クレデンシャル |
| `ALLOWED_EMAIL` | ログインを許可する Google アカウント (1 件) |
| `MCP_API_KEY` | MCP 用の静的 API キー。`openssl rand -base64 32` 等で生成 |
| `DB_PATH` | SQLite ファイルパス (既定 `/data/cctasks.db`) |
| `PUBLIC_BASE_URL` | **任意**(サーバー)。OAuth リダイレクトは未設定ならリクエストから自動導出。ただし Claude Code 側の `.mcp.json` が `${PUBLIC_BASE_URL}/mcp` に使うので、MCP を使うなら設定する |

Google Cloud Console 側の「承認済みのリダイレクト URI」には
`<公開 URL>/login/oauth2/code/google` を登録する(`PUBLIC_BASE_URL` を設定すればその値が基点、
未設定ならプロキシ経由の実 URL)。セッション Cookie の `Secure` はリクエストのスキームから
自動判定される(https なら付与、http なら非付与)ので、http ローカル・https 本番のどちらでも動く。

## Claude Code から使う

`.mcp.json` は **書き換え不要**。接続先とキーは環境変数で展開されるので、
シェルに `PUBLIC_BASE_URL` と `CC_TASKS_API_KEY` を設定するだけでよい。

```bash
# .env の値をそのままシェルに流し込む例
export PUBLIC_BASE_URL=$(grep '^PUBLIC_BASE_URL=' .env | cut -d= -f2-)
export CC_TASKS_API_KEY=$(grep '^MCP_API_KEY=' .env | cut -d= -f2-)
```

リポジトリの [.mcp.json](.mcp.json) は次のとおり(値はコミットしない):

```json
{
  "mcpServers": {
    "cc-tasks": {
      "type": "http",
      "url": "${PUBLIC_BASE_URL}/mcp",
      "headers": { "Authorization": "Bearer ${CC_TASKS_API_KEY}" }
    }
  }
}
```

公開しているツール:

| ツール | 用途 |
|---|---|
| `list_tasks` | プロジェクトの残タスク一覧 (status 省略時は done 以外) |
| `get_task` | タスク詳細を、そのままプロンプトとして読める Markdown で取得 |
| `update_task_status` | 着手時 `in_progress` / 完了時 `done` |
| `add_note` | 進捗・引き継ぎ事項の書き戻し (追記のみ) |

## API

すべて `/api` 配下、JSON、要セッション。エラーは `{"error":{"code":"...","message":"..."}}`。

| メソッド | パス | 説明 |
|---|---|---|
| GET | `/api/me` | ログイン中ユーザー |
| GET | `/api/projects` | プロジェクト一覧 (`?archived=false` が既定、`?archived=` で全件) |
| POST | `/api/projects` | 作成 |
| PATCH | `/api/projects/{id}` | 更新 (name, repoUrls, description, archived)。repoUrls は配列(空配列で全消し) |
| GET | `/api/tasks` | 一覧 (`?projectId=&status=`) |
| POST | `/api/tasks` | 作成 (projectId は任意) |
| GET | `/api/tasks?done=false` | 未完了(done 以外)一覧 |
| GET | `/api/tasks?done=true&page=&size=` | 完了一覧をページングで取得(`{items,total,page,size,totalPages}`) |
| GET | `/api/tasks/{id}` | 詳細 (notes 込み) |
| PATCH | `/api/tasks/{id}` | 更新 (projectId, title, context, acceptanceCriteria, outOfScope, status) |
| DELETE | `/api/tasks/{id}` | 削除 (notes カスケード) |
| POST | `/api/tasks/{id}/notes` | ノート追記 (author は `human` 固定) |

JSON は camelCase。リクエストは snake_case (`acceptance_criteria` 等) でも受け付ける。
更新系は `X-XSRF-TOKEN` ヘッダが必要(Cookie の `XSRF-TOKEN` をそのまま返す)。

## テスト

```bash
cd backend  && ./gradlew test      # SQLite の型変換 / MCP の Markdown 整形 / API キー認証
cd frontend && npm run typecheck
```

## 将来構想

- **✳ ハンドオフを Claude アプリ起動に戻す**: Claude モバイルアプリはユニバーサルリンクの
  クエリ(`prompt` / `repositories`)を引き継がないため、現状はやむを得ず空タブ + JS 遷移で
  ユニバーサルリンクを回避し、スマホでもブラウザ版 claude.ai/code を開いている
  ([ClaudeCodeButton.vue](frontend/src/components/ClaudeCodeButton.vue))。
  アプリがプリフィルを引き継ぐようになったら、この回避をやめて通常のリンク遷移
  (タップでアプリが開く)に戻したい

## ライセンス

[MIT License](LICENSE)

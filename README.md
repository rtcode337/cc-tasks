# cc-tasks

Claude Code に依頼したいタスクをメモ・管理する自分専用 Web アプリ。

出先でスマホ(PWA)からタスクを放り込み、帰宅後に Claude Code で消化する。
あわせて、**すべての Claude Code 環境に効かせたい共通ルール**を 1 箇所で管理し、
まとめて 1 本の Markdown として取り出せる。

- 画面・操作の仕様(現行): [docs/画面仕様.md](docs/画面仕様.md)
- 当初の仕様: [docs/仕様書_v0.1.md](docs/仕様書_v0.1.md)
- 実装メモ・ハマりどころ: [CLAUDE.md](CLAUDE.md)

## 使い方の流れ

出先で思いついたら開いてメモを一言残す → 帰宅後にカードの ✳ ボタンで **Claude Code をそのまま開いて**
消化する(またはメモをコピーして貼る)→ 終わったらメモを **完了** にする、という素早い運用を想定。

扱うものは 3 つだけ:

| | 中身 | どこで | ポイント |
|---|---|---|---|
| **タスク** | Claude Code に依頼したいこと(数行のメモ) | トップで入力・一覧、完了分は `/done` | 状態は **未完了 / 完了 の 2 つだけ**。カードの ✳ から Claude Code へハンドオフ |
| **プロジェクト** | タスクの入れ物(リポジトリ 1 つに対応する想定) | トップ(専用画面は無い) | 紐づけは**任意**。未紐づけは「未分類」として一覧の末尾(トップでは 0 件でも出る)。片付いたらアーカイブ |
| **ルール** | 全 Claude Code 環境に効かせたい決まりごと(Markdown) | ルール画面 | 有効なものを表示順に **1 本へ連結**してコピーし、各環境の指示ファイルに貼る。**規約リポジトリ**を設定すると ✳ で開くセッションに常に含まれる(→ [ルールを Claude Code に効かせる](#ルールを-claude-code-に効かせる)) |

主な操作:

| したいこと | どうする |
|---|---|
| タスクを放り込む | トップ上部に入力して保存(プロジェクトは選んでも選ばなくてもよい) |
| Claude Code で消化する | カードの **✳**(内容をプリフィルして `claude.ai/code` を開く)。**📋** は本文コピー |
| 編集・削除する | **カードの本文を押す**とモーダル。削除もここから(誤タップ防止で一覧には置かない) |
| 完了 / 未完了を切り替える | カードのボタンから(モーダルに状態の選択は無い) |
| 並び替える・別プロジェクトへ移す | 行を**長押ししてからドラッグ**。グループ見出しならプロジェクトの並び。「未分類」へ落とせば紐づけが外れる |
| プロジェクトを作る・編集する | 「未完了」見出しの **＋ プロジェクト** / 各グループ見出しの **編集** |
| プロジェクトを片付ける | 編集モーダルからアーカイブ(**未完了 0 件のときだけ**)。削除はアーカイブ済みのときだけ |
| 再読み込みする | 画面を**下に引っ張る**(PWA にはリロード手段が無いため) |

画面ごとの詳しい挙動・制約は [docs/画面仕様.md](docs/画面仕様.md) にまとめてある。

## スクリーンショット

| トップ(未完了) | 編集モーダル | ルール |
|---|---|---|
| <img src="docs/images/home.png" alt="トップ(未完了)" height="340"> | <img src="docs/images/task-edit.png" alt="編集モーダル" height="340"> | <img src="docs/images/rules.png" alt="ルール" height="340"> |

<sub>ローカル dev(認証なし)に架空のサンプルデータ(`sample-*`)を入れて撮ったもの。</sub>

## 構成

| レイヤ | 技術 |
|---|---|
| バックエンド | Java 21 / Spring Boot 3.5 / Spring Data JDBC / SQLite |
| フロントエンド | Vue 3 / Vite / TypeScript / Pinia / vite-plugin-pwa |
| 配信 | マルチステージ Docker ビルドで単一コンテナ |

```
[スマホ/ブラウザ PWA]
   Google OAuth セッション
            |
   リバースプロキシ (TLS 終端)
            |
  Spring Boot (単一コンテナ)
    /         → Vue SPA
    /api/**   → REST (要セッション)
    SQLite: /data/cctasks.db
```

## 起動

### ローカル開発(ホットリロード)

```bash
./dev.sh
```

backend(dev プロファイル / **認証なし**)+ 継続コンパイル + フロント(Vite HMR)を
まとめて起動する。ブラウザで開くのは **`http://localhost:7001`**。

- フロント: 保存で即 HMR
- バックエンド: 保存 → 再コンパイル → devtools が数秒で自動再起動
- dev プロファイルは認証を通さないので**ログイン不要**

個別に起動する場合:

```bash
# backend (:7000)。継続コンパイルを別ターミナルで回すと保存時に自動再起動する
cd backend && ./gradlew bootRun --args='--spring.profiles.active=dev'
cd backend && ./gradlew -t classes          # 別ターミナル

# frontend (:7001)。/api を :7000 にプロキシ
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
| `DB_PATH` | SQLite ファイルパス (既定 `/data/cctasks.db`) |
| `PUBLIC_BASE_URL` | **任意**。OAuth リダイレクトは未設定ならリクエストから自動導出する。プロキシが `X-Forwarded-*` を送らない場合のみ設定 |

Google Cloud Console 側の「承認済みのリダイレクト URI」には
`<公開 URL>/login/oauth2/code/google` を登録する(`PUBLIC_BASE_URL` を設定すればその値が基点、
未設定ならプロキシ経由の実 URL)。セッション Cookie の `Secure` はリクエストのスキームから
自動判定される(https なら付与、http なら非付与)ので、http ローカル・https 本番のどちらでも動く。

## ルールを Claude Code に効かせる

**ルール**画面で、全環境に効かせたい決まりごとを Markdown で何本でも書ける。
「まとめて表示」を押すと、有効なルールが表示順に 1 本の Markdown へ連結されるので、
それをコピーして各環境の指示ファイルに貼る。先頭には次の 2 つが自動で付く:

- 「特定リポジトリの説明ではなく作業対象のすべてのリポジトリに適用する共通ルール」という前置き
- 「規約リポジトリ(下記)自体は配布専用なので自動では更新しない。更新はユーザーが
  更新後の Markdown を明示的に渡したときだけ」というルール

| 環境 | 貼り先 | 有効範囲 |
|---|---|---|
| CLI 版 | `~/.claude/rules/cc-tasks.md` | マシン上の全リポジトリ |
| Web 版 | 規約リポジトリ(下記)のルート `CLAUDE.md` | ✳ 経由の全セッション |
| Web 版 (代替) | リポジトリの `.claude/rules/cc-tasks.md` | そのリポジトリ |

ユーザーレベルの設定はクラウドセッションに引き継がれないため、Web 版はリポジトリ経由で
届ける。ルール画面の一覧の下で**規約リポジトリ**(GitHub の `owner/repo` か URL)を
設定すると、✳(Claude Code へのハンドオフ)で開くセッションにそのリポジトリが常に含まれ、
ルート直下の CLAUDE.md が読み込まれる —— 連結ルールをそこに貼っておけば、
リポジトリごとに置いて回らなくてよい。空にして保存すると解除。

ルールは**指示であって強制ではない**(CLAUDE.md と同じくシステムプロンプトの後の
ユーザーメッセージとして届く)。絶対に破らせたくない操作は Claude Code の
フック側で止めること。

## API

すべて `/api` 配下、JSON、要セッション。エラーは `{"error":{"code":"...","message":"..."}}`。

| メソッド | パス | 説明 |
|---|---|---|
| GET | `/api/me` | ログイン中ユーザー |
| GET | `/api/projects` | プロジェクト一覧 (`?archived=false` が既定、`?archived=` で全件) |
| POST | `/api/projects` | 作成 |
| PATCH | `/api/projects/{id}` | 更新 (name, repoUrls, description, archived)。repoUrls は配列(空配列で全消し)。`archived:true` は未完了が 0 件のときだけ通る(残っていれば 400) |
| DELETE | `/api/projects/{id}` | 削除。**アーカイブ済みのときだけ**通る(未アーカイブなら 400)。紐づくタスクも完了分ごと消える |
| PUT | `/api/projects/order` | 並び替え。全プロジェクトの id を望む順で送る (`{"ids":[...]}`)。並び替え後の全件を返す |
| GET | `/api/tasks` | 一覧 (`?projectId=&status=`) |
| POST | `/api/tasks` | 作成 (projectId は任意) |
| GET | `/api/tasks?done=false` | 未完了(done 以外)一覧 |
| PUT | `/api/tasks/order` | プロジェクト内の並び替え。`{"projectId":1,"ids":[...]}` を望む順で送る(未紐づけは `projectId:null`)。画面に出ている分だけの部分集合でよいが、別プロジェクトのタスクを混ぜると 400 |
| GET | `/api/tasks/{id}` | 詳細 (プロジェクト名込み) |
| PATCH | `/api/tasks/{id}` | 更新 (projectId, title, status)。null のフィールドは変更しない。**`projectId:0` は「紐づけを外す」**(未分類に戻す) |
| DELETE | `/api/tasks/{id}` | 削除 |
| GET | `/api/rules` | ルール一覧 (表示順。無効なものも含む) |
| GET | `/api/rules/combined` | 有効なルールを表示順に連結した Markdown (`{"markdown":"..."}`)。先頭に適用範囲の前置きと「規約リポジトリの扱い」ルールが付く |
| POST | `/api/rules` | 作成 (title, body, enabled) |
| PATCH | `/api/rules/{id}` | 更新 (null のフィールドは変更しない) |
| DELETE | `/api/rules/{id}` | 削除 |
| PUT | `/api/rules/order` | 並び替え。全ルールの id を望む順で送る。並び順がそのまま連結順になる |
| GET | `/api/rules/settings` | ルール画面の設定 (`{"rulesRepoUrl":"..."}`。未設定なら null) |
| PATCH | `/api/rules/settings` | 規約リポジトリの更新。null は変更しない、空文字で解除 |

JSON は camelCase。リクエストは snake_case (`project_id` 等) でも受け付ける。
更新系は `X-XSRF-TOKEN` ヘッダが必要(Cookie の `XSRF-TOKEN` をそのまま返す)。

## テスト

```bash
cd backend  && ./gradlew test      # SQLite の型変換 / 並び替え / ルールの連結
cd frontend && npm run typecheck
```

## 将来構想

- **✳ ハンドオフの iOS 依存を減らす**: スマホでのプリフィルは「リンク長押し →
  Safari で開く、を一度やってユニバーサルリンクを無効化」+「PWA では `x-safari-`
  スキーム(非公式、iOS 17+)で Safari 本体を開く」という iOS の挙動頼みで成立している。
  Claude モバイルアプリがユニバーサルリンクのクエリ(`prompt` / `repositories`)を
  引き継ぐようになれば、これらの回避は不要になり素の直リンクに戻せる

## ライセンス

[MIT License](LICENSE)

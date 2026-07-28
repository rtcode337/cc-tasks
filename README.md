# cc-tasks

Claude Code に依頼したいタスクをメモ・管理する自分専用 Web アプリ。

出先でスマホ(PWA)からタスクを放り込み、帰宅後に Claude Code で消化する。
あわせて、**すべての Claude Code 環境に効かせたい共通ルール**を 1 箇所で管理し、
まとめて 1 本の Markdown として取り出せる。

- 仕様: [docs/仕様書_v0.1.md](docs/仕様書_v0.1.md)
- 実装メモ・ハマりどころ: [CLAUDE.md](CLAUDE.md)

## 使い方の流れ

出先で思いついたら開いてメモを一言残す → 帰宅後にカードの ✳ ボタンで **Claude Code をそのまま開いて**
消化する(またはメモをコピーして貼る)→ 終わったらメモを **完了(削除)** する、という素早い運用を想定。

- **トップ**: タスク入力(数行)+ プロジェクト選択(任意)+ 保存。その下に未完了一覧をプロジェクトごとに折りたたみ表示(デフォルトは折りたたみ。プロジェクトの並び順で並び、**プロジェクト未設定のタスクは「未分類」として一番下**。開閉状態はブラウザに保存)。タスクが 0 件のプロジェクトも「放り込み先」として並ぶ(未分類も 0 件で出る)
- **完了したタスク**: トップの左下のリンクから。**見え方はトップの未完了一覧と同じ**(プロジェクトごとに折りたたみ、未分類は末尾)。カードの「未完了に戻す」で戻せる
- **アーカイブしたプロジェクト**: トップの右下のリンクから。見え方はトップと同じで、ここから戻す
- **ルール**: 全 Claude Code 環境に効かせたい決まりごとを Markdown で複数管理。「まとめて表示」で 1 本に連結してコピーできる(長押しドラッグの並び順がそのまま連結順)
- ヘッダ: トップ / ルール / ログアウト / テーマ切替(明暗)

**プロジェクトの作成・編集はトップで完結する**(専用画面は持たない)。新規は「未完了」見出しの
右端の「＋ プロジェクト」、編集は各グループ見出しの右端の「編集」から。
アーカイブは編集モーダルの右上のボタン(アーカイブ済みなら「アーカイブから戻す」)で、
押すと入力中の内容ごと保存して閉じる。**アーカイブできるのは未完了が 0 件のときだけ**
(戻すのは無条件)——片付いていないタスクごとトップから消えると取りこぼすため。
**削除はアーカイブ済みのときだけ**、同じ行の右端の「削除」から。
完了したものを含め、紐づくタスクも一緒に消える(戻せない)。

**並び替え**は行のどこでも **長押し(ロングタップ)してからドラッグ**(マウス・タッチ両対応)。
グループ見出しを長押しすると **プロジェクトの並び**が、カードを長押しすると
**そのプロジェクト内のタスクの並び**が変わる。長押しの前に指を動かせば普通のスクロールのままなので、
一覧の操作を邪魔しない。タスクの手動並び順はプロジェクト内でだけ効き、
新しく放り込んだタスクはグループの先頭に積まれる。
プロジェクトをまたぐ並びは従来どおり作成日時降順のまま。

カードを **別のプロジェクトのグループまで運んで離すと、そのプロジェクトへ移る**
(受け皿になっているグループに枠が付く)。落とした位置にそのまま入る。
「未分類」のグループへ運べば紐づけを外せる(トップでは未分類グループを 0 件でも出すので、
落とす先は常にある)。

画面を下に引っ張ると再読み込みできる(PWA にはリロード手段が無いため)。トップ・完了・
ルール・アーカイブ一覧ではデータだけを取り直し、それ以外の画面ではページ全体をリロードする。

プロジェクト紐づけは任意。未紐づけのまま放り込み、あとから紐づけられる。
各カード右上の 📋 アイコンで本文をクリップボードへコピー。**カードの本文を押すと編集モーダル**が開き、「完了」で done にする。
タスクの状態は **未完了 / 完了 の 2 つだけ**で、切り替えはカードのボタンからだけ行う(編集モーダルに状態の選択は無い)。
物理削除は誤タップを避けるため一覧には置かず、編集モーダルの「削除」から行う。
📋 の隣の ✳ アイコンは **Claude Code へのハンドオフ**:タスク内容をプリフィルした
`https://claude.ai/code?prompt=…&repositories=…` を新規タブで開く。`repositories` には
プロジェクトのリポジトリ URL のうち GitHub のものを `owner/repo` に変換して渡す。
送信はプリフィルのみで、実行開始はその場でワンタップ。
スマホは初期状態ではユニバーサルリンクで Claude アプリが開き、アプリはクエリの
プリフィルを引き継がないため内容が失われる(中継ページ経由の JS 遷移でも回避できなかった)。
**一度リンクを長押しして「Safari で開く」を選ぶと** iOS がそれを覚えてブラウザで開くようになり、
以後はプリフィルが効く。ホーム画面追加(PWA)の場合はアプリ内ブラウザではなく
`x-safari-` スキームで Safari 本体を開き、claude.ai のログインセッションを使えるようにしている。

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
まとめて起動する。ブラウザで開くのは **http://localhost:7001**。

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
それをコピーして各環境の指示ファイルに貼る:

| 環境 | 貼り先 | 有効範囲 |
|---|---|---|
| CLI 版 | `~/.claude/rules/cc-tasks.md` | マシン上の全リポジトリ |
| Web 版 | リポジトリの `.claude/rules/cc-tasks.md` | そのリポジトリ |

ユーザーレベルの設定はクラウドセッションに引き継がれないため、Web 版だけは
リポジトリごとに置く必要がある。

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
| GET | `/api/rules/combined` | 有効なルールを表示順に連結した Markdown (`{"markdown":"..."}`) |
| POST | `/api/rules` | 作成 (title, body, enabled) |
| PATCH | `/api/rules/{id}` | 更新 (null のフィールドは変更しない) |
| DELETE | `/api/rules/{id}` | 削除 |
| PUT | `/api/rules/order` | 並び替え。全ルールの id を望む順で送る。並び順がそのまま連結順になる |

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

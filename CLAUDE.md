# cc-tasks

Claude Code に依頼したいタスクをメモ・管理する自分専用 Web アプリ。
設計(画面・操作、データモデル、API)は [docs/詳細設計.md](docs/詳細設計.md) が正。迷ったらそちらを見る。
(旧 `docs/画面仕様.md` と `docs/仕様書_v0.1.md` は 2026-07 に詳細設計.md へ統合・削除した。)

- 人間は PWA から出先でタスクを放り込む(Google OAuth、許可メール 1 件)
- 全 Claude Code 環境に効かせたい共通ルールを「ルール」画面で管理し、連結してコピーする
- 完成後はこのアプリ自身のタスク管理をこのアプリで行う(ドッグフーディング)

**MCP サーバー機能とタスクの経緯(notes)は 2026-07 に廃止した。** MCP は
「使いたい機能ではない」と判断して丸ごと削除(`.mcp.json`・API キー認証・Spring AI 依存ごと)。
notes も同時に廃止し、`notes` テーブルは `SchemaMigrations` が DROP する。
過去のドキュメントやコミットに出てくる `list_tasks` / `add_note` はもう存在しない。

## 構成

```
backend/    Java 25 + Spring Boot 4.1 + Spring Data JDBC + SQLite。REST のみ
frontend/   Vue 3 + Vite + TypeScript + Pinia + vite-plugin-pwa。SPA
Dockerfile  frontend build → backend build → JRE のマルチステージ。単一コンテナ
```

ロジックは `ProjectService` / `TaskService` / `RuleService` に置き、コントローラは薄く保つ。

## 開発の回し方

### バックエンド単体

```bash
cd backend
./gradlew bootRun --args='--spring.profiles.active=dev'   # :7000
./gradlew test
```

`dev` プロファイルは **認証を通さない**(Google OAuth 無しで curl を叩けるようにするため)。
本番では絶対に有効化しない。DB は `backend/data/cctasks.db` に作られる。

### フロントエンド

```bash
cd frontend
npm install
npm run dev       # :7001 → /api を :7000 にプロキシ
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
amd64 / arm64 の両方をネイティブランナーで並列ビルドして 1 マニフェストにまとめる
(arm64 の無料 `ubuntu-24.04-arm` ランナーは public リポジトリ限定)。

### CI と依存更新

PR と `main` への push で [.github/workflows/ci.yml](.github/workflows/ci.yml) が
`./gradlew test` と `npm run build`(vue-tsc の型チェック込み)を回す。
docker-publish はビルドするだけでテストを実行しないため、マージ前の検証はこちらが担う。
依存更新は Dependabot([.github/dependabot.yml](.github/dependabot.yml))が週 1 回
npm / Gradle / Docker ベースイメージ / GitHub Actions の更新 PR を作る
(パッチ・マイナーはエコシステムごとに 1 本へグループ化、メジャーだけ個別 PR)。
更新 PR は ci が通ったのを確認してからマージする。

## 動作確認の記録 (v0.1 時点)

| 項目 | 結果 |
|---|---|
| REST API 一式 (curl) | 全エンドポイント疎通。エラー形式も `{"error":{"code","message"}}` で統一 |
| SQLite 永続化 | 再起動後もデータが残る。`created_at` は ISO 8601 の TEXT、`archived` は 0/1 |
| PWA | manifest / Service Worker 生成、未知のパスの直リンクも SPA にフォールバック |
| 認証 | 未認証 `/api` は 401、CSRF 無し POST は 403、ログイン試行は 20 回/分で 429 |
| Docker | イメージ 122 MB。`--memory 256m` で起動 1.8 秒 / RSS 138 MB |

## ハマりどころ (触る前に読む)

### SQLite × Spring Data JDBC

Spring Data JDBC は SQLite 方言を同梱していない。以下の 2 つが無いと起動すらしない/黙って壊れる。

- `SqliteDialect` + `SqliteDialectProvider` を `META-INF/spring.factories` で登録している。
  消すと起動時に `NoDialectException`。
- `JdbcConfig` のコンバータ。特に **`Instant` は `JdbcValue` を返す書き込みコンバータでないと効かない**。
  素直に `Converter<Instant, String>` を書くと、Spring Data JDBC が先に列型を
  `java.sql.Timestamp` と決めてしまい、SQLite にエポックミリ秒が入る。

DDL は `backend/src/main/resources/schema.sql`。起動のたびに `CREATE TABLE IF NOT EXISTS` で流す。
ただしこれは新規 DB にしか効かないため、**列を足すときは schema.sql と `SchemaMigrations`
(起動時に冪等な ALTER を当てる)の両方を直す**(マイグレーションツールは入れていない)。

再起動後の最初の書き込みが極端に遅くなる問題への対策が 2 つ入っている。
JDBC URL の `synchronous=NORMAL`(WAL ではコミット毎の fsync 不要。HDD スピンアップ待ちの回避)と、
`WriteWarmup`(起動時に INSERT + DELETE を 1 コミットして、書き込み経路の JIT とディスクの
初回コストを前払い)。外すと HDD がスピンダウンする本番環境で初回保存が数秒〜十数秒待ちに戻る。

読み取り側(初回アクセスが極端に重い)にも対をなす対策が `HttpWarmup` に入っている。
起動直後に自分自身へ GET を投げて MVC・フィルタチェーンのクラスロードと JIT を前払いし
(ローカル実測で初回 85ms → 5ms。本番ではこのクラスロードが眠った HDD からの jar 読みになり
数秒〜十数秒に化ける)、さらに 5 分ごとに静的資材(jar 内)と SQLite を読んでページキャッシュに
留める(キャッシュヒットはディスク I/O ゼロなので HDD は眠ったままでよい)。あわせて
`SpaWebConfig` で Vite のハッシュ付き `/assets/**` に `Cache-Control: max-age=1y, immutable`
を付け、再訪時の取り直しを無くしている。

### OAuth クレデンシャル

`spring.security.oauth2.client.registration.google.client-id` を空文字で置くと
Spring Boot の検証で起動が落ちる。そのため `GoogleOAuthEnvironmentPostProcessor` が
`GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` が揃っているときだけ登録を組み立てる。
未設定でも起動はするが `/api` は 401 のままになる。

`PUBLIC_BASE_URL` は任意。設定すればリダイレクト URI の基点になり、未設定なら
`{baseUrl}`(`ForwardedHeaderFilter` 適用後のリクエスト)から導出する。
ただし Tomcat の `RemoteIpValve` は `X-Forwarded-Proto: https` を見ると
`X-Forwarded-Port` が無い限りポートを 443 に固定し、**非標準ポート公開だと
`{baseUrl}` のポートが落ちて `redirect_uri` が Google 登録値と食い違う**。
リバースプロキシによってはポートを `X-Forwarded-Host` ではなく **`Host` ヘッダ**
(例 `Host: example.me:8443`)でしか伝えてこないため、これが顕在化する。
これを避けるため `ForwardedRedirectUriResolver` が origin を自前導出する:
スキーム=`X-Forwarded-Proto`、ホスト=`X-Forwarded-Host`(あれば)→無ければ `Host` ヘッダ。
`Host` はポートを保持しているのでポートが残る(Next.js の travel-log と同じ挙動)。
`PUBLIC_BASE_URL` を明示設定したときはテンプレートが絶対 URL なので書き換えは無効。
**Google Console の「承認済みリダイレクト URI」にはポート込みで登録が必要**
(例 `https://example.me:8443/login/oauth2/code/google`)。
セッション Cookie の `Secure` は `application.yml` で **あえて指定していない** ——
Tomcat がリクエストのスキームを見て自動で付けるので、http ローカルでは非 Secure、
https 本番では Secure になり、`PUBLIC_BASE_URL` 無しでも両方でログインできる。
ここを `secure: true` で固定すると http ローカルでセッションが載らずログインループになる。

### フィルタの差し込み位置

ログインのレート制限は `OAuth2AuthorizationRequestRedirectFilter` の **手前** に入れる必要がある。
`UsernamePasswordAuthenticationFilter` の手前だと、認可リクエストのリダイレクトの方が
先に起きて素通りする。

## 規約

- コメント・UI 文言はすべて日本語
- タスクの状態遷移に制約は設けない(手戻り・中止を許容)
- PATCH は「null のフィールドは変更しない」部分更新。空文字は「消す」。
  **数値の id はこれだと「消す」を表せないので、`PATCH /api/tasks/{id}` の `projectId` だけは
  `0`(`TaskService.UNLINK_PROJECT_ID`)を「紐づけを外す」の意味に使う**(id は 1 から振られるので衝突しない)。
  フロント側の定数は `api/types.ts` の `UNLINK_PROJECT_ID`
- タスク(メモ)のプロジェクト紐づけは任意(`project_id` は nullable)。未紐づけで放り込み、後から紐づけられる
- 一覧の並びの既定は作成日時降順(更新で順番が動くと探しづらいため `updated_at` ではなく `created_at`)。
  そのうえで **プロジェクト内だけは手動並び替えを効かせる**(`tasks.sort_order` 昇順 → 作成日時降順)。
  並び替えで 1, 2, 3, … を振り、新規タスクは 0 のままなのでグループの先頭に積まれる(放り込む UX を壊さない)。
  `sort_order` は *プロジェクト内* の順序なので、`projectId` で絞らない一覧では
  使わず作成日時降順のままにする —— プロジェクトをまたいで番号が混ざると探しづらいため。
  この分岐は SQL 側(`ORDER BY CASE WHEN :projectId IS NULL THEN 0 ELSE sort_order END, …`)と
  フロント側(ストアはフラットに作成日時降順、トップのグループ内だけ `compareInProject`)の両方に入っている
- UX は「タスクを素早く放り込む」優先。トップ = タスク入力 + 未完了一覧(プロジェクトごとに折りたたみ、デフォルトは閉。開いたグループを localStorage `cc-tasks-home-expanded` に保存)。並びはプロジェクトの並び順(`projects.sort_order`)
- **プロジェクト未設定のタスクは「未分類」グループとして一番下**に出す(`lib/groups.ts` の
  `withUnlinkedGroup()`。`TaskGroup.project` が null のかたまり、key は `none`)。
  他と同じ見た目・同じ折りたたみで描くが、**見出しの並び替えと「編集」だけ持たせない**
  —— プロジェクトではないので動かす先も編集する中身も無いため。
  **トップでは該当が 0 件でもグループを出す**(`withUnlinkedGroup` の第 3 引数 `keepEmpty`)
  —— 放り込み先・ドラッグで紐づけを外す先になるため。完了一覧(`/done`)は
  「置き場」の意味が無いので従来どおり 0 件なら出さない。`ProjectGroups` はこれを `pinnedGroups` として
  並び替え対象(`sortableGroups`)の後ろに固定で連結する
- トップにはタスクが 0 件のプロジェクトも出す(そこへ放り込む導線になるため)。
  例外はアーカイブ済みで、こちらは「まだ残っているタスクの置き場」としてだけ出し、片付いたら一覧から消える
- 並び替えは **行のどこでも長押し(400ms)してからドラッグ**(`frontend/src/lib/dragSort.ts`。
  Pointer Events でマウス・タッチ両対応)。☰ の掴み代は置かない —— 画面が狭く、カード幅を削りたくないため。
  トップではグループ見出しでプロジェクトの並び、カードでそのグループ内のタスクの並びが変わる。
  掴み代が無い代わりに、**以下の手当てが全部必要**(どれか外すと操作が壊れる):
  - 長押しの成立前に指が 8px 以上動いたら中止する。そうしないと普通のスクロールがドラッグに化ける
  - 成立後は `touchmove` を **非パッシブで `preventDefault()`**(`touch-action` はジェスチャ開始時に
    確定してしまい、後から効かせられない)。要素側の `touch-action: none` では代用できない
  - 指を離した直後の `click`(リンク遷移・グループの開閉)を 1 回だけ握りつぶす
    (行のルートに `@click.capture="sorter.clickGuard"`)
  - **`pointermove` / `pointerup` は行ではなく `window` で受ける**(押している間だけ登録)。
    行に張ると、並び替えで Vue が行を動かした瞬間に壊れる —— `insertBefore` は
    内部でノードを一度取り外すので、そこで**ポインタキャプチャが暗黙に解放される**。
    以降イベントは指の下の要素へ飛び、行のハンドラには二度と来ない
    (症状は「一度並び替えたあと、指から離れた瞬間に追従が止まる」)。
    同じ理由で `setPointerCapture` は使わない —— 解放される前提には立てないため
  - カード内のボタン・リンクは `data-no-drag` で除外する。特に **✳ は長押しを奪ってはいけない**
    (iOS で「長押し → Safari で開く」を一度やってもらう必要がある。§ハンドオフ参照)。
    合わせて `.card` には `-webkit-touch-callout: none`、`[data-no-drag]` には `default` を当てて戻す
  - ドラッグ中は「下に引っ張って更新」と食い合うので、App.vue が `isDragActive()` を見て見送る
  掴んだ行は `translateY` で**指に追従**する(`follow()`)。ここで効いてくるのが、
  並び替えで行のレイアウト位置自体が動くこと —— なので基準は常に
  「レイアウト上の位置 = 現在の rect から今当てている translateY を引いた値」で計算する。
  並び替えた直後は DOM がまだ入れ替わっていないので `nextTick` でもう一度当て直す。
  同じ理由で、挿入先を決める中心線の判定でも掴んでいる行だけは translateY を引いて測る
  (引かないと、指に貼り付いた行が自分自身の判定を乱して並びが決まらなくなる)
  トップは全プロジェクトを出すとは限らない(タスクの無いアーカイブ済みは出ない)ため、
  プロジェクトの並び替えは `projects.reorderVisible()` で「出ている分の枠だけ詰め替え」て全件の順に埋め戻してから送る
  (`PUT /api/projects/order` は全件の id を要求するため)。タスク側の `PUT /api/tasks/order` は逆に部分集合でよく、
  同じプロジェクト(未分類なら `projectId=null`)のタスクだけを混ぜずに送る
- **カードを別のプロジェクトのグループまで運ぶと、そのプロジェクトへ移る**
  (`useDragSort` の `{ dropZones: true }` + `lib/taskMove.ts`)。仕組み:
  - グループの `<section>` に `data-drop-zone="{key}"` を振り、ドラッグ中は
    `document.elementFromPoint` で指の下のゾーンを拾う。掴んだ行には `pointer-events: none` を当てる
    —— そうしないと指の下にあるのが「運んでいる行」自身になり、移動先を拾えない
  - 元と違うゾーンの上に居る間は**元のリストの並び替えを止める**(どのみち離せば移動になるので、
    残りのカードが動いて見えるのは邪魔なだけ)。受け皿のグループには `outline` で枠を出す。
    `border` だとその幅ぶんレイアウトがずれて、追従中のカードが飛ぶ
  - 保存は「`PATCH` で `projectId` を付け替え → 移動先グループを `PUT /api/tasks/order` で並べ直す」の 2 段。
    落とした Y 座標から移動先での挿入位置を割り出すので、**離した位置にそのまま入る**
  - **逆(プロジェクト → 未分類)も同じようにできる**。PATCH に `projectId: 0` を送って紐づけを外し、
    並べ直しは `projectId: null` で送る(§規約の PATCH の項)。トップでは未分類グループを
    0 件でも描くので、落とす先は常にある
- **タスクの状態は未完了(`todo`)と完了(`done`)の 2 つだけ**。着手中(`in_progress`)は
  2026-07 に廃止した。wire 値の `todo` は「未着手」ではなく「未完了」の意味(既存 DB との
  互換のため名前は据え置き)。既存 DB に着手中の行は無かったのでデータ移行は書いていない。
  既存 DB の CHECK 制約も `in_progress` を許したままだが、アプリがもう書かないので実害はない
  (SQLite で CHECK を変えるにはテーブル再作成が要るため、あえて触らない)
- **タスクの一覧・詳細・編集の専用画面はすべて廃止**。未完了はトップ、完了は `/done`、
  編集はカードを押してモーダル。ヘッダは「トップ / ルール」だけになった。
  `/done` はトップの未完了一覧と**同じ見せ方**にするため `ProjectGroups` を共有する
  (`:sortable="false"` `:project-editable="false"`。完了分を並べ替える意味も、
  そこからプロジェクトを編集する意味も無いため)。完了タスクを持つプロジェクトだけ出す
  —— トップと違って「放り込み先」の意味が無いので、0 件のプロジェクトは出さない。
  完了分はストアに持たず `/done` のローカル state として `GET /api/tasks?status=done` で
  開くたびに取り直す(**ページングしていないので、完了が数百件まで増えたら重くなる**)。
- **タスクの詳細画面と編集画面は廃止**。編集は一覧の**カード本文を押してモーダル**で行う
  (`TaskFormModal.vue`)。カードに編集ボタンは置かない —— ボタンを増やすより、
  カードそのものを押せる方が指の移動が少ない。`/tasks/:id` と `/tasks/:id/edit` は
  ルートから消えており、古いブックマークはワイルドカードでトップに流れる。
  物理削除は誤タップを避けるため一覧には置かず、編集モーダルの「削除」からだけ行う。
  **完了/未完了の切り替えはカードのボタンだけ**が持つ(未完了一覧では「完了」、
  完了一覧では「未完了に戻す」)。編集モーダルに状態の選択は置かない —— 同じことを
  2 箇所でできると、どちらが正かが分からなくなるため。モーダルの保存は `status` を
  送らないので、完了タスクの本文を直しても完了のまま残る。
  カード内は「メモ」と「右カラム(上: コピー/✳、下: 完了ボタン)」の 2 列。
  右カラムを `align-items: stretch` で伸ばすことで、メモが複数行でも
  **ボタンの下端がメモの下端に揃う**。
  完了タスクはトップ左下のリンクから `/done` で確認する。
  編集モーダルの「プロジェクト」で **「プロジェクトなし」を選べば紐づけを外せる**
  (保存時に `projectId: 0` を送る。`undefined` だと「変更しない」になって外れない)
- コピーは複製に見えないようクリップボードアイコンでカード右上(編集・完了ボタンの上)に置く。✳ ハンドオフはその隣
- **プロジェクト専用画面は持たない**(廃止済み)。作成・編集・アーカイブ・並び替えはすべてトップで完結する:
  新規は「未完了」見出しの右端の「＋ プロジェクト」、編集は各グループ見出しの右端の「編集」、
  どちらも `ProjectFormModal.vue`(`v-if` で出し入れする前提なのでフォームの初期値は setup で一度だけ組み立てる)。
  `/projects` はルートから消したので、古いブックマークはワイルドカードでトップに流れる
- アーカイブはモーダルの**見出しと同じ行の右端**のボタン(タスク編集モーダルの「削除」と同じ置き方)。
  アーカイブ済みなら「アーカイブから戻す」に変わる。押すと**入力中の内容ごと**保存して閉じる(編集を捨てさせない)。
  **アーカイブできるのは未完了が 0 件のときだけ**(戻すのは無条件)。片付いていないタスクごと
  トップから消えると放り込んだものを取りこぼすため。UI はボタンを disabled にし、
  `ProjectService.update` でも弾く(REST を直接叩いても通らない)
- **プロジェクトの削除はアーカイブ済みのときだけ**(`DELETE /api/projects/{id}`)。
  アーカイブ自体が「未完了 0 件」を条件にしているので、片付いたことを確かめる一段を
  必ず通ってからでないと消えない。紐づくタスクは `TaskRepository.deleteByProjectId` で
  同一トランザクションにまとめて消す(**完了済みも巻き添え**)。
  ボタンはモーダル見出し行の一番右(「アーカイブから戻す」の右)。
  確認ダイアログに件数は出さない —— ストアには未完了しか無く、アーカイブ済みは必ず 0 件なので、
  「0 件」と言いながら完了済みを消すことになる
- トップにはアーカイブ済みを出さない。代わりに一覧の一番下**右**の「アーカイブしたプロジェクト →」から
  `/archived`(`ArchivedProjectsView`)へ(左は「完了したタスク →」)。**この導線が唯一のアーカイブを戻す口なので消してはいけない**。
  見え方はトップと揃える必要があるので、グループの描画は `ProjectGroups.vue` に寄せて共有している
  (アーカイブ一覧は並べ替える意味が無いので `:sortable="false"`)。グループ組み立ては `lib/groups.ts`
- `GET /api/projects` の `archived` パラメータに **`defaultValue` を使ってはいけない**。
  Spring は「パラメータが空文字」のときも defaultValue で置き換えるため、
  `?archived=`(全件のつもり)が `false` に化けてアーカイブ済みが一件も返らなくなる。
  未指定(null)と空文字は自前で分ける
- **タスクが持つのは title だけ**。`context` / `acceptance_criteria` / `out_of_scope` は廃止した
  —— 出先で放り込む使い方では埋まらなかったため。これに伴い、トップの
  「受け入れ条件などを詳しく書く →」リンクも撤去した(編集モーダルに残るのは
  タスク内容・プロジェクトのみ)。
  一度も中身が書かれないまま廃止したので、既存 DB の 3 列も `SchemaMigrations` が
  `DROP COLUMN` で落とす(SQLite 3.35+ が必要。同梱の sqlite-jdbc は 3.53)
- ✳ アイコン(コピーの隣)は Claude Code へのハンドオフ。タスク内容をプリフィルした
  `https://claude.ai/code?prompt=…&repositories=…` を新規タブで開く**直リンク**。
  スマホは初期状態ではユニバーサルリンクで Claude アプリが開き、アプリはクエリを
  引き継がないためプリフィルが失われる。**空タブ + JS 遷移、中継ページ `/handoff` からの
  JS 遷移のどちらでもユニバーサルリンクの発火は回避できず撤去済み**(再挑戦するとき用のメモ)。
  効く回避は「リンク長押し → Safari で開く、を一度やる」で、iOS が以後ブラウザで開くのを覚える。
  さらに iOS の PWA(スタンドアロン)では外部リンクがアプリ内ブラウザで開いて claude.ai の
  ログインセッションを共有しないため、`navigator.standalone` のときだけ href を
  `x-safari-https://…`(非公式スキーム、iOS 17+)にして Safari 本体で開かせている。
  効かなくなったら素の URL に戻す。URL 組み立ては `frontend/src/lib/claudeCode.ts`。
  `repositories` はプロジェクトのリポジトリ URL のうち GitHub のみ `owner/repo` スラッグに
  変換して付与し(規約リポジトリが設定されていればそれも常に足す。§ルール機能)、
  プロンプトは約 4,500 文字で切り詰める(Web 版のプリフィル上限 5,000 文字対策)。
  プロンプトの中身は**タスク内容そのもの**で、タスク番号は付けない —— MCP 廃止後は
  Claude Code 側から cc-tasks を参照できず、番号を渡しても意味が無いため。
  かわりに規約リポジトリが設定されていれば「まず規約リポジトリの CLAUDE.md
  (共通ルール)に従う」の一言を先頭に添える(セッションに含めるだけでは
  他リポジトリの説明と誤読されうるので、従う対象だと明示する)
- 下に引っ張って更新: PWA にはリロード手段が無いため App.vue がタッチジェスチャを検知し、
  ビューが `usePullToRefresh`(`frontend/src/lib/pullToRefresh.ts`)で登録した再読込処理を呼ぶ。
  未登録の画面はページ全体のリロードにフォールバック
- 開発時は `spring-boot-devtools` で自動再起動(bootJar には入らず本番では無効)。反復は `./dev.sh`(backend dev + 継続コンパイル + Vite HMR)で回す。開くのは :7001、dev は認証なし
- セッションはディスク永続化(`server.servlet.session.persistent`、store-dir は `SESSION_DIR`=`/data/sessions`)。再起動・再デプロイでも再ログイン不要。timeout は 30d
- 管理系エンドポイント(Actuator 等)は追加しない
- **コードを変更したら、その内容に合わせて `CLAUDE.md`・`README.md`・`docs/詳細設計.md` も同じコミットで更新する**。
  特にポート番号・環境変数・コマンド・ディレクトリ構成・CI/CD ワークフロー・API 仕様など、
  ドキュメントに書かれている事実が変わったときは必ず追従させる(記述と実装を食い違わせない)。
  該当する記述が無ければ更新は不要。迷ったら各ファイルを grep して古い記述が残っていないか確認する
- **README は人に読ませる入口として簡潔に保つ**。設計の詳細・API 仕様は `docs/詳細設計.md` に置き、
  README からはリンクで参照する。CLAUDE.md へのリンクは README に張らない(人に読ませるものではないため)

## ルール機能

「全 Claude Code 環境に効かせたい決まりごと」を Markdown で複数持ち、**表示順に連結して
1 本の Markdown として取り出す**ための機能(`dev.cctasks.rule`, `RulesView.vue`)。

- 連結は **サーバー側**(`RuleService.combined()`)で行う。貼り付ける本文と
  `GET /api/rules/combined` が返す本文を常に一致させるため
- 各ルールは `## <title>` の見出しを付けて連ねる。貼り付け先でルールの境目が読めるように
- **連結の先頭には前置き**(`# 共通ルール` + 適用範囲の一文。`RuleService.COMBINED_PREAMBLE`)を
  **自動で付ける**。CLAUDE.md の中身は普通「そのリポジトリ自身の説明」として読まれるため、
  貼り先がどこであれ「作業対象のすべてのリポジトリに効く」と明示する。
  ルールとして登録させないのは、貼り替えのたびに消えたり並び替えで先頭から動いたりしないため。
  有効なルールが 0 件なら前置きも付けず空文字のまま
- **前置きの直後には「規約リポジトリの扱い」ルール**(`RuleService.COMBINED_REPO_RULE`)も
  **自動で付ける**。規約リポジトリは各ユーザーが自分用に作る配布専用のプライベートリポジトリで、
  育てる対象ではない。セッションにサブリポジトリとして含まれるぶん、放っておくとタスクの
  ついでに CLAUDE.md を書き換えられかねないので、「読み取り専用・自動更新禁止。更新は
  ユーザーが更新後の Markdown を明示的に渡したときだけ、その内容で丸ごと置き換える」と
  Claude Code に伝える。URL からの取得は書かない —— `/api` は要ログインで、
  セッションから `GET /api/rules/combined` を叩けないため。自動付与にする理由は前置きと同じ
- `enabled=false` のルールは連結に含めない。消さずに一時的に外せる
- **並び順がそのまま連結順**。並び替えはプロジェクト/タスクと同じ長押しドラッグ
- モーダルは Markdown をレンダリングせず `<pre>` で素のまま出す。**貼り付けるのは
  Markdown そのものだから**。整形して見せると何をコピーしているのか分からなくなる。
  本文の上に貼り先の説明(規約リポジトリの CLAUDE.md に丸ごと貼り替えるか、
  CLI 版なら `~/.claude/rules/cc-tasks.md`)を添えて、コピー後に迷わないようにする
- **規約リポジトリ**をルール画面の一覧の下で設定できる(`GET/PATCH /api/rules/settings`、
  値は GitHub URL か `owner/repo` スラッグ。PATCH は規約どおり null=変更しない・空文字=消す)。
  設定すると ✳ ハンドオフの `repositories` に**常に**付与される(`claudeCodeUrl` の第 3 引数。
  `repoSlug()` で正規化し、プロジェクトのリポジトリと重複すれば足さない)——
  Web 版のセッションに含まれたリポジトリは**ルート直下の CLAUDE.md が読み込まれる**ので、
  連結ルールをそのリポジトリの CLAUDE.md に置いておけば ✳ 経由の全セッションに共通ルールが効く
  (プライマリでないリポジトリの `.claude/rules/` は読まれる保証が無いのでルート直下に置く)。
  保存先は汎用 KV の `settings` テーブル(行が無い = 未設定)。Spring Data JDBC は
  文字列主キーの upsert と相性が悪い(save が常に UPDATE 扱い)ため、
  ここだけ `SettingRepository` が JdbcTemplate で直接書く。フロントは
  `stores/rules.ts` の `loadSettings()`(✳ を出すカードごとに呼ばれるので
  in-flight を共有して 1 リクエストにまとめる)

配信は**手でコピーする**。CLI 版なら `~/.claude/rules/cc-tasks.md`(マシン上の全リポジトリに効く)、
Web 版はユーザーレベル設定がクラウドセッションに引き継がれないため、
**規約リポジトリ(上記)の CLAUDE.md に連結ルールを貼って ✳ 経由でセッションに含める**か、
リポジトリごとに `.claude/rules/cc-tasks.md` を置く。自動配信(フックや `GET /api/rules.md` の
API キー認証エンドポイント)は**まだ作っていない**。必要になってから足す。

ルールは指示であって強制ではない —— CLAUDE.md と同じくシステムプロンプトの後の
ユーザーメッセージとして届くので、遵守は保証されない。絶対に止めたい操作は
Claude Code のフック側の仕事。

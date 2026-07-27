-- 起動のたびに流すため IF NOT EXISTS を付けている。

CREATE TABLE IF NOT EXISTS projects (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT    NOT NULL UNIQUE,          -- 表示名 (例: "sample-project")
    repo_url    TEXT,                             -- リポジトリ URL (複数可・改行区切り。Web版CC連携で使用)
    description TEXT,
    archived    INTEGER NOT NULL DEFAULT 0,       -- 0/1
    sort_order  INTEGER NOT NULL DEFAULT 0,       -- 手動並び替えの表示順 (昇順)。既存 DB へは SchemaMigrations が ALTER で追加
    created_at  TEXT    NOT NULL,                 -- ISO 8601 (UTC)
    updated_at  TEXT    NOT NULL
);

CREATE TABLE IF NOT EXISTS tasks (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    -- プロジェクト紐づけは任意。出先でメモだけ放り込み、後から紐づけられる
    project_id          INTEGER REFERENCES projects(id),
    -- 持つのは「何をやりたいか」だけ。背景・受け入れ条件・スコープ外の列は廃止した
    -- (既存 DB からは SchemaMigrations が DROP COLUMN で落とす)
    title               TEXT    NOT NULL,
    -- 未完了(todo)と完了(done)の 2 つだけ
    status              TEXT    NOT NULL DEFAULT 'todo'
                        CHECK (status IN ('todo', 'done')),
    -- プロジェクト内の手動並び替えの表示順 (昇順)。0 = 未並び替え(新規タスク)で
    -- グループの先頭に積まれる。既存 DB へは SchemaMigrations が ALTER で追加
    sort_order          INTEGER NOT NULL DEFAULT 0,
    created_at          TEXT    NOT NULL,
    updated_at          TEXT    NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_tasks_project_status ON tasks(project_id, status);

-- 全 Claude Code 環境に適用したい共通ルール。Markdown 本文を複数持ち、
-- 表示順(sort_order 昇順)に連結して 1 本のルール集として取り出す。
CREATE TABLE IF NOT EXISTS rules (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    title      TEXT    NOT NULL,                 -- 見出し。連結時に "## <title>" になる
    body       TEXT    NOT NULL,                 -- 本文 (Markdown)
    enabled    INTEGER NOT NULL DEFAULT 1,       -- 0/1。外したルールは連結に含めない
    sort_order INTEGER NOT NULL DEFAULT 0,       -- 手動並び替えの表示順 (昇順)
    created_at TEXT    NOT NULL,
    updated_at TEXT    NOT NULL
);

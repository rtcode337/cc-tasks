-- 仕様書 §5.1 の DDL。起動のたびに流すため IF NOT EXISTS を付けている。
-- notes は tasks 削除時にカスケード削除する(サービス層でも明示削除しているが、
-- DB 側にも制約として持たせて取りこぼしを防ぐ)。

CREATE TABLE IF NOT EXISTS projects (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT    NOT NULL UNIQUE,          -- 表示名 (例: "sample-project")
    repo_url    TEXT,                             -- GitHub URL (Web版CC連携で使用)
    description TEXT,
    archived    INTEGER NOT NULL DEFAULT 0,       -- 0/1
    created_at  TEXT    NOT NULL,                 -- ISO 8601 (UTC)
    updated_at  TEXT    NOT NULL
);

CREATE TABLE IF NOT EXISTS tasks (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    -- プロジェクト紐づけは任意。出先でメモだけ放り込み、後から紐づけられる
    project_id          INTEGER REFERENCES projects(id),
    title               TEXT    NOT NULL,
    context             TEXT,                     -- 背景・現状・動機
    acceptance_criteria TEXT,                     -- 受け入れ条件
    out_of_scope        TEXT,                     -- やらないこと
    status              TEXT    NOT NULL DEFAULT 'todo'
                        CHECK (status IN ('todo', 'in_progress', 'done')),
    created_at          TEXT    NOT NULL,
    updated_at          TEXT    NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_tasks_project_status ON tasks(project_id, status);

CREATE TABLE IF NOT EXISTS notes (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    task_id    INTEGER NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    author     TEXT    NOT NULL CHECK (author IN ('human', 'claude_code')),
    body       TEXT    NOT NULL,
    created_at TEXT    NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_notes_task ON notes(task_id);

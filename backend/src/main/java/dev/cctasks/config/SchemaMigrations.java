package dev.cctasks.config;

import java.util.List;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 起動時の軽量マイグレーション。
 *
 * マイグレーションツールは入れていないため、schema.sql の CREATE TABLE は
 * 新規 DB にしか効かない。既存 DB への列追加・削除はここで冪等な ALTER として当てる。
 * schema.sql の列を足す/消すときは、必ずここにも対応する ALTER を足すこと。
 */
@Component
public class SchemaMigrations {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigrations.class);

    private final JdbcTemplate jdbc;

    public SchemaMigrations(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    void migrate() {
        // projects.sort_order (2026-07): プロジェクトの手動並び替え
        if (!columnExists("projects", "sort_order")) {
            jdbc.execute("ALTER TABLE projects ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0");
            // 既存行の初期値は従来の表示順(名前順)に合わせる
            jdbc.execute("""
                    UPDATE projects SET sort_order =
                        (SELECT COUNT(*) FROM projects p2 WHERE p2.name <= projects.name)""");
            log.info("projects.sort_order 列を追加しました(初期値は名前順)");
        }

        // tasks.sort_order (2026-07): プロジェクト内のタスク手動並び替え
        if (!columnExists("tasks", "sort_order")) {
            jdbc.execute("ALTER TABLE tasks ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0");
            // 既存行は 0(未並び替え)のままでよい。0 どうしは従来どおり作成日時降順に並ぶ
            log.info("tasks.sort_order 列を追加しました(既存行は未並び替え=0)");
        }

        // tasks.context / acceptance_criteria / out_of_scope (2026-07 廃止):
        // 一度も中身が書かれないまま廃止したので、列ごと落として構わない。
        // DROP COLUMN は SQLite 3.35+ (同梱の sqlite-jdbc は 3.53) で使える。
        // どの列も索引に使っていないので落とせる (idx_tasks_project_status は project_id/status)。
        for (String column : List.of("context", "acceptance_criteria", "out_of_scope")) {
            if (columnExists("tasks", column)) {
                jdbc.execute("ALTER TABLE tasks DROP COLUMN " + column);
                log.info("廃止した tasks.{} 列を削除しました", column);
            }
        }

        // notes (2026-07 廃止): タスクの経緯タイムラインごと廃止したのでテーブルを落とす。
        // MCP の add_note が唯一の主要な書き込み口だったが、その MCP も廃止した。
        if (tableExists("notes")) {
            jdbc.execute("DROP TABLE notes");
            log.info("廃止した notes テーブルを削除しました");
        }

        // tasks.status の CHECK (2026-07): 一度廃止した着手中 (in_progress) を復活した。
        // 廃止中に作られた DB は CHECK が in_progress を許さず書き込みで落ちるため、
        // テーブルを作り直して差し替える (SQLite は CHECK だけの変更ができない)。
        // 廃止前に作られた DB は CHECK が in_progress を許したままなので対象外。
        String tasksDdl = jdbc.queryForObject(
                "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'tasks'", String.class);
        if (tasksDdl != null && !tasksDdl.contains("in_progress")) {
            jdbc.execute("ALTER TABLE tasks RENAME TO tasks_old");
            // DDL は schema.sql の tasks と同じに保つこと
            jdbc.execute("""
                    CREATE TABLE tasks (
                        id                  INTEGER PRIMARY KEY AUTOINCREMENT,
                        project_id          INTEGER REFERENCES projects(id),
                        title               TEXT    NOT NULL,
                        status              TEXT    NOT NULL DEFAULT 'todo'
                                            CHECK (status IN ('todo', 'in_progress', 'done')),
                        sort_order          INTEGER NOT NULL DEFAULT 0,
                        created_at          TEXT    NOT NULL,
                        updated_at          TEXT    NOT NULL
                    )""");
            jdbc.execute("""
                    INSERT INTO tasks (id, project_id, title, status, sort_order, created_at, updated_at)
                    SELECT id, project_id, title, status, sort_order, created_at, updated_at FROM tasks_old""");
            jdbc.execute("DROP TABLE tasks_old");
            // 索引は旧テーブルに付いたまま消えるので張り直す
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_tasks_project_status ON tasks(project_id, status)");
            log.info("tasks テーブルを作り直し、status の CHECK に in_progress を戻しました");
        }
    }

    private boolean tableExists(String table) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = ?",
                Integer.class, table);
        return count != null && count > 0;
    }

    private boolean columnExists(String table, String column) {
        return jdbc.queryForList(
                "SELECT name FROM pragma_table_info(?)", String.class, table)
                .stream()
                .anyMatch(column::equals);
    }
}

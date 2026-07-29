package dev.cctasks.config;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 着手中 (in_progress) を許さない CHECK の時期に作られた DB を、
 * SchemaMigrations がテーブルを作り直して救えることを確認する。
 * Spring コンテキストは要らないので、素の SQLite ファイルに直接当てる。
 */
class SchemaMigrationsTests {

    @Test
    void 着手中を許さないCHECKのDBはテーブルが作り直される() {
        Path db = Path.of(System.getProperty("java.io.tmpdir"),
                "cctasks-migration-test-" + System.nanoTime() + ".db");
        db.toFile().deleteOnExit();
        var dataSource = new SingleConnectionDataSource("jdbc:sqlite:" + db, true);
        try {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);

            // 着手中の廃止中に schema.sql が作っていた形を再現する
            jdbc.execute("""
                    CREATE TABLE projects (
                        id          INTEGER PRIMARY KEY AUTOINCREMENT,
                        name        TEXT    NOT NULL UNIQUE,
                        repo_url    TEXT,
                        description TEXT,
                        archived    INTEGER NOT NULL DEFAULT 0,
                        sort_order  INTEGER NOT NULL DEFAULT 0,
                        created_at  TEXT    NOT NULL,
                        updated_at  TEXT    NOT NULL
                    )""");
            jdbc.execute("""
                    CREATE TABLE tasks (
                        id                  INTEGER PRIMARY KEY AUTOINCREMENT,
                        project_id          INTEGER REFERENCES projects(id),
                        title               TEXT    NOT NULL,
                        status              TEXT    NOT NULL DEFAULT 'todo'
                                            CHECK (status IN ('todo', 'done')),
                        sort_order          INTEGER NOT NULL DEFAULT 0,
                        created_at          TEXT    NOT NULL,
                        updated_at          TEXT    NOT NULL
                    )""");
            jdbc.execute("CREATE INDEX idx_tasks_project_status ON tasks(project_id, status)");
            jdbc.update("""
                    INSERT INTO tasks (title, status, sort_order, created_at, updated_at)
                    VALUES ('残っているメモ', 'todo', 3, '2026-07-01T00:00:00.000Z', '2026-07-01T00:00:00.000Z')""");

            new SchemaMigrations(jdbc).migrate();

            // 既存の行は id ごと残り、in_progress が書けるようになる
            assertThat(jdbc.queryForObject("SELECT title FROM tasks WHERE id = 1", String.class))
                    .isEqualTo("残っているメモ");
            assertThat(jdbc.queryForObject("SELECT sort_order FROM tasks WHERE id = 1", Integer.class))
                    .isEqualTo(3);
            jdbc.update("UPDATE tasks SET status = 'in_progress' WHERE id = 1");

            // 索引も張り直されている
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM sqlite_master
                    WHERE type = 'index' AND name = 'idx_tasks_project_status'""", Integer.class))
                    .isEqualTo(1);

            // 2 回目は何もしない(冪等)
            new SchemaMigrations(jdbc).migrate();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM tasks", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT status FROM tasks WHERE id = 1", String.class))
                    .isEqualTo("in_progress");
        } finally {
            dataSource.destroy();
        }
    }
}

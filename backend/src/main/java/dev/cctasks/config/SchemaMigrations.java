package dev.cctasks.config;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 起動時の軽量マイグレーション。
 *
 * マイグレーションツールは入れていないため、schema.sql の CREATE TABLE は
 * 新規 DB にしか効かない。既存 DB への列追加はここで冪等な ALTER として当てる。
 * schema.sql に列を足すときは、必ずここにも対応する ALTER を足すこと。
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
    }

    private boolean columnExists(String table, String column) {
        return jdbc.queryForList(
                "SELECT name FROM pragma_table_info(?)", String.class, table)
                .stream()
                .anyMatch(column::equals);
    }
}

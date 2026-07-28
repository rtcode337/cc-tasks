package dev.cctasks.setting;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * アプリ全体のキーバリュー設定。行が無い = 未設定。
 * 現状はルール機能の規約リポジトリ({@code rules_repo_url})だけが使う。
 *
 * <p>Spring Data JDBC は文字列を主キーにすると save が常に UPDATE 扱いになり
 * upsert と相性が悪いため、ここだけ JdbcTemplate で直接書く。
 */
@Repository
public class SettingRepository {

    /** TEXT 列の日時書式。JdbcConfig のコンバータと同じ ISO 8601 (UTC, ミリ秒精度)に揃える */
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private final JdbcTemplate jdbc;

    public SettingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> find(String key) {
        return jdbc.queryForList("SELECT value FROM settings WHERE key = ?", String.class, key)
                .stream().findFirst();
    }

    public void upsert(String key, String value, Instant now) {
        jdbc.update("""
                INSERT INTO settings (key, value, updated_at) VALUES (?, ?, ?)
                ON CONFLICT (key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at""",
                key, value, TIMESTAMP_FORMAT.format(now));
    }

    public void delete(String key) {
        jdbc.update("DELETE FROM settings WHERE key = ?", key);
    }
}

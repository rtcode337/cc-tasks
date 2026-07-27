package dev.cctasks.rule;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * すべての Claude Code 環境に効かせたい共通ルールの 1 本。
 *
 * <p>本文は Markdown。複数のルールを表示順に連結して 1 本のルール集にし、
 * それを各環境の指示ファイル(`~/.claude/rules/` など)に貼って使う。
 */
@Table("rules")
public record Rule(
        @Id Long id,
        /** 見出し。連結時に {@code ## <title>} になる。 */
        String title,
        String body,
        /** false のルールは連結に含めない(消さずに一時的に外せる)。 */
        boolean enabled,
        @Column("sort_order") int sortOrder,
        Instant createdAt,
        Instant updatedAt) {
}

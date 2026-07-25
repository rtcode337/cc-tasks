package dev.cctasks.task;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Claude Code に依頼したいタスク。編集可能なフィールドの集合。
 * 経緯・進捗は notes 側に追記オンリーで蓄積する。
 */
@Table("tasks")
public record Task(
        @Id Long id,
        Long projectId,
        String title,
        String context,
        String acceptanceCriteria,
        String outOfScope,
        TaskStatus status,
        Instant createdAt,
        Instant updatedAt) {
}

package dev.cctasks.note;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * タスクのタイムライン。追記オンリー(更新・削除 API は持たない)。
 */
@Table("notes")
public record Note(
        @Id Long id,
        Long taskId,
        NoteAuthor author,
        String body,
        Instant createdAt) {
}

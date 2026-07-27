package dev.cctasks.task;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Claude Code に依頼したいタスク。
 *
 * <p>持つのは「何をやりたいか」({@code title})だけ。コンテキスト・受け入れ条件・
 * スコープ外・経緯(notes)はいずれも廃止した —— 出先で放り込む用途では埋まらず、
 * 書きたいことは title に収まるため。
 */
@Table("tasks")
public record Task(
        @Id Long id,
        Long projectId,
        String title,
        TaskStatus status,
        /**
         * プロジェクト内の手動並び順(昇順)。並び替えで 1, 2, 3, … を振る。
         * 0 は「並び替えていない」で、新規タスクはここに入る
         * (0 < 1 なのでグループの先頭、同値どうしは作成日時降順で新しい順に積まれる)。
         */
        @Column("sort_order") int sortOrder,
        Instant createdAt,
        Instant updatedAt) {
}

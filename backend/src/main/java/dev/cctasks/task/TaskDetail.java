package dev.cctasks.task;

import java.util.List;

import dev.cctasks.note.Note;
import dev.cctasks.project.Project;

/**
 * タスク本体 + 所属プロジェクト + ノートのタイムライン(新しい順)。
 */
public record TaskDetail(Task task, Project project, List<Note> notes) {
}

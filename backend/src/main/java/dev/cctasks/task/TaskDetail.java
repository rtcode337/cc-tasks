package dev.cctasks.task;

import dev.cctasks.project.Project;

/**
 * タスク本体 + 所属プロジェクト(未紐づけなら null)。
 */
public record TaskDetail(Task task, Project project) {
}

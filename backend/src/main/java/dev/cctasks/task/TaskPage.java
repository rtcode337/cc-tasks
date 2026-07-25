package dev.cctasks.task;

import java.util.List;

/**
 * 完了タスク一覧のページング結果。
 */
public record TaskPage(List<Task> items, long total, int page, int size) {
}

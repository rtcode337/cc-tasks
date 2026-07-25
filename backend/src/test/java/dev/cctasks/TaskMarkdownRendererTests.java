package dev.cctasks;

import java.time.Instant;
import java.util.List;

import dev.cctasks.mcp.TaskMarkdownRenderer;
import dev.cctasks.note.Note;
import dev.cctasks.note.NoteAuthor;
import dev.cctasks.project.Project;
import dev.cctasks.task.Task;
import dev.cctasks.task.TaskDetail;
import dev.cctasks.task.TaskStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * get_task の返却は Claude Code がそのままプロンプトとして読む。
 * 見出し構成が崩れると使い勝手に直結するので固定しておく。
 */
class TaskMarkdownRendererTests {

    private final TaskMarkdownRenderer renderer = new TaskMarkdownRenderer();

    private static final Instant T1 = Instant.parse("2026-07-19T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-07-20T10:00:00Z");

    @Test
    void 仕様書の例と同じ構成で出力する() {
        String markdown = renderer.render(detail(
                new Task(12L, 1L, "タスクに優先度を付けられるようにする", "作成日時順だけだと重要なタスクが埋もれる",
                        "一覧で優先度順に並べ替えられる", "自動での優先度推定", TaskStatus.IN_PROGRESS, T1, T2),
                List.of(
                        new Note(2L, 12L, NoteAuthor.CLAUDE_CODE, "○○まで実装済み。残: △△", T2),
                        new Note(1L, 12L, NoteAuthor.HUMAN, "そもそもの発端は…", T1))));

        assertThat(markdown)
                .startsWith("# タスク #12: タスクに優先度を付けられるようにする")
                .contains("- プロジェクト: sample-project (https://github.com/example/sample-project)")
                .contains("- 状態: in_progress")
                .contains("## コンテキスト\n\n作成日時順だけだと重要なタスクが埋もれる")
                .contains("## 受け入れ条件\n\n一覧で優先度順に並べ替えられる")
                .contains("## スコープ外\n\n自動での優先度推定")
                .contains("## これまでの経緯(notes 新しい順)")
                .contains("- [2026-07-20 claude_code] ○○まで実装済み。残: △△")
                .contains("- [2026-07-19 human] そもそもの発端は…");

        // 新しい順であること
        assertThat(markdown.indexOf("2026-07-20")).isLessThan(markdown.indexOf("2026-07-19"));
    }

    @Test
    void 未記入の欄とノート無しが分かる形で出る() {
        String markdown = renderer.render(detail(
                new Task(1L, 1L, "タイトルだけ", null, null, null, TaskStatus.TODO, T1, T1),
                List.of()));

        assertThat(markdown)
                .contains("## コンテキスト\n\n(未記入)")
                .contains("## 受け入れ条件\n\n(未記入)")
                .contains("## スコープ外\n\n(未記入)")
                .contains("(まだノートはありません)");
    }

    @Test
    void 複数行ノートは箇条書きから外れないようインデントされる() {
        String markdown = renderer.render(detail(
                new Task(1L, 1L, "タスク", null, null, null, TaskStatus.TODO, T1, T1),
                List.of(new Note(1L, 1L, NoteAuthor.CLAUDE_CODE, "1 行目\n2 行目", T1))));

        assertThat(markdown).contains("- [2026-07-19 claude_code] 1 行目\n  2 行目");
    }

    private static TaskDetail detail(Task task, List<Note> notes) {
        Project project = new Project(1L, "sample-project", "https://github.com/example/sample-project",
                null, false, T1, T1);
        return new TaskDetail(task, project, notes);
    }
}

package dev.cctasks.mcp;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import dev.cctasks.note.Note;
import dev.cctasks.task.Task;
import dev.cctasks.task.TaskDetail;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * タスクを「そのまま Claude Code のプロンプトとして使える Markdown」に整形する
 * (仕様書 §7.2 get_task の返却例)。
 */
@Component
public class TaskMarkdownRenderer {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd")
            .withZone(ZoneOffset.UTC);

    public String render(TaskDetail detail) {
        Task task = detail.task();
        StringBuilder md = new StringBuilder();

        md.append("# タスク #").append(task.id()).append(": ").append(task.title()).append("\n\n");
        if (detail.project() != null) {
            md.append("- プロジェクト: ").append(detail.project().name());
            if (StringUtils.hasText(detail.project().repoUrl())) {
                md.append(" (").append(detail.project().repoUrl()).append(")");
            }
            md.append("\n");
        }
        else {
            md.append("- プロジェクト: (未紐づけ)\n");
        }
        md.append("- 状態: ").append(task.status().wireValue()).append("\n\n");

        section(md, "コンテキスト", task.context());
        section(md, "受け入れ条件", task.acceptanceCriteria());
        section(md, "スコープ外", task.outOfScope());

        md.append("## これまでの経緯(notes 新しい順)\n\n");
        if (detail.notes().isEmpty()) {
            md.append("(まだノートはありません)\n");
        }
        else {
            for (Note note : detail.notes()) {
                md.append("- [").append(DATE.format(note.createdAt())).append(" ")
                        .append(note.author().wireValue()).append("] ")
                        .append(indentContinuation(note.body())).append("\n");
            }
        }
        return md.toString();
    }

    private static void section(StringBuilder md, String heading, String body) {
        md.append("## ").append(heading).append("\n\n");
        md.append(StringUtils.hasText(body) ? body.strip() : "(未記入)").append("\n\n");
    }

    /** 複数行ノートが箇条書きから外れないよう 2 桁インデントで折り返す。 */
    private static String indentContinuation(String body) {
        return body.strip().replace("\n", "\n  ");
    }
}

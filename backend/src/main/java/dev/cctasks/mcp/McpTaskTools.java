package dev.cctasks.mcp;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import dev.cctasks.note.Note;
import dev.cctasks.note.NoteAuthor;
import dev.cctasks.task.Task;
import dev.cctasks.task.TaskDetail;
import dev.cctasks.task.TaskService;
import dev.cctasks.task.TaskStatus;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/**
 * Claude Code に公開する MCP ツール (仕様書 §7.2)。
 *
 * <p>description は Claude Code が自律的に使い方を判断する材料になるため、
 * 「いつ呼ぶか」まで含めて日本語で丁寧に書く。
 */
@Service
public class McpTaskTools {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    private final TaskService taskService;
    private final TaskMarkdownRenderer renderer;

    public McpTaskTools(TaskService taskService, TaskMarkdownRenderer renderer) {
        this.taskService = taskService;
        this.renderer = renderer;
    }

    /** list_tasks の 1 行分。 */
    public record TaskSummary(long id, String title, String status, String updatedAt) {

        static TaskSummary from(Task task) {
            return new TaskSummary(task.id(), task.title(), task.status().wireValue(),
                    TIMESTAMP.format(task.updatedAt()) + " UTC");
        }
    }

    /** add_note の返却。 */
    public record AddedNote(long id, String createdAt) {
    }

    @Tool(name = "list_tasks", description = """
            指定したプロジェクトに溜まっているタスクの一覧を取得する。
            作業を始める前にこれを呼んで、何が残っているかを把握すること。
            status を省略すると done 以外(todo と in_progress)を返すので、
            「次に何をやるか」を探す用途ではまず省略して呼べばよい。
            返るのは id / title / status / 最終更新時刻の一覧で、詳細は含まない。
            中身を読むには get_task を続けて呼ぶこと。
            """)
    public List<TaskSummary> listTasks(
            @ToolParam(description = """
                    プロジェクト名(リポジトリ名)。例: "sample-project"、"another-project"。
                    アプリに登録されている表示名と完全一致させること。
                    """)
            String project,
            @ToolParam(required = false, description = """
                    絞り込む状態。todo(未着手) / in_progress(着手済み) / done(完了) のいずれか。
                    省略時は done 以外を返す。
                    """)
            String status) {

        TaskStatus parsed = (status == null || status.isBlank()) ? null : TaskStatus.from(status);
        return taskService.listByProjectName(project, parsed).stream()
                .map(TaskSummary::from)
                .toList();
    }

    @Tool(name = "get_task", resultConverter = RawTextResultConverter.class, description = """
            タスクの詳細を、そのまま作業の指示として読める Markdown で取得する。
            コンテキスト(背景・動機)、受け入れ条件、スコープ外、および
            これまでの経緯(過去セッションの引き継ぎメモを含む notes、新しい順)が含まれる。
            着手するタスクを決めたら、実装を始める前に必ずこれを読むこと。
            特に「これまでの経緯」には前回どこまで進んだかが書かれているので、
            同じ作業をやり直さないためにも先に確認すること。
            """)
    public String getTask(
            @ToolParam(description = "タスク ID。list_tasks が返す id をそのまま渡す。")
            int taskId) {

        TaskDetail detail = taskService.detail(taskId);
        return renderer.render(detail);
    }

    @Tool(name = "update_task_status", description = """
            タスクの状態を変更する。
            実際に着手したタイミングで in_progress に、作業を完了したタイミングで done にすること。
            人間はこの状態を見て「今どれが動いているか」を判断するので、
            着手・完了の申告はこまめに行うこと。
            状態遷移に制約は無いため、中止して未着手に戻す場合は todo を指定してよい。
            """)
    public TaskSummary updateTaskStatus(
            @ToolParam(description = "タスク ID。")
            int taskId,
            @ToolParam(description = "新しい状態。todo / in_progress / done のいずれか。")
            String status) {

        Task updated = taskService.updateStatus(taskId, TaskStatus.from(status));
        return TaskSummary.from(updated);
    }

    @Tool(name = "add_note", description = """
            実装中の気づき・進捗・引き継ぎ事項をタスクに書き戻す(追記のみ。既存ノートは編集できない)。

            重要: セッションを終了する前に、次のセッションが続きから始められる粒度で
            進捗を必ず書き戻すこと。具体的には
            「どこまで実装したか」「触ったファイル」「残っている作業」
            「試して駄目だった方法とその理由」「判断に迷って保留にした点」を書く。
            単なる「作業しました」ではなく、記憶が完全に失われた状態の自分が読んで
            再開できる内容にすること。

            作業の途中でも、後から効いてくる発見(仕様の曖昧さ、既存コードの罠など)が
            あればその都度書き残してよい。本文は Markdown で書ける。
            """)
    public AddedNote addNote(
            @ToolParam(description = "タスク ID。")
            int taskId,
            @ToolParam(description = "追記する本文。Markdown 可。")
            String body) {

        Note note = taskService.addNote(taskId, NoteAuthor.CLAUDE_CODE, body);
        return new AddedNote(note.id(), TIMESTAMP.format(note.createdAt()) + " UTC");
    }
}

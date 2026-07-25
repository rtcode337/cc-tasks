package dev.cctasks;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import dev.cctasks.note.Note;
import dev.cctasks.note.NoteAuthor;
import dev.cctasks.note.NoteRepository;
import dev.cctasks.project.Project;
import dev.cctasks.project.ProjectService;
import dev.cctasks.task.Task;
import dev.cctasks.task.TaskService;
import dev.cctasks.task.TaskStatus;
import dev.cctasks.web.ApiException;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SQLite + Spring Data JDBC の噛み合わせを実際の DB で確認する。
 * 方言・型変換まわりは静的に見て分からないので、ここが崩れたら即気付けるようにしておく。
 */
@SpringBootTest
@Transactional
class TaskServiceIntegrationTests {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) throws Exception {
        Path db = Path.of(System.getProperty("java.io.tmpdir"), "cctasks-test-" + System.nanoTime() + ".db");
        db.toFile().deleteOnExit();
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + db + "?foreign_keys=on");
    }

    @Autowired
    ProjectService projectService;

    @Autowired
    TaskService taskService;

    @Autowired
    NoteRepository noteRepository;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void タイムスタンプはISO8601のTEXTで保存される() {
        Project project = projectService.create("sample-project", null, null);

        String stored = jdbc.queryForObject(
                "SELECT created_at FROM projects WHERE id = ?", String.class, project.id());

        assertThat(stored).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z");
        assertThat(Instant.parse(stored)).isEqualTo(project.createdAt());
    }

    @Test
    void 真偽値は0と1で保存され読み戻せる() {
        Project project = projectService.create("another-project", null, null);
        assertThat(project.archived()).isFalse();

        Project archived = projectService.update(project.id(), null, null, null, true);
        assertThat(archived.archived()).isTrue();

        assertThat(jdbc.queryForObject("SELECT archived FROM projects WHERE id = ?", Integer.class, project.id()))
                .isEqualTo(1);
        assertThat(projectService.list(true)).extracting(Project::name).containsExactly("another-project");
        assertThat(projectService.list(false)).isEmpty();
    }

    @Test
    void statusはDDLのCHECK制約と同じ表記で保存される() {
        Project project = projectService.create("sample-project", null, null);
        Task task = taskService.create(project.id(), "並び替え機能の実装", null, null, null, null);

        assertThat(task.status()).isEqualTo(TaskStatus.TODO);
        assertThat(jdbc.queryForObject("SELECT status FROM tasks WHERE id = ?", String.class, task.id()))
                .isEqualTo("todo");

        taskService.updateStatus(task.id(), TaskStatus.IN_PROGRESS);
        assertThat(jdbc.queryForObject("SELECT status FROM tasks WHERE id = ?", String.class, task.id()))
                .isEqualTo("in_progress");
    }

    @Test
    void 部分更新は指定しなかった項目を変えない() {
        Project project = projectService.create("sample-project", null, null);
        Task task = taskService.create(project.id(), "元のタイトル", "元のコンテキスト", "元の条件", null, null);

        Task updated = taskService.update(task.id(), null, null, null, null, null, TaskStatus.DONE);

        assertThat(updated.title()).isEqualTo("元のタイトル");
        assertThat(updated.context()).isEqualTo("元のコンテキスト");
        assertThat(updated.acceptanceCriteria()).isEqualTo("元の条件");
        assertThat(updated.status()).isEqualTo(TaskStatus.DONE);
        assertThat(updated.createdAt()).isEqualTo(task.createdAt());
    }

    @Test
    void ノートは新しい順に並ぶ() {
        Project project = projectService.create("sample-project", null, null);
        Task task = taskService.create(project.id(), "タスク", null, null, null, null);

        taskService.addNote(task.id(), NoteAuthor.HUMAN, "1 件目");
        taskService.addNote(task.id(), NoteAuthor.CLAUDE_CODE, "2 件目");

        List<Note> notes = noteRepository.findByTaskIdNewestFirst(task.id());
        assertThat(notes).extracting(Note::body).containsExactly("2 件目", "1 件目");
        assertThat(notes.get(0).author()).isEqualTo(NoteAuthor.CLAUDE_CODE);
    }

    @Test
    void タスク削除でノートも消える() {
        Project project = projectService.create("sample-project", null, null);
        Task task = taskService.create(project.id(), "タスク", null, null, null, null);
        taskService.addNote(task.id(), NoteAuthor.HUMAN, "メモ");

        taskService.delete(task.id());

        assertThat(noteRepository.findByTaskIdNewestFirst(task.id())).isEmpty();
        assertThatThrownBy(() -> taskService.requireById(task.id())).isInstanceOf(ApiException.class);
    }

    @Test
    void MCPのlist_tasksはstatus省略時にdoneを除く() {
        Project project = projectService.create("sample-project", null, null);
        Task open = taskService.create(project.id(), "残っている", null, null, null, null);
        Task closed = taskService.create(project.id(), "終わった", null, null, null, null);
        taskService.updateStatus(closed.id(), TaskStatus.DONE);

        assertThat(taskService.listByProjectName("sample-project", null))
                .extracting(Task::id).containsExactly(open.id());
        assertThat(taskService.listByProjectName("sample-project", TaskStatus.DONE))
                .extracting(Task::id).containsExactly(closed.id());
    }

    @Test
    void 未完了一覧はdoneを除き完了一覧はページングされる() {
        Project project = projectService.create("sample-project", null, null);
        // done を 12 件、todo を 2 件
        for (int i = 0; i < 12; i++) {
            Task t = taskService.create(project.id(), "完了 " + i, null, null, null, null);
            taskService.updateStatus(t.id(), TaskStatus.DONE);
        }
        taskService.create(project.id(), "未完了 A", null, null, null, null);
        taskService.create(project.id(), "未完了 B", null, null, null, null);

        // 未完了(active)には done が出ない
        assertThat(taskService.listActive(null)).hasSize(2)
                .allSatisfy(t -> assertThat(t.status()).isNotEqualTo(TaskStatus.DONE));

        // 完了は 10 件ずつページング。total は 12
        var page0 = taskService.listDone(null, 0, 10);
        assertThat(page0.total()).isEqualTo(12);
        assertThat(page0.items()).hasSize(10);
        var page1 = taskService.listDone(null, 1, 10);
        assertThat(page1.items()).hasSize(2);
        // 全 done が status=done であること
        assertThat(page0.items()).allSatisfy(t -> assertThat(t.status()).isEqualTo(TaskStatus.DONE));
    }

    @Test
    void 同名プロジェクトは409になる() {
        projectService.create("sample-project", null, null);

        assertThatThrownBy(() -> projectService.create("sample-project", null, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).status().value()).isEqualTo(409));
    }

    @Test
    void リポジトリURLは複数持て改行区切りで保存される() {
        Project project = projectService.create("multi-repo",
                java.util.List.of(" https://github.com/example/app ", "", "https://github.com/example/infra",
                        "https://github.com/example/app"),
                null);

        // trim・空要素除去・重複除去(先勝ち)される
        assertThat(project.repoUrlList()).containsExactly(
                "https://github.com/example/app", "https://github.com/example/infra");
        assertThat(jdbc.queryForObject("SELECT repo_url FROM projects WHERE id = ?", String.class, project.id()))
                .isEqualTo("https://github.com/example/app\nhttps://github.com/example/infra");

        // null は「変更しない」
        Project untouched = projectService.update(project.id(), null, null, "説明だけ更新", null);
        assertThat(untouched.repoUrlList()).hasSize(2);

        // 空リストは「全部消す」
        Project cleared = projectService.update(project.id(), null, java.util.List.of(), null, null);
        assertThat(cleared.repoUrlList()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT repo_url FROM projects WHERE id = ?", String.class, project.id()))
                .isNull();
    }

    @Test
    void プロジェクト無しでタスクを作成し後から紐づけできる() {
        Task memo = taskService.create(null, "出先で思いついたメモ", null, null, null, null);
        assertThat(memo.projectId()).isNull();
        assertThat(taskService.detail(memo.id()).project()).isNull();

        Project project = projectService.create("sample-project", null, null);
        Task linked = taskService.update(memo.id(), project.id(), null, null, null, null, null);

        assertThat(linked.projectId()).isEqualTo(project.id());
        assertThat(taskService.detail(memo.id()).project().name()).isEqualTo("sample-project");
    }

    @Test
    void 存在しないプロジェクトへのタスク作成は404になる() {
        assertThatThrownBy(() -> taskService.create(9999L, "タスク", null, null, null, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).status().value()).isEqualTo(404));
    }
}

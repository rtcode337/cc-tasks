package dev.cctasks;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import dev.cctasks.project.Project;
import dev.cctasks.project.ProjectService;
import dev.cctasks.rule.Rule;
import dev.cctasks.rule.RuleService;
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
    RuleService ruleService;

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
        Task task = taskService.create(project.id(), "並び替え機能の実装", null);

        assertThat(task.status()).isEqualTo(TaskStatus.TODO);
        assertThat(jdbc.queryForObject("SELECT status FROM tasks WHERE id = ?", String.class, task.id()))
                .isEqualTo("todo");

        taskService.updateStatus(task.id(), TaskStatus.DONE);
        assertThat(jdbc.queryForObject("SELECT status FROM tasks WHERE id = ?", String.class, task.id()))
                .isEqualTo("done");

        // 完了から未完了へ戻せる(遷移に制約は設けない)
        taskService.updateStatus(task.id(), TaskStatus.TODO);
        assertThat(jdbc.queryForObject("SELECT status FROM tasks WHERE id = ?", String.class, task.id()))
                .isEqualTo("todo");
    }

    @Test
    void 部分更新は指定しなかった項目を変えない() {
        Project project = projectService.create("sample-project", null, null);
        Task task = taskService.create(project.id(), "元のタイトル", null);

        Task updated = taskService.update(task.id(), null, null, TaskStatus.DONE);

        assertThat(updated.title()).isEqualTo("元のタイトル");
        assertThat(updated.projectId()).isEqualTo(project.id());
        assertThat(updated.status()).isEqualTo(TaskStatus.DONE);
        assertThat(updated.createdAt()).isEqualTo(task.createdAt());
    }

    @Test
    void 未完了一覧はdoneを除き完了一覧はページングされる() {
        Project project = projectService.create("sample-project", null, null);
        // done を 12 件、todo を 2 件
        for (int i = 0; i < 12; i++) {
            Task t = taskService.create(project.id(), "完了 " + i, null);
            taskService.updateStatus(t.id(), TaskStatus.DONE);
        }
        taskService.create(project.id(), "未完了 A", null);
        taskService.create(project.id(), "未完了 B", null);

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
    void プロジェクト内のタスクは手動で並び替えられる() {
        Project project = projectService.create("sample-project", null, null);
        Task a = taskService.create(project.id(), "A", null);
        Task b = taskService.create(project.id(), "B", null);
        Task c = taskService.create(project.id(), "C", null);

        // 並び替える前は新しい順
        assertThat(taskService.listActive(project.id()))
                .extracting(Task::title).containsExactly("C", "B", "A");

        taskService.reorder(project.id(), List.of(a.id(), c.id(), b.id()));
        assertThat(taskService.listActive(project.id()))
                .extracting(Task::title).containsExactly("A", "C", "B");

        // 新しく放り込んだタスクは sortOrder=0 なので並び替え済み (1..n) より前に積まれる
        taskService.create(project.id(), "D", null);
        assertThat(taskService.listActive(project.id()))
                .extracting(Task::title).containsExactly("D", "A", "C", "B");

        // 編集しても並び順は動かない
        taskService.update(a.id(), null, "A(改題)", null);
        assertThat(taskService.listActive(project.id()))
                .extracting(Task::title).containsExactly("D", "A(改題)", "C", "B");
    }

    @Test
    void 未紐づけタスクも並び替えでき別プロジェクト混在は400になる() {
        Task a = taskService.create(null, "A", null);
        Task b = taskService.create(null, "B", null);

        assertThat(taskService.reorder(null, List.of(b.id(), a.id())))
                .extracting(Task::sortOrder).containsExactly(1, 2);
        assertThat(jdbc.queryForObject("SELECT sort_order FROM tasks WHERE id = ?", Integer.class, b.id()))
                .isEqualTo(1);

        // 紐づいたタスクを未紐づけのかたまりに混ぜることはできない
        Project project = projectService.create("sample-project", null, null);
        Task linked = taskService.create(project.id(), "紐づき", null);
        assertThatThrownBy(() -> taskService.reorder(null, List.of(a.id(), linked.id())))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).status().value()).isEqualTo(400));
    }

    @Test
    void 未完了が残っているプロジェクトはアーカイブできない() {
        Project project = projectService.create("sample-project", null, null);
        Task task = taskService.create(project.id(), "残っている", null);

        assertThatThrownBy(() -> projectService.update(project.id(), null, null, null, true))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).status().value()).isEqualTo(400));

        // 片付ければ通る
        taskService.updateStatus(task.id(), TaskStatus.DONE);
        assertThat(projectService.update(project.id(), null, null, null, true).archived()).isTrue();

        // 戻すのはいつでもよい
        assertThat(projectService.update(project.id(), null, null, null, false).archived()).isFalse();
    }

    @Test
    void プロジェクトはアーカイブ済みのときだけタスクごと削除できる() {
        Project project = projectService.create("sample-project", null, null);
        Task todo = taskService.create(project.id(), "未完了のまま", null);
        Task done = taskService.create(project.id(), "片付いた", null);
        taskService.updateStatus(done.id(), TaskStatus.DONE);

        // アーカイブ前は消せない
        assertThatThrownBy(() -> projectService.delete(project.id()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).status().value()).isEqualTo(400));

        taskService.updateStatus(todo.id(), TaskStatus.DONE);
        projectService.update(project.id(), null, null, null, true);
        projectService.delete(project.id());

        assertThat(projectService.list(null)).noneMatch(p -> p.id().equals(project.id()));
        // 完了済みも含めて巻き添えで消える
        assertThat(taskService.search(null, null)).noneMatch(t -> t.projectId() != null
                && t.projectId().equals(project.id()));
    }

    @Test
    void ルールは表示順に連結され無効なものは含まれない() {
        Rule first = ruleService.create("コミットの作法", "- main に直接 push しない", null);
        Rule second = ruleService.create("テスト", "- 変更したら必ずテストを走らせる", null);
        Rule off = ruleService.create("下書き", "まだ有効にしていない", false);

        assertThat(ruleService.combined()).isEqualTo("""
                ## コミットの作法

                - main に直接 push しない

                ## テスト

                - 変更したら必ずテストを走らせる
                """);

        // 並び替えると連結順も入れ替わる
        ruleService.reorder(List.of(second.id(), off.id(), first.id()));
        assertThat(ruleService.combined()).startsWith("## テスト").endsWith("- main に直接 push しない\n");

        // 有効にすれば連結に入る
        ruleService.update(off.id(), null, null, true);
        assertThat(ruleService.combined()).contains("## 下書き");

        // 1 本も有効でなければ空文字
        for (Rule rule : ruleService.list()) {
            ruleService.update(rule.id(), null, null, false);
        }
        assertThat(ruleService.combined()).isEmpty();
    }

    @Test
    void ルールの部分更新と削除() {
        Rule rule = ruleService.create("見出し", "本文", null);

        // null のフィールドは変更しない
        Rule renamed = ruleService.update(rule.id(), "新しい見出し", null, null);
        assertThat(renamed.title()).isEqualTo("新しい見出し");
        assertThat(renamed.body()).isEqualTo("本文");
        assertThat(renamed.enabled()).isTrue();

        ruleService.delete(rule.id());
        assertThat(ruleService.list()).isEmpty();
        assertThatThrownBy(() -> ruleService.requireById(rule.id()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).status().value()).isEqualTo(404));
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
        Task memo = taskService.create(null, "出先で思いついたメモ", null);
        assertThat(memo.projectId()).isNull();
        assertThat(taskService.detail(memo.id()).project()).isNull();

        Project project = projectService.create("sample-project", null, null);
        Task linked = taskService.update(memo.id(), project.id(), null, null);

        assertThat(linked.projectId()).isEqualTo(project.id());
        assertThat(taskService.detail(memo.id()).project().name()).isEqualTo("sample-project");
    }

    @Test
    void 存在しないプロジェクトへのタスク作成は404になる() {
        assertThatThrownBy(() -> taskService.create(9999L, "タスク", null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).status().value()).isEqualTo(404));
    }
}

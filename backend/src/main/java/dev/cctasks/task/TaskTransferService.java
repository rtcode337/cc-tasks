package dev.cctasks.task;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.cctasks.project.Project;
import dev.cctasks.project.ProjectRepository;
import dev.cctasks.project.ProjectService;
import dev.cctasks.web.ApiException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 未完了タスクの一括書き出し・読み込み。
 *
 * <p>DB を失っても打ち直さずに戻せるようにするための機能。**書き出したものをそのまま
 * 読み込める**(書き出し結果が読み込みのリクエスト本文と同じ形)ので、テキストとして
 * 手元に置いておけばバックアップになる。
 *
 * <p>持ち出すのは<b>未完了のタスクと、その所属プロジェクトの名前・リポジトリ</b>だけ。
 * 完了タスクは「片付いたものの記録」で復元する意味が薄く、プロジェクトの説明・並び順・
 * アーカイブ状態は復元対象にしない(戻したいのは待ち行列であって画面の状態ではない)。
 */
@Service
public class TaskTransferService {

    /**
     * 書き出し形式のバージョン。読み込み側は「これ以下なら読む」で判定する ——
     * 将来 2 を書き出すようになっても、古い 1 のファイルは読めるようにするため。
     */
    public static final int FORMAT_VERSION = 1;

    /** 書き出した 1 タスク。id も並び順も持たない(復元先で採番するため)。 */
    public record ExportedTask(String title, TaskStatus status) {
    }

    /** プロジェクトと、そこに属する未完了タスク。 */
    public record ExportedProject(String name, List<String> repoUrls, List<ExportedTask> tasks) {
    }

    /** 書き出し全体。これがそのまま読み込みのリクエスト本文になる。 */
    public record Export(
            int version,
            Instant exportedAt,
            List<ExportedProject> projects,
            List<ExportedTask> unassignedTasks) {
    }

    /**
     * 読み込み結果。件数ではなく中身を返すのは、実行前の確認({@code dryRun})で
     * 「何が作られ、何が飛ばされるか」をそのまま画面に出すため。
     */
    public record ImportResult(
            List<String> createdProjects,
            List<String> createdTasks,
            List<String> skippedTasks) {
    }

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final ProjectService projectService;
    private final Clock clock;

    public TaskTransferService(TaskRepository taskRepository, ProjectRepository projectRepository,
            ProjectService projectService, Clock clock) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.projectService = projectService;
        this.clock = clock;
    }

    /**
     * 未完了タスクを書き出す。プロジェクトは表示順、タスクは各プロジェクト内の並び順。
     * 未完了タスクを持たないプロジェクトは含めない(戻したいのはタスクなので、
     * 空のプロジェクトまで作ると復元先に使っていない箱が増える)。
     */
    @Transactional(readOnly = true)
    public Export export() {
        List<ExportedProject> projects = new ArrayList<>();
        for (Project project : projectRepository.findAllOrdered()) {
            List<ExportedTask> tasks = exported(taskRepository.searchActive(project.id()));
            if (!tasks.isEmpty()) {
                projects.add(new ExportedProject(project.name(), project.repoUrlList(), tasks));
            }
        }
        return new Export(FORMAT_VERSION, now(), projects, exported(unassignedActive()));
    }

    /**
     * 書き出したものを読み込む。
     *
     * <p>プロジェクトは<b>名前で照合し、無ければ作る</b>(リポジトリも一緒に登録する)。
     * 既にあるものは触らない —— 復元のたびに手元の設定を上書きされると困るため。
     *
     * <p>タスクは<b>同じプロジェクトに同じタイトルの未完了タスクがあれば飛ばす</b>。
     * 同じファイルを二度読んでも増えないようにするため(復元は繰り返し試すことがある)。
     *
     * @param dryRun true なら書き込まず、作る/飛ばす予定だけ返す
     */
    @Transactional
    public ImportResult importTasks(Export data, boolean dryRun) {
        if (data == null || (isEmpty(data.projects()) && isEmpty(data.unassignedTasks()))) {
            throw ApiException.badRequest("読み込めるタスクがありません。書き出した JSON を貼り付けてください");
        }
        if (data.version() > FORMAT_VERSION) {
            throw ApiException.badRequest(
                    "対応していない形式です(version=%d)。このアプリが読めるのは %d までです"
                            .formatted(data.version(), FORMAT_VERSION));
        }

        ImportResult result = new ImportResult(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        // 既存の未完了タスクの (プロジェクト名, タイトル)。プロジェクトを id ではなく名前で
        // 引くのは、dryRun ではまだ id が無い(作っていない)プロジェクトがあるため
        Set<String> taken = existingKeys();

        for (ExportedProject entry : nullSafe(data.projects())) {
            String name = requireProjectName(entry.name());
            Project existing = projectRepository.findByName(name).orElse(null);
            Long projectId = existing != null ? existing.id() : null;
            if (existing == null) {
                result.createdProjects().add(name);
                if (!dryRun) {
                    projectId = projectService.create(name, entry.repoUrls(), null).id();
                }
            }
            importInto(projectId, name, entry.tasks(), taken, result, dryRun);
        }
        importInto(null, "", data.unassignedTasks(), taken, result, dryRun);
        return result;
    }

    private void importInto(Long projectId, String projectName, List<ExportedTask> tasks,
            Set<String> taken, ImportResult result, boolean dryRun) {
        for (ExportedTask task : nullSafe(tasks)) {
            if (!StringUtils.hasText(task.title())) {
                continue;
            }
            String title = task.title().trim();
            String label = projectName.isEmpty() ? title : projectName + " / " + title;
            // taken に足せなかった = 既にある、または同じファイル内での重複
            if (!taken.add(key(projectName, title))) {
                result.skippedTasks().add(label);
                continue;
            }
            result.createdTasks().add(label);
            if (!dryRun) {
                Instant now = now();
                // sortOrder=0 は「並び替えていない」。TaskService.create と同じ扱いにする
                taskRepository.save(new Task(null, projectId, title,
                        task.status() != null ? task.status() : TaskStatus.TODO, 0, now, now));
            }
        }
    }

    /** 既存の未完了タスクを (プロジェクト名, タイトル) で引けるようにする。 */
    private Set<String> existingKeys() {
        Map<Long, String> nameById = projectRepository.findAllOrdered().stream()
                .collect(Collectors.toMap(Project::id, Project::name, (a, b) -> a, LinkedHashMap::new));
        return taskRepository.searchActive(null).stream()
                .map(task -> key(task.projectId() != null ? nameById.getOrDefault(task.projectId(), "") : "",
                        task.title()))
                .collect(Collectors.toCollection(HashSet::new));
    }

    private static String key(String projectName, String title) {
        // NUL 区切り。表示に使える文字でつなぐと、名前とタイトルの境目が違っても
        // 同じキーになりうる(「A」+「B C」と「A B」+「C」)
        return projectName + '\0' + title;
    }

    /** プロジェクトに紐づいていない未完了タスク。 */
    private List<Task> unassignedActive() {
        return taskRepository.searchActive(null).stream()
                .filter(task -> task.projectId() == null)
                .toList();
    }

    private static List<ExportedTask> exported(List<Task> tasks) {
        return tasks.stream().map(task -> new ExportedTask(task.title(), task.status())).toList();
    }

    private static String requireProjectName(String name) {
        if (!StringUtils.hasText(name)) {
            throw ApiException.badRequest("プロジェクト名が空のものがあります");
        }
        return name.trim();
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list != null ? list : List.of();
    }

    private static boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}

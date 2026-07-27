package dev.cctasks.task;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import dev.cctasks.project.Project;
import dev.cctasks.project.ProjectService;
import dev.cctasks.web.ApiException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * タスク操作のサービス層。
 */
@Service
public class TaskService {

    /**
     * 更新で projectId にこれ(0)を送ると紐づけを外す(未分類に戻す)。
     * null は「変更しない」の意味なので、外す指示をそれと分けるための値。
     * プロジェクトの id は 1 から振られるので実在の id とはぶつからない。
     */
    public static final long UNLINK_PROJECT_ID = 0L;

    private final TaskRepository taskRepository;
    private final ProjectService projectService;
    private final Clock clock;

    public TaskService(TaskRepository taskRepository, ProjectService projectService, Clock clock) {
        this.taskRepository = taskRepository;
        this.projectService = projectService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Task> search(Long projectId, TaskStatus status) {
        return taskRepository.search(projectId, status != null ? status.wireValue() : null);
    }

    /** 未完了(done 以外)。トップと一覧の既定表示。 */
    @Transactional(readOnly = true)
    public List<Task> listActive(Long projectId) {
        return taskRepository.searchActive(projectId);
    }

    /** 完了タスクをページングで取得する。size は 1〜100 に収める。 */
    @Transactional(readOnly = true)
    public TaskPage listDone(Long projectId, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        long total = taskRepository.countDone(projectId);
        List<Task> items = taskRepository.searchDone(projectId, safeSize, safePage * safeSize);
        return new TaskPage(items, total, safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public Task requireById(long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("タスクが見つかりません: id=" + id));
    }

    @Transactional(readOnly = true)
    public TaskDetail detail(long id) {
        Task task = requireById(id);
        // 未紐づけなら project は null
        Project project = task.projectId() != null ? projectService.requireById(task.projectId()) : null;
        return new TaskDetail(task, project);
    }

    @Transactional
    public Task create(Long projectId, String title, TaskStatus status) {
        // プロジェクト紐づけは任意。指定された場合だけ存在確認する
        if (projectId != null) {
            projectService.requireById(projectId);
        }
        if (!StringUtils.hasText(title)) {
            throw ApiException.badRequest("title は必須です");
        }
        Instant now = now();
        // sortOrder=0 は「並び替えていない」。手動で並べた分 (1..n) より前に来るので
        // 放り込んだタスクはグループの先頭に積まれる
        return taskRepository.save(new Task(null, projectId, title.trim(),
                status != null ? status : TaskStatus.TODO, 0, now, now));
    }

    /**
     * null のフィールドは「変更しない」を意味する部分更新。
     * 状態遷移に制約は設けない(仕様書 §5.2)。
     *
     * <p>projectId だけは「変更しない」と「紐づけを外す」を分ける必要があるので、
     * {@link #UNLINK_PROJECT_ID} を送ったときだけ未分類に戻す。
     */
    @Transactional
    public Task update(long id, Long projectId, String title, TaskStatus status) {
        Task current = requireById(id);
        boolean unlink = projectId != null && projectId == UNLINK_PROJECT_ID;
        if (projectId != null && !unlink) {
            projectService.requireById(projectId);
        }
        if (title != null && !StringUtils.hasText(title)) {
            throw ApiException.badRequest("title を空にはできません");
        }
        Task updated = new Task(
                current.id(),
                unlink ? null : (projectId != null ? projectId : current.projectId()),
                title != null ? title.trim() : current.title(),
                status != null ? status : current.status(),
                current.sortOrder(),
                current.createdAt(),
                now());
        return taskRepository.save(updated);
    }

    /**
     * プロジェクト内(未紐づけなら {@code projectId=null} のかたまり)の手動並び替え。
     * 渡した id へ先頭から 1, 2, 3, … と sort_order を振る。
     *
     * <p>画面に出ていないタスクには触らないので ids は部分集合でよい。
     * ただし別プロジェクトのタスクを混ぜると順序の意味が壊れるため、そこだけは弾く。
     */
    @Transactional
    public List<Task> reorder(Long projectId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw ApiException.badRequest("ids は必須です");
        }
        if (new HashSet<>(ids).size() != ids.size()) {
            throw ApiException.badRequest("ids に重複があります");
        }
        List<Task> reordered = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            Task task = requireById(ids.get(i));
            if (!Objects.equals(task.projectId(), projectId)) {
                throw ApiException.badRequest(
                        "別のプロジェクトのタスクは同時に並び替えできません: id=" + task.id());
            }
            int sortOrder = i + 1;
            taskRepository.updateSortOrder(task.id(), sortOrder);
            reordered.add(new Task(task.id(), task.projectId(), task.title(), task.status(),
                    sortOrder, task.createdAt(), task.updatedAt()));
        }
        return reordered;
    }

    @Transactional
    public Task updateStatus(long id, TaskStatus status) {
        if (status == null) {
            throw ApiException.badRequest("status は必須です");
        }
        return update(id, null, null, status);
    }

    /** 物理削除。 */
    @Transactional
    public void delete(long id) {
        taskRepository.deleteById(requireById(id).id());
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}

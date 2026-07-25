package dev.cctasks.task;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import dev.cctasks.note.Note;
import dev.cctasks.note.NoteAuthor;
import dev.cctasks.note.NoteRepository;
import dev.cctasks.project.Project;
import dev.cctasks.project.ProjectService;
import dev.cctasks.web.ApiException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * REST(PWA)と MCP(Claude Code)が共有するタスク操作のサービス層。
 */
@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final NoteRepository noteRepository;
    private final ProjectService projectService;
    private final Clock clock;

    public TaskService(TaskRepository taskRepository, NoteRepository noteRepository,
            ProjectService projectService, Clock clock) {
        this.taskRepository = taskRepository;
        this.noteRepository = noteRepository;
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

    /**
     * MCP list_tasks 用。status 省略時は done 以外を返す。
     */
    @Transactional(readOnly = true)
    public List<Task> listByProjectName(String projectName, TaskStatus status) {
        Project project = projectService.requireByName(projectName);
        if (status == null) {
            return taskRepository.findOpenByProjectId(project.id());
        }
        return taskRepository.search(project.id(), status.wireValue());
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
        return new TaskDetail(task, project, noteRepository.findByTaskIdNewestFirst(id));
    }

    @Transactional
    public Task create(Long projectId, String title, String context, String acceptanceCriteria,
            String outOfScope, TaskStatus status) {
        // プロジェクト紐づけは任意。指定された場合だけ存在確認する
        if (projectId != null) {
            projectService.requireById(projectId);
        }
        if (!StringUtils.hasText(title)) {
            throw ApiException.badRequest("title は必須です");
        }
        Instant now = now();
        return taskRepository.save(new Task(null, projectId, title.trim(),
                blankToNull(context), blankToNull(acceptanceCriteria), blankToNull(outOfScope),
                status != null ? status : TaskStatus.TODO, now, now));
    }

    /**
     * null のフィールドは「変更しない」を意味する部分更新。
     * 状態遷移に制約は設けない(仕様書 §5.2)。
     */
    @Transactional
    public Task update(long id, Long projectId, String title, String context,
            String acceptanceCriteria, String outOfScope, TaskStatus status) {
        Task current = requireById(id);
        if (projectId != null) {
            projectService.requireById(projectId);
        }
        if (title != null && !StringUtils.hasText(title)) {
            throw ApiException.badRequest("title を空にはできません");
        }
        Task updated = new Task(
                current.id(),
                projectId != null ? projectId : current.projectId(),
                title != null ? title.trim() : current.title(),
                context != null ? blankToNull(context) : current.context(),
                acceptanceCriteria != null ? blankToNull(acceptanceCriteria) : current.acceptanceCriteria(),
                outOfScope != null ? blankToNull(outOfScope) : current.outOfScope(),
                status != null ? status : current.status(),
                current.createdAt(),
                now());
        return taskRepository.save(updated);
    }

    @Transactional
    public Task updateStatus(long id, TaskStatus status) {
        if (status == null) {
            throw ApiException.badRequest("status は必須です");
        }
        return update(id, null, null, null, null, null, status);
    }

    /** 物理削除。notes もカスケード削除する(仕様書 §5.2)。 */
    @Transactional
    public void delete(long id) {
        Task task = requireById(id);
        noteRepository.deleteByTaskId(task.id());
        taskRepository.deleteById(task.id());
    }

    /** ノートは追記のみ。更新・削除の口は用意しない(仕様書 §5.3)。 */
    @Transactional
    public Note addNote(long taskId, NoteAuthor author, String body) {
        Task task = requireById(taskId);
        if (!StringUtils.hasText(body)) {
            throw ApiException.badRequest("body は必須です");
        }
        Note note = noteRepository.save(new Note(null, task.id(), author, body, now()));
        // ノート追記もタスクの「動き」なので updated_at を進める(一覧の並び順に効く)
        taskRepository.save(new Task(task.id(), task.projectId(), task.title(), task.context(),
                task.acceptanceCriteria(), task.outOfScope(), task.status(), task.createdAt(), now()));
        return note;
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}

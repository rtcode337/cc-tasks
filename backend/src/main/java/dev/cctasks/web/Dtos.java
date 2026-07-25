package dev.cctasks.web;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import dev.cctasks.note.Note;
import dev.cctasks.project.Project;
import dev.cctasks.task.Task;
import dev.cctasks.task.TaskDetail;
import dev.cctasks.task.TaskStatus;
import jakarta.validation.constraints.NotBlank;

/**
 * REST の入出力 DTO。
 *
 * <p>JSON は camelCase で統一する。リクエストは snake_case も {@code @JsonAlias} で受け付ける
 * (仕様書は DB 列名と混在した表記のため、どちらで送っても通るようにしておく)。
 */
public final class Dtos {

    private Dtos() {
    }

    // --- me ---

    public record MeResponse(String email, String name, String pictureUrl) {
    }

    // --- projects ---

    public record ProjectResponse(
            Long id,
            String name,
            String repoUrl,
            String description,
            boolean archived,
            Instant createdAt,
            Instant updatedAt) {

        public static ProjectResponse from(Project p) {
            return new ProjectResponse(p.id(), p.name(), p.repoUrl(), p.description(),
                    p.archived(), p.createdAt(), p.updatedAt());
        }
    }

    public record CreateProjectRequest(
            @NotBlank(message = "は必須です") String name,
            @JsonAlias("repo_url") String repoUrl,
            String description) {
    }

    public record UpdateProjectRequest(
            String name,
            @JsonAlias("repo_url") String repoUrl,
            String description,
            Boolean archived) {
    }

    // --- tasks ---

    public record TaskResponse(
            Long id,
            Long projectId,
            String title,
            String context,
            String acceptanceCriteria,
            String outOfScope,
            TaskStatus status,
            Instant createdAt,
            Instant updatedAt) {

        public static TaskResponse from(Task t) {
            return new TaskResponse(t.id(), t.projectId(), t.title(), t.context(),
                    t.acceptanceCriteria(), t.outOfScope(), t.status(), t.createdAt(), t.updatedAt());
        }
    }

    public record TaskDetailResponse(
            Long id,
            Long projectId,
            String projectName,
            String title,
            String context,
            String acceptanceCriteria,
            String outOfScope,
            TaskStatus status,
            Instant createdAt,
            Instant updatedAt,
            List<NoteResponse> notes) {

        public static TaskDetailResponse from(TaskDetail d) {
            Task t = d.task();
            return new TaskDetailResponse(t.id(), t.projectId(),
                    d.project() != null ? d.project().name() : null, t.title(),
                    t.context(), t.acceptanceCriteria(), t.outOfScope(), t.status(),
                    t.createdAt(), t.updatedAt(),
                    d.notes().stream().map(NoteResponse::from).toList());
        }
    }

    public record CreateTaskRequest(
            @JsonAlias("project_id") Long projectId,
            @NotBlank(message = "は必須です") String title,
            String context,
            @JsonAlias("acceptance_criteria") String acceptanceCriteria,
            @JsonAlias("out_of_scope") String outOfScope,
            TaskStatus status) {
    }

    public record UpdateTaskRequest(
            @JsonAlias("project_id") Long projectId,
            String title,
            String context,
            @JsonAlias("acceptance_criteria") String acceptanceCriteria,
            @JsonAlias("out_of_scope") String outOfScope,
            TaskStatus status) {
    }

    public record PagedResponse<T>(List<T> items, long total, int page, int size, int totalPages) {

        public PagedResponse(List<T> items, long total, int page, int size) {
            this(items, total, page, size, (int) Math.ceil((double) total / Math.max(1, size)));
        }
    }

    // --- notes ---

    public record NoteResponse(Long id, Long taskId, String author, String body, Instant createdAt) {

        public static NoteResponse from(Note n) {
            return new NoteResponse(n.id(), n.taskId(), n.author().wireValue(), n.body(), n.createdAt());
        }
    }

    public record CreateNoteRequest(@NotBlank(message = "は必須です") String body) {
    }
}

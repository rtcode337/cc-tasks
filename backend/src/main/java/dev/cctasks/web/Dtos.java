package dev.cctasks.web;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import dev.cctasks.project.Project;
import dev.cctasks.rule.Rule;
import dev.cctasks.task.Task;
import dev.cctasks.task.TaskDetail;
import dev.cctasks.task.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

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
            List<String> repoUrls,
            String description,
            boolean archived,
            int sortOrder,
            Instant createdAt,
            Instant updatedAt) {

        public static ProjectResponse from(Project p) {
            return new ProjectResponse(p.id(), p.name(), p.repoUrlList(), p.description(),
                    p.archived(), p.sortOrder(), p.createdAt(), p.updatedAt());
        }
    }

    /** 並び替え。全プロジェクトの id を望む順で過不足なく指定する。 */
    public record ReorderProjectsRequest(@NotEmpty List<Long> ids) {
    }

    public record CreateProjectRequest(
            @NotBlank(message = "は必須です") String name,
            @JsonAlias("repo_urls") List<String> repoUrls,
            String description) {
    }

    /** repoUrls は null で「変更しない」、空配列で「全部消す」。 */
    public record UpdateProjectRequest(
            String name,
            @JsonAlias("repo_urls") List<String> repoUrls,
            String description,
            Boolean archived) {
    }

    // --- tasks ---

    public record TaskResponse(
            Long id,
            Long projectId,
            String title,
            TaskStatus status,
            int sortOrder,
            Instant createdAt,
            Instant updatedAt) {

        public static TaskResponse from(Task t) {
            return new TaskResponse(t.id(), t.projectId(), t.title(), t.status(), t.sortOrder(),
                    t.createdAt(), t.updatedAt());
        }
    }

    public record TaskDetailResponse(
            Long id,
            Long projectId,
            String projectName,
            String title,
            TaskStatus status,
            Instant createdAt,
            Instant updatedAt) {

        public static TaskDetailResponse from(TaskDetail d) {
            Task t = d.task();
            return new TaskDetailResponse(t.id(), t.projectId(),
                    d.project() != null ? d.project().name() : null, t.title(), t.status(),
                    t.createdAt(), t.updatedAt());
        }
    }

    public record CreateTaskRequest(
            @JsonAlias("project_id") Long projectId,
            @NotBlank(message = "は必須です") String title,
            TaskStatus status) {
    }

    /**
     * プロジェクト内の並び替え。ids を望む順で送る。
     * projectId は未紐づけのかたまりなら null。ids は画面に出ている分だけの部分集合でよい。
     */
    public record ReorderTasksRequest(
            @JsonAlias("project_id") Long projectId,
            @NotEmpty List<Long> ids) {
    }

    /**
     * null のフィールドは「変更しない」。
     * projectId は 0({@code TaskService.UNLINK_PROJECT_ID})を送ると紐づけを外す。
     */
    public record UpdateTaskRequest(
            @JsonAlias("project_id") Long projectId,
            String title,
            TaskStatus status) {
    }

    // --- rules ---

    public record RuleResponse(
            Long id,
            String title,
            String body,
            boolean enabled,
            int sortOrder,
            Instant createdAt,
            Instant updatedAt) {

        public static RuleResponse from(Rule r) {
            return new RuleResponse(r.id(), r.title(), r.body(), r.enabled(), r.sortOrder(),
                    r.createdAt(), r.updatedAt());
        }
    }

    /** 有効なルールを連結した 1 本の Markdown。 */
    public record CombinedRulesResponse(String markdown) {
    }

    public record CreateRuleRequest(
            @NotBlank(message = "は必須です") String title,
            @NotBlank(message = "は必須です") String body,
            Boolean enabled) {
    }

    /** null のフィールドは「変更しない」。 */
    public record UpdateRuleRequest(String title, String body, Boolean enabled) {
    }

    /** 並び替え。全ルールの id を望む順で過不足なく指定する。 */
    public record ReorderRulesRequest(@NotEmpty List<Long> ids) {
    }

    public record PagedResponse<T>(List<T> items, long total, int page, int size, int totalPages) {

        public PagedResponse(List<T> items, long total, int page, int size) {
            this(items, total, page, size, (int) Math.ceil((double) total / Math.max(1, size)));
        }
    }

}

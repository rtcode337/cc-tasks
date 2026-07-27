package dev.cctasks.web;

import java.util.List;

import dev.cctasks.project.Project;
import dev.cctasks.project.ProjectService;
import dev.cctasks.web.Dtos.CreateProjectRequest;
import dev.cctasks.web.Dtos.ProjectResponse;
import dev.cctasks.web.Dtos.ReorderProjectsRequest;
import dev.cctasks.web.Dtos.UpdateProjectRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    /**
     * {@code ?archived=false} が既定。{@code ?archived=} を空で送ると全件。
     *
     * <p>ここで {@code defaultValue} を使ってはいけない —— Spring は
     * 「パラメータが空文字」のときも defaultValue で置き換えるため、
     * {@code ?archived=}(全件のつもり)が {@code false} に化けてアーカイブ済みが消える。
     * 未指定(null)と空文字は自前で区別する。
     */
    @GetMapping
    public List<ProjectResponse> list(
            @RequestParam(name = "archived", required = false) String archived) {
        Boolean filter = archived == null ? Boolean.FALSE : archived.isBlank() ? null : Boolean.valueOf(archived);
        return projectService.list(filter).stream().map(ProjectResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@Valid @RequestBody CreateProjectRequest request) {
        Project created = projectService.create(request.name(), request.repoUrls(), request.description());
        return ProjectResponse.from(created);
    }

    /** 並び替え。全プロジェクトの id を望む順で送ると、並び替え後の一覧を返す。 */
    @PutMapping("/order")
    public List<ProjectResponse> reorder(@Valid @RequestBody ReorderProjectsRequest request) {
        return projectService.reorder(request.ids()).stream().map(ProjectResponse::from).toList();
    }

    @PatchMapping("/{id}")
    public ProjectResponse update(@PathVariable long id, @RequestBody UpdateProjectRequest request) {
        Project updated = projectService.update(id, request.name(), request.repoUrls(),
                request.description(), request.archived());
        return ProjectResponse.from(updated);
    }

    /** アーカイブ済みのみ。紐づくタスクも一緒に消える。 */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        projectService.delete(id);
    }
}

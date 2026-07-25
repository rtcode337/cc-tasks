package dev.cctasks.web;

import java.util.List;

import dev.cctasks.project.Project;
import dev.cctasks.project.ProjectService;
import dev.cctasks.web.Dtos.CreateProjectRequest;
import dev.cctasks.web.Dtos.ProjectResponse;
import dev.cctasks.web.Dtos.UpdateProjectRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    /** {@code ?archived=false} が既定。{@code ?archived=} を空で送ると全件。 */
    @GetMapping
    public List<ProjectResponse> list(
            @RequestParam(name = "archived", required = false, defaultValue = "false") String archived) {
        Boolean filter = archived.isBlank() ? null : Boolean.valueOf(archived);
        return projectService.list(filter).stream().map(ProjectResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@Valid @RequestBody CreateProjectRequest request) {
        Project created = projectService.create(request.name(), request.repoUrls(), request.description());
        return ProjectResponse.from(created);
    }

    @PatchMapping("/{id}")
    public ProjectResponse update(@PathVariable long id, @RequestBody UpdateProjectRequest request) {
        Project updated = projectService.update(id, request.name(), request.repoUrl(),
                request.description(), request.archived());
        return ProjectResponse.from(updated);
    }
}

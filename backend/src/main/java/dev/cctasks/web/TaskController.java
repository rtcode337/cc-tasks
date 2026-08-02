package dev.cctasks.web;

import java.util.List;

import dev.cctasks.task.Task;
import dev.cctasks.task.TaskPage;
import dev.cctasks.task.TaskService;
import dev.cctasks.task.TaskStatus;
import dev.cctasks.task.TaskTransferService;
import dev.cctasks.web.Dtos.PagedResponse;
import dev.cctasks.web.Dtos.CreateTaskRequest;
import dev.cctasks.web.Dtos.ReorderTasksRequest;
import dev.cctasks.web.Dtos.TaskDetailResponse;
import dev.cctasks.web.Dtos.TaskResponse;
import dev.cctasks.web.Dtos.UpdateTaskRequest;
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
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;
    private final TaskTransferService transferService;

    public TaskController(TaskService taskService, TaskTransferService transferService) {
        this.taskService = taskService;
        this.transferService = transferService;
    }

    /**
     * 未完了タスクの書き出し(プロジェクト名・リポジトリ付き)。
     * 返した本文をそのまま {@code POST /import} に渡せる。
     */
    @GetMapping("/export")
    public TaskTransferService.Export export() {
        return transferService.export();
    }

    /**
     * 書き出したものの読み込み。プロジェクトは名前で照合し、無ければ作る。
     * dryRun=true なら書き込まず、作る/飛ばす予定だけ返す。
     */
    @PostMapping("/import")
    public TaskTransferService.ImportResult importTasks(
            @RequestBody TaskTransferService.Export data,
            @RequestParam(name = "dryRun", required = false, defaultValue = "false") boolean dryRun) {
        return transferService.importTasks(data, dryRun);
    }

    /**
     * タスク一覧。
     * <ul>
     *   <li>{@code done=true} … 完了タスクをページングで返す({@code page}/{@code size})</li>
     *   <li>{@code done=false} … 未完了(done 以外)を返す</li>
     *   <li>いずれも無指定 … {@code status} で絞り込んだ全件(従来どおり)</li>
     * </ul>
     * done=true のときだけ返却形が {@link Dtos.PagedResponse} になる。
     */
    @GetMapping
    public Object list(
            @RequestParam(name = "projectId", required = false) Long projectId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "done", required = false) Boolean done,
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "10") int size) {

        if (Boolean.TRUE.equals(done)) {
            TaskPage pageResult = taskService.listDone(projectId, page, size);
            return new PagedResponse<>(
                    pageResult.items().stream().map(TaskResponse::from).toList(),
                    pageResult.total(), pageResult.page(), pageResult.size());
        }
        if (Boolean.FALSE.equals(done)) {
            return taskService.listActive(projectId).stream().map(TaskResponse::from).toList();
        }
        TaskStatus parsed = (status == null || status.isBlank()) ? null : TaskStatus.from(status);
        return taskService.search(projectId, parsed).stream().map(TaskResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(@Valid @RequestBody CreateTaskRequest request) {
        Task created = taskService.create(request.projectId(), request.title(), request.status());
        return TaskResponse.from(created);
    }

    /**
     * プロジェクト内の並び替え。ids を望む順で送ると、並び替えた分を返す。
     * projectId は未紐づけのかたまりなら null。
     */
    @PutMapping("/order")
    public List<TaskResponse> reorder(@Valid @RequestBody ReorderTasksRequest request) {
        return taskService.reorder(request.projectId(), request.ids()).stream()
                .map(TaskResponse::from).toList();
    }

    @GetMapping("/{id}")
    public TaskDetailResponse get(@PathVariable long id) {
        return TaskDetailResponse.from(taskService.detail(id));
    }

    @PatchMapping("/{id}")
    public TaskResponse update(@PathVariable long id, @RequestBody UpdateTaskRequest request) {
        Task updated = taskService.update(id, request.projectId(), request.title(), request.status());
        return TaskResponse.from(updated);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        taskService.delete(id);
    }

}

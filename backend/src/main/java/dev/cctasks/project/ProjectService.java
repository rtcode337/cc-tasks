package dev.cctasks.project;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import dev.cctasks.task.TaskRepository;
import dev.cctasks.web.ApiException;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ProjectService {

    private final ProjectRepository repository;
    // アーカイブの可否判定と、削除時の巻き添え削除に使う。
    // TaskService ではなくリポジトリを直接見て bean の循環を避ける
    private final TaskRepository taskRepository;
    private final Clock clock;

    public ProjectService(ProjectRepository repository, TaskRepository taskRepository, Clock clock) {
        this.repository = repository;
        this.taskRepository = taskRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Project> list(Boolean archived) {
        if (archived == null) {
            return repository.findAllOrdered();
        }
        return repository.findByArchivedOrdered(archived);
    }

    @Transactional(readOnly = true)
    public Project requireById(long id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("プロジェクトが見つかりません: id=" + id));
    }

    @Transactional
    public Project create(String name, List<String> repoUrls, String description) {
        String trimmedName = requireName(name);
        requireNameAvailable(trimmedName, null);
        Instant now = now();
        try {
            // 新規プロジェクトは並びの末尾に足す
            return repository.save(new Project(null, trimmedName, joinRepoUrls(repoUrls),
                    blankToNull(description), false, repository.maxSortOrder() + 1, now, now));
        }
        catch (DuplicateKeyException ex) {
            throw ApiException.conflict("同名のプロジェクトが既にあります: " + trimmedName);
        }
    }

    /**
     * null のフィールドは「変更しない」を意味する部分更新。
     * repoUrls は空リストで「全部消す」。
     *
     * <p>アーカイブは **未完了タスクが 0 件のときだけ**通す。片付いていないタスクごと
     * 一覧から消えると、放り込んだものを取りこぼすため(戻すのはいつでもよい)。
     */
    @Transactional
    public Project update(long id, String name, List<String> repoUrls, String description, Boolean archived) {
        Project current = requireById(id);
        if (name != null) {
            requireNameAvailable(requireName(name), current.id());
        }
        if (Boolean.TRUE.equals(archived) && !current.archived()) {
            long incomplete = taskRepository.countIncompleteByProjectId(current.id());
            if (incomplete > 0) {
                throw ApiException.badRequest(
                        "未完了のタスクが %d 件あるためアーカイブできません: %s".formatted(incomplete, current.name()));
            }
        }
        Project updated = new Project(
                current.id(),
                name != null ? requireName(name) : current.name(),
                repoUrls != null ? joinRepoUrls(repoUrls) : current.repoUrls(),
                description != null ? blankToNull(description) : current.description(),
                archived != null ? archived : current.archived(),
                current.sortOrder(),
                current.createdAt(),
                now());
        try {
            return repository.save(updated);
        }
        catch (DuplicateKeyException ex) {
            throw ApiException.conflict("同名のプロジェクトが既にあります: " + updated.name());
        }
    }

    /**
     * アーカイブ済みのプロジェクトを、紐づくタスクごと消す。
     *
     * <p>アーカイブしていないものは消せない —— アーカイブ自体が「未完了 0 件」を条件にしているので、
     * 片付いたことを確かめる一段を必ず通させるため。戻せない操作なので入口はここだけにする。
     */
    @Transactional
    public void delete(long id) {
        Project current = requireById(id);
        if (!current.archived()) {
            throw ApiException.badRequest(
                    "アーカイブしてからでないと削除できません: " + current.name());
        }
        taskRepository.deleteByProjectId(current.id());
        repository.delete(current);
    }

    /**
     * 並び替え。ids は全プロジェクト(アーカイブ含む)の id を望む順で過不足なく指定する。
     * 先頭から 1, 2, 3, … と sort_order を振り直す。
     */
    @Transactional
    public List<Project> reorder(List<Long> ids) {
        List<Project> all = repository.findAllOrdered();
        Set<Long> existingIds = all.stream().map(Project::id).collect(Collectors.toSet());
        if (ids == null || ids.size() != existingIds.size() || !existingIds.equals(new HashSet<>(ids))) {
            throw ApiException.badRequest("ids には全プロジェクトの id を過不足なく指定してください");
        }
        for (int i = 0; i < ids.size(); i++) {
            repository.updateSortOrder(ids.get(i), i + 1);
        }
        return repository.findAllOrdered();
    }

    /** name は UNIQUE。自分自身との衝突は無視する。 */
    private void requireNameAvailable(String name, Long selfId) {
        repository.findByName(name)
                .filter(existing -> !existing.id().equals(selfId))
                .ifPresent(existing -> {
                    throw ApiException.conflict("同名のプロジェクトが既にあります: " + name);
                });
    }

    private String requireName(String name) {
        if (!StringUtils.hasText(name)) {
            throw ApiException.badRequest("name は必須です");
        }
        return name.trim();
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    /** URL リストを DB 保存用の改行区切りに畳む。空要素は捨て、重複は先勝ち。null・空なら null。 */
    private static String joinRepoUrls(List<String> urls) {
        if (urls == null) {
            return null;
        }
        List<String> cleaned = urls.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        return cleaned.isEmpty() ? null : String.join("\n", cleaned);
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}

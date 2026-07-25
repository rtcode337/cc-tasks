package dev.cctasks.project;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import dev.cctasks.web.ApiException;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ProjectService {

    private final ProjectRepository repository;
    private final Clock clock;

    public ProjectService(ProjectRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Project> list(Boolean archived) {
        if (archived == null) {
            return repository.findAllOrderByName();
        }
        return repository.findByArchivedOrderByName(archived);
    }

    @Transactional(readOnly = true)
    public Project requireById(long id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("プロジェクトが見つかりません: id=" + id));
    }

    @Transactional(readOnly = true)
    public Project requireByName(String name) {
        return repository.findByName(name)
                .orElseThrow(() -> ApiException.notFound("プロジェクトが見つかりません: name=" + name));
    }

    @Transactional
    public Project create(String name, String repoUrl, String description) {
        String trimmedName = requireName(name);
        requireNameAvailable(trimmedName, null);
        Instant now = now();
        try {
            return repository.save(new Project(null, trimmedName, blankToNull(repoUrl),
                    blankToNull(description), false, now, now));
        }
        catch (DuplicateKeyException ex) {
            throw ApiException.conflict("同名のプロジェクトが既にあります: " + trimmedName);
        }
    }

    /**
     * null のフィールドは「変更しない」を意味する部分更新。
     */
    @Transactional
    public Project update(long id, String name, String repoUrl, String description, Boolean archived) {
        Project current = requireById(id);
        if (name != null) {
            requireNameAvailable(requireName(name), current.id());
        }
        Project updated = new Project(
                current.id(),
                name != null ? requireName(name) : current.name(),
                repoUrl != null ? blankToNull(repoUrl) : current.repoUrl(),
                description != null ? blankToNull(description) : current.description(),
                archived != null ? archived : current.archived(),
                current.createdAt(),
                now());
        try {
            return repository.save(updated);
        }
        catch (DuplicateKeyException ex) {
            throw ApiException.conflict("同名のプロジェクトが既にあります: " + updated.name());
        }
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

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}

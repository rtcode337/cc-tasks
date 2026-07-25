package dev.cctasks.project;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * プロジェクト(リポジトリ)。タスクの所属先。
 */
@Table("projects")
public record Project(
        @Id Long id,
        String name,
        String repoUrl,
        String description,
        boolean archived,
        Instant createdAt,
        Instant updatedAt) {

    public Project withId(Long newId) {
        return new Project(newId, name, repoUrl, description, archived, createdAt, updatedAt);
    }
}

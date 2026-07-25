package dev.cctasks.project;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * プロジェクト(リポジトリ)。タスクの所属先。
 *
 * <p>リポジトリ URL は複数持てる。DB 上は {@code repo_url} 列 1 本に改行区切りで保持し
 * (マイグレーションツールを入れていないため列は増やさない)、入出力はリストで扱う。
 */
@Table("projects")
public record Project(
        @Id Long id,
        String name,
        @Column("repo_url") String repoUrls,
        String description,
        boolean archived,
        Instant createdAt,
        Instant updatedAt) {

    public Project withId(Long newId) {
        return new Project(newId, name, repoUrls, description, archived, createdAt, updatedAt);
    }

    /** 改行区切りの {@code repoUrls} をリストに展開する。未設定なら空リスト。 */
    public List<String> repoUrlList() {
        if (repoUrls == null || repoUrls.isBlank()) {
            return List.of();
        }
        return Arrays.stream(repoUrls.split("\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}

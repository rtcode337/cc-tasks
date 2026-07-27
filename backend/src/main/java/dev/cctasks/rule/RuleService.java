package dev.cctasks.rule;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import dev.cctasks.web.ApiException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class RuleService {

    private final RuleRepository repository;
    private final Clock clock;

    public RuleService(RuleRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Rule> list() {
        return repository.findAllOrdered();
    }

    @Transactional(readOnly = true)
    public Rule requireById(long id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("ルールが見つかりません: id=" + id));
    }

    /**
     * 有効なルールを表示順に 1 本の Markdown へ連結する。
     * 各ルールは {@code ## <title>} の見出しを付けて並べる —— 貼り付け先で
     * どこからどこまでが 1 ルールかを読み手(と Claude)が判別できるようにするため。
     * 有効なルールが 1 本も無ければ空文字を返す。
     */
    @Transactional(readOnly = true)
    public String combined() {
        return repository.findAllOrdered().stream()
                .filter(Rule::enabled)
                .map(rule -> "## " + rule.title() + "\n\n" + rule.body().strip() + "\n")
                .collect(Collectors.joining("\n"));
    }

    @Transactional
    public Rule create(String title, String body, Boolean enabled) {
        Instant now = now();
        // 新規は並びの末尾に足す
        return repository.save(new Rule(null, requireTitle(title), requireBody(body),
                enabled == null || enabled, repository.maxSortOrder() + 1, now, now));
    }

    /** null のフィールドは「変更しない」を意味する部分更新。 */
    @Transactional
    public Rule update(long id, String title, String body, Boolean enabled) {
        Rule current = requireById(id);
        return repository.save(new Rule(
                current.id(),
                title != null ? requireTitle(title) : current.title(),
                body != null ? requireBody(body) : current.body(),
                enabled != null ? enabled : current.enabled(),
                current.sortOrder(),
                current.createdAt(),
                now()));
    }

    @Transactional
    public void delete(long id) {
        repository.deleteById(requireById(id).id());
    }

    /**
     * 並び替え。ids は全ルールの id を望む順で過不足なく指定する。
     * 先頭から 1, 2, 3, … と sort_order を振り直す(プロジェクトの並び替えと同じ方式)。
     */
    @Transactional
    public List<Rule> reorder(List<Long> ids) {
        Set<Long> existingIds = repository.findAllOrdered().stream()
                .map(Rule::id).collect(Collectors.toSet());
        if (ids == null || ids.size() != existingIds.size() || !existingIds.equals(new HashSet<>(ids))) {
            throw ApiException.badRequest("ids には全ルールの id を過不足なく指定してください");
        }
        for (int i = 0; i < ids.size(); i++) {
            repository.updateSortOrder(ids.get(i), i + 1);
        }
        return repository.findAllOrdered();
    }

    private static String requireTitle(String title) {
        if (!StringUtils.hasText(title)) {
            throw ApiException.badRequest("title は必須です");
        }
        return title.trim();
    }

    private static String requireBody(String body) {
        if (!StringUtils.hasText(body)) {
            throw ApiException.badRequest("body は必須です");
        }
        return body.strip();
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}

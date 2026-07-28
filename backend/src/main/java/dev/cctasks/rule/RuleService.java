package dev.cctasks.rule;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import dev.cctasks.setting.SettingRepository;
import dev.cctasks.web.ApiException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class RuleService {

    /** settings テーブルのキー。規約リポジトリ(連結ルールを CLAUDE.md として置く先)の URL/スラッグ */
    static final String RULES_REPO_URL_KEY = "rules_repo_url";

    /**
     * 連結の先頭に自動で付ける前置き。CLAUDE.md の中身は普通「そのリポジトリ自身の説明」として
     * 読まれるため、貼り先がどこであれ「作業対象のすべてのリポジトリに効く共通ルール」だと
     * 明示する。ルールとして登録させないのは、貼り替えのたびに消えたり、並び替えで
     * 先頭から動いたりしないようにするため。
     */
    public static final String COMBINED_PREAMBLE = """
            # 共通ルール

            以下は特定リポジトリの説明ではなく、作業対象のすべてのリポジトリに適用する共通ルール。
            """;

    /**
     * 前置きの直後に自動で付ける「規約リポジトリ自体の扱い」ルール。
     * 規約リポジトリは各ユーザーが自分用に作る配布専用のプライベートリポジトリで、
     * 育てる対象ではない。セッションにサブリポジトリとして含まれるため、放っておくと
     * タスクのついでに CLAUDE.md を書き換えられかねない —— それを止め、更新は
     * ユーザーが更新後の Markdown を明示的に渡したときに限ることを Claude Code に伝える
     * (URL からの取得は書かない。/api は要ログインで、セッションからは取得できないため)。
     * 自動付与にする理由は前置きと同じ。
     */
    public static final String COMBINED_REPO_RULE = """
            ## 規約リポジトリの扱い

            この CLAUDE.md が置かれているリポジトリ(規約リポジトリ)は、共通ルールを
            Claude Code のセッションに読み込ませるための置き場であって、開発対象ではない。
            読み取り専用として扱い、**自動では更新しない**(タスクのついでにこの CLAUDE.md を
            直したり、このリポジトリへコミット・push したりしない)。
            更新するのは、ユーザーが更新後の Markdown を渡して「この内容で規約を更新して」と
            明示的に指示したときだけ。そのときは渡された内容で CLAUDE.md を丸ごと置き換える。
            """;

    private final RuleRepository repository;
    private final SettingRepository settings;
    private final Clock clock;

    public RuleService(RuleRepository repository, SettingRepository settings, Clock clock) {
        this.repository = repository;
        this.settings = settings;
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
     * 先頭に {@link #COMBINED_PREAMBLE}(適用範囲の前置き)と
     * {@link #COMBINED_REPO_RULE}(規約リポジトリ自体は自動更新しない)を付け、
     * 各ルールは {@code ## <title>} の見出しを付けて並べる —— 貼り付け先で
     * どこからどこまでが 1 ルールかを読み手(と Claude)が判別できるようにするため。
     * 有効なルールが 1 本も無ければ前置きも付けず空文字を返す。
     */
    @Transactional(readOnly = true)
    public String combined() {
        String rules = repository.findAllOrdered().stream()
                .filter(Rule::enabled)
                .map(rule -> "## " + rule.title() + "\n\n" + rule.body().strip() + "\n")
                .collect(Collectors.joining("\n"));
        return rules.isEmpty() ? "" : COMBINED_PREAMBLE + "\n" + COMBINED_REPO_RULE + "\n" + rules;
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

    /** 規約リポジトリ。未設定なら null。 */
    @Transactional(readOnly = true)
    public String rulesRepoUrl() {
        return settings.find(RULES_REPO_URL_KEY).orElse(null);
    }

    /**
     * 規約リポジトリの更新。PATCH の規約どおり null は「変更しない」、
     * 空文字(空白のみ含む)は「消す」。更新後の値を返す。
     */
    @Transactional
    public String updateRulesRepoUrl(String value) {
        if (value != null) {
            String trimmed = value.trim();
            if (trimmed.isEmpty()) {
                settings.delete(RULES_REPO_URL_KEY);
            } else {
                settings.upsert(RULES_REPO_URL_KEY, trimmed, now());
            }
        }
        return rulesRepoUrl();
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

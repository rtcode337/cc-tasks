package dev.cctasks.rule;

import java.util.ArrayList;
import java.util.List;

/**
 * 連結ルール({@link RuleService#combined()} の出力)を個々のルールへ戻すパーサ。
 *
 * <p>連結の逆変換なので、規則は {@code combined()} と対になっている:
 * <ul>
 *   <li>{@code ## <title>} の見出しで 1 本に区切る。見出しの下から次の見出しの手前までが本文</li>
 *   <li>最初の見出しより前(前置き {@code # 共通ルール} と適用範囲の一文)は捨てる。
 *       連結時に自動で付くものなので、ルールとして取り込むと貼り替えのたびに増える</li>
 *   <li>同じ理由で「規約リポジトリの扱い」({@link RuleService#COMBINED_REPO_RULE})も捨てる</li>
 * </ul>
 *
 * <p>見出しの判定は**コードブロックの外だけ**で行う。ルール本文にはシェルの例が入ることがあり、
 * フェンス内の {@code ## …} をコメントではなく見出しと解釈すると、そこでルールが分断される。
 */
public final class RuleMarkdownParser {

    /** 取り込む 1 本。id や並び順はまだ持たない。 */
    public record ParsedRule(String title, String body) {
    }

    /**
     * 連結時に自動で付くルールの見出し。取り込みでは捨てる。
     * {@link RuleService#COMBINED_REPO_RULE} 自身の 1 行目から取る —— 同じ文字列を
     * 2 箇所に書くと、片方だけ直したときに黙って二重取り込みになるため。
     */
    static final String AUTO_ADDED_TITLE = headingOf(RuleService.COMBINED_REPO_RULE);

    private RuleMarkdownParser() {
    }

    /**
     * 見出しごとに切り出す。取り込めるものが無ければ空リスト。
     * 本文が空の見出しは落とす(見出しだけのルールは連結しても意味を成さないため)。
     */
    public static List<ParsedRule> parse(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }
        List<ParsedRule> parsed = new ArrayList<>();
        String title = null;
        StringBuilder body = new StringBuilder();
        // コードフェンスの状態。開いた記号(``` か ~~~)と長さを覚えておき、
        // 同じ記号で同じ長さ以上の行が来るまでは中身として扱う
        char fenceChar = 0;
        int fenceLength = 0;

        for (String line : markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            String stripped = line.strip();
            if (fenceChar != 0) {
                if (fenceLengthOf(stripped, fenceChar) >= fenceLength) {
                    fenceChar = 0;
                }
            } else if (isFenceOpen(stripped)) {
                fenceChar = stripped.charAt(0);
                fenceLength = fenceLengthOf(stripped, fenceChar);
            } else if (isHeading(stripped)) {
                add(parsed, title, body);
                title = stripped.substring(3).strip();
                body.setLength(0);
                continue;
            }
            // 見出しより前(前置き)は捨てる
            if (title != null) {
                body.append(line).append('\n');
            }
        }
        add(parsed, title, body);
        return List.copyOf(parsed);
    }

    private static void add(List<ParsedRule> parsed, String title, StringBuilder body) {
        if (title == null || title.isEmpty() || title.equals(AUTO_ADDED_TITLE)) {
            return;
        }
        String text = body.toString().strip();
        if (!text.isEmpty()) {
            parsed.add(new ParsedRule(title, text));
        }
    }

    /** {@code ## } で始まる行だけ。{@code ### } は本文の一部として残す。 */
    private static boolean isHeading(String stripped) {
        return stripped.startsWith("## ");
    }

    private static boolean isFenceOpen(String stripped) {
        return fenceLengthOf(stripped, '`') >= 3 || fenceLengthOf(stripped, '~') >= 3;
    }

    /** 行頭に fence 文字が何個続くか。0 なら fence ではない。 */
    private static int fenceLengthOf(String stripped, char fence) {
        int i = 0;
        while (i < stripped.length() && stripped.charAt(i) == fence) {
            i++;
        }
        return i;
    }

    /** Markdown の先頭にある {@code ## } 見出しの文字列。 */
    private static String headingOf(String markdown) {
        return markdown.lines()
                .filter(line -> line.startsWith("## "))
                .findFirst()
                .map(line -> line.substring(3).strip())
                .orElseThrow(() -> new IllegalStateException("見出しのない自動付与ルール: " + markdown));
    }
}

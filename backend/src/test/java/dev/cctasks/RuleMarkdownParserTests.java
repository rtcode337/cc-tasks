package dev.cctasks;

import java.util.List;

import dev.cctasks.rule.RuleMarkdownParser;
import dev.cctasks.rule.RuleMarkdownParser.ParsedRule;
import dev.cctasks.rule.RuleService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * {@link RuleMarkdownParser} が連結ルールを個々のルールへ戻せることの検証。
 * 連結({@link RuleService#combined()})の逆変換なので、往復して元に戻るかが要。
 */
class RuleMarkdownParserTests {

    @Test
    void 見出しごとに切り出し_前置きと自動付与ルールは捨てる() {
        String markdown = RuleService.COMBINED_PREAMBLE + "\n" + RuleService.COMBINED_REPO_RULE + "\n"
                + """
                ## 日本語で書く

                ユーザーへの応答はすべて日本語。

                ## コミットの作法

                確認を取るのは commit の前。
                """;

        List<ParsedRule> parsed = RuleMarkdownParser.parse(markdown);

        assertThat(parsed)
                .extracting(ParsedRule::title, ParsedRule::body)
                .containsExactly(
                        tuple("日本語で書く", "ユーザーへの応答はすべて日本語。"),
                        tuple("コミットの作法", "確認を取るのは commit の前。"));
    }

    @Test
    void コードブロック内の見出しに見える行では区切らない() {
        String markdown = """
                ## シェルの例

                ```bash
                ## これはコメントであって見出しではない
                echo hi
                ```

                フェンスを閉じたあとの本文。
                """;

        List<ParsedRule> parsed = RuleMarkdownParser.parse(markdown);

        assertThat(parsed).hasSize(1);
        assertThat(parsed.getFirst().title()).isEqualTo("シェルの例");
        assertThat(parsed.getFirst().body())
                .contains("## これはコメントであって見出しではない")
                .contains("フェンスを閉じたあとの本文。");
    }

    @Test
    void 見出しの階層は本文として残す() {
        String markdown = """
                ## 外部データを取り込むとき

                ### 収録ソース

                本文。
                """;

        List<ParsedRule> parsed = RuleMarkdownParser.parse(markdown);

        assertThat(parsed).hasSize(1);
        assertThat(parsed.getFirst().body()).startsWith("### 収録ソース");
    }

    @Test
    void 本文が空の見出しは落とす() {
        List<ParsedRule> parsed = RuleMarkdownParser.parse("""
                ## 見出しだけ

                ## 中身のあるルール

                本文。
                """);

        assertThat(parsed).extracting(ParsedRule::title).containsExactly("中身のあるルール");
    }

    @Test
    void 見出しが無ければ何も取り込まない() {
        assertThat(RuleMarkdownParser.parse("ただの文章。\n\n# 見出し 1 だけ")).isEmpty();
        assertThat(RuleMarkdownParser.parse("")).isEmpty();
        assertThat(RuleMarkdownParser.parse(null)).isEmpty();
    }

    @Test
    void CRLF_で貼り付けても行末が本文に残らない() {
        List<ParsedRule> parsed = RuleMarkdownParser.parse("## 見出し\r\n\r\n本文の 1 行目\r\n本文の 2 行目\r\n");

        assertThat(parsed).hasSize(1);
        assertThat(parsed.getFirst().body()).isEqualTo("本文の 1 行目\n本文の 2 行目");
    }
}

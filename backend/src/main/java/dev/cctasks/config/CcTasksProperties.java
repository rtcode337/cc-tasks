package dev.cctasks.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * アプリ固有の設定 (仕様書 §9 の環境変数一覧に対応)。
 */
@ConfigurationProperties(prefix = "cctasks")
public record CcTasksProperties(
        /** ログインを許可する Google アカウントのメール (1 件)。 */
        String allowedEmail,
        /** MCP 用の静的 API キー。 */
        String mcpApiKey,
        /** 公開 URL (OAuth リダイレクト構築用)。 */
        String publicBaseUrl,
        RateLimit rateLimit) {

    public CcTasksProperties {
        rateLimit = rateLimit != null ? rateLimit : new RateLimit(20);
    }

    public record RateLimit(int requestsPerMinute) {

        public RateLimit {
            requestsPerMinute = requestsPerMinute > 0 ? requestsPerMinute : 20;
        }
    }
}

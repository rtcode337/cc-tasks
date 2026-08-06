package dev.cctasks.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * アプリ固有の設定 (仕様書 §9 の環境変数一覧に対応)。
 */
@ConfigurationProperties(prefix = "cctasks")
public record CcTasksProperties(
        /** ログインを許可する Google アカウントのメール (1 件)。 */
        String allowedEmail,
        /** 公開 URL (OAuth リダイレクト構築用)。 */
        String publicBaseUrl,
        /**
         * redirect_uri の組み立てに使ってよいホスト (ポート込み。カンマ区切りで複数)。
         * 未設定なら Host / X-Forwarded-Host からの導出を行わない
         * ({@code publicBaseUrl} か Spring 既定の {@code {baseUrl}} に委ねる)。
         */
        List<String> allowedRedirectHosts,
        RateLimit rateLimit) {

    public CcTasksProperties {
        rateLimit = rateLimit != null ? rateLimit : new RateLimit(20);
        allowedRedirectHosts = allowedRedirectHosts != null ? List.copyOf(allowedRedirectHosts) : List.of();
    }

    public record RateLimit(int requestsPerMinute) {

        public RateLimit {
            requestsPerMinute = requestsPerMinute > 0 ? requestsPerMinute : 20;
        }
    }
}

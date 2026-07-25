package dev.cctasks.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

/**
 * GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET が設定されているときだけ
 * Google の ClientRegistration を組み立てる。
 *
 * <p>{@code spring.security.oauth2.client.registration.google.client-id} を
 * 空文字で置いておくと Spring Boot が起動時に検証エラーで落ちるため、
 * 「未設定なら登録そのものを作らない」形にしている
 * (M1〜M3 の段階では OAuth 抜きで起動できる必要がある)。
 */
public class GoogleOAuthEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String PREFIX = "spring.security.oauth2.client.registration.google.";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String clientId = environment.getProperty("GOOGLE_CLIENT_ID");
        String clientSecret = environment.getProperty("GOOGLE_CLIENT_SECRET");
        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(clientSecret)) {
            return;
        }

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(PREFIX + "client-id", clientId);
        properties.put(PREFIX + "client-secret", clientSecret);
        properties.put(PREFIX + "provider", "google");
        properties.put(PREFIX + "scope", "openid,email,profile");
        properties.put(PREFIX + "client-name", "Google");

        // PUBLIC_BASE_URL は任意。設定されていればそれを基点にリダイレクト URI を組む。
        // 未設定なら Spring 既定の {baseUrl}(= ForwardedHeaderFilter 適用後の
        // リクエストのスキーム/ホスト)に任せる。プロキシが X-Forwarded-Proto /
        // X-Forwarded-Host を送っていれば未設定で問題ない。
        // (.mcp.json の ${PUBLIC_BASE_URL} はクライアント側で展開される別物)
        String publicBaseUrl = environment.getProperty("PUBLIC_BASE_URL");
        String redirectBase = StringUtils.hasText(publicBaseUrl)
                ? trimTrailingSlash(publicBaseUrl)
                : "{baseUrl}";
        properties.put(PREFIX + "redirect-uri", redirectBase + "/login/oauth2/code/google");

        environment.getPropertySources()
                .addLast(new MapPropertySource("cctasks-google-oauth", properties));
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}

package dev.cctasks.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.util.StringUtils;

/**
 * 仕様書 §6 の実装。
 *
 * <ul>
 *   <li>{@code /api/**} … Google OAuth セッション必須。未認証は 401 (JSON)。
 *       フロントが 401 を見てログイン画面へ誘導する</li>
 *   <li>それ以外 … SPA シェルの配信。ログインは {@code /oauth2/authorization/google} へのリンク</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private static final String CSP =
            "default-src 'self'; "
            + "script-src 'self'; "
            + "style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data: https://lh3.googleusercontent.com; "
            + "font-src 'self' data:; "
            + "connect-src 'self'; "
            + "manifest-src 'self'; "
            + "worker-src 'self'; "
            + "object-src 'none'; "
            + "base-uri 'self'; "
            + "form-action 'self' https://accounts.google.com; "
            + "frame-ancestors 'none'";

    /**
     * PWA(人間)側。Google OAuth のクレデンシャルが設定されていない場合は
     * oauth2Login を組み立てられないため、/api を 401 のままにして起動だけは通す。
     */
    @Bean
    @Order(1)
    @Profile("!dev")
    SecurityFilterChain appFilterChain(HttpSecurity http, CcTasksProperties properties,
            IpRateLimiter rateLimiter,
            ObjectProvider<ClientRegistrationRepository> clientRegistrations) throws Exception {

        // SPA は Cookie の XSRF-TOKEN を読んで X-XSRF-TOKEN ヘッダで送り返す
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null);

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfHandler))
                .headers(SecurityConfig::commonHeaders)
                .authorizeHttpRequests(auth -> auth
                        // データは /api で守る。SPA シェルと PWA 資材は誰でも取得可
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        // CSRF トークン欠落などもエラー形式を揃える
                        .accessDeniedHandler((request, response, denied) ->
                                writeError(response, HttpStatus.FORBIDDEN.value(), "forbidden",
                                        "このリクエストは許可されていません")))
                // 認可リクエストのリダイレクトは UsernamePasswordAuthenticationFilter より
                // 手前で起きるので、その更に手前に差し込まないと素通りする
                .addFilterBefore(new LoginRateLimitFilter(rateLimiter),
                        OAuth2AuthorizationRequestRedirectFilter.class);

        if (clientRegistrations.getIfAvailable() == null) {
            log.error("GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET が未設定のためログインを無効化しました。"
                    + " /api は 401 を返し続けます");
            return http.build();
        }

        // リダイレクト URI のポートは X-Forwarded-Host(ポート込み)から保持する。
        // PUBLIC_BASE_URL 設定時はテンプレートが絶対 URL なので書き換えは無効化される。
        // ヘッダ由来のホストは ALLOWED_REDIRECT_HOSTS の許可リストでのみ採用する。
        if (!StringUtils.hasText(properties.publicBaseUrl()) && properties.allowedRedirectHosts().isEmpty()) {
            log.warn("PUBLIC_BASE_URL も ALLOWED_REDIRECT_HOSTS も未設定です。"
                    + " リダイレクト URI はリクエストからの自動導出を行わず {baseUrl} 展開のみになります"
                    + "(非標準ポートやリバースプロキシ配下ではログインに失敗する可能性があります)。"
                    + " どちらかを設定してください");
        }
        ForwardedRedirectUriResolver redirectUriResolver = new ForwardedRedirectUriResolver(
                clientRegistrations.getIfAvailable(),
                StringUtils.hasText(properties.publicBaseUrl()),
                properties.allowedRedirectHosts());

        return http
                .oauth2Login(oauth -> oauth
                        .authorizationEndpoint(authz -> authz
                                .authorizationRequestResolver(redirectUriResolver))
                        .userInfoEndpoint(userInfo -> userInfo
                                .oidcUserService(AllowedEmailUserServices.oidc(properties.allowedEmail()))
                                .userService(AllowedEmailUserServices.oauth2(properties.allowedEmail())))
                        .defaultSuccessUrl("/", true)
                        // 許可メール以外は 403 で即拒否 (仕様書 §6.1)
                        .failureHandler((request, response, exception) ->
                                writeError(response, HttpStatus.FORBIDDEN.value(), "forbidden",
                                        "このアカウントではログインできません")))
                .logout(logout -> logout
                        .logoutUrl("/api/logout")
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpServletResponse.SC_NO_CONTENT))
                        .deleteCookies("CCTASKSSESSION"))
                .build();
    }

    /**
     * ローカル開発 (M1/M2) 用。Google OAuth を通さずに REST を叩けるようにする。
     * 本番では有効化しないこと。
     */
    @Bean
    @Order(1)
    @Profile("dev")
    SecurityFilterChain devFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }

    private static void commonHeaders(HeadersConfigurer<HttpSecurity> headers) {
        // X-Content-Type-Options: nosniff は Spring Security の既定で付与される
        headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(CSP))
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.SAME_ORIGIN))
                .httpStrictTransportSecurity(hsts -> hsts
                        .includeSubDomains(true)
                        .maxAgeInSeconds(31_536_000));
    }

    private static void writeError(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                "{\"error\":{\"code\":\"%s\",\"message\":\"%s\"}}".formatted(code, message));
    }
}

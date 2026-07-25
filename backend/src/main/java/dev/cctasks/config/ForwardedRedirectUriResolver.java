package dev.cctasks.config;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * OAuth のリダイレクト URI のスキーム/ホスト/ポートを、リバースプロキシが付ける
 * {@code X-Forwarded-Proto} / {@code X-Forwarded-Host}(ポートを含む)から直接組み立てる。
 *
 * <p>背景: Tomcat の {@code RemoteIpValve}(= {@code forward-headers-strategy: native})は
 * {@code X-Forwarded-Proto: https} を見つけると、{@code X-Forwarded-Port} が無い限りポートを
 * 443 に固定する。このため非標準ポートで公開していると {@code {baseUrl}} 展開時にポートが落ち、
 * 生成される {@code redirect_uri} が Google 側の登録値と食い違ってログインが弾かれる。
 * ここで {@code X-Forwarded-Host}(ポート込み)を直読みしてポートを保持する。
 * これは travel-log の {@code getExternalOrigin} と同じ導出で、{@code PUBLIC_BASE_URL} を
 * 設定しなくても正しい {@code redirect_uri} になるようにするためのもの。
 *
 * <p>{@code PUBLIC_BASE_URL} が明示設定されているときは {@code redirect-uri} テンプレートが
 * その絶対 URL になっている(= 設定を尊重する)ので、この書き換えは行わない。
 */
public class ForwardedRedirectUriResolver implements OAuth2AuthorizationRequestResolver {

    private final DefaultOAuth2AuthorizationRequestResolver delegate;
    private final boolean publicBaseUrlConfigured;

    public ForwardedRedirectUriResolver(ClientRegistrationRepository clientRegistrationRepository,
            boolean publicBaseUrlConfigured) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepository,
                OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
        this.publicBaseUrlConfigured = publicBaseUrlConfigured;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return rewrite(delegate.resolve(request), request);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return rewrite(delegate.resolve(request, clientRegistrationId), request);
    }

    /**
     * {@code X-Forwarded-Host} から組んだ origin で redirect_uri のスキーム/ホスト/ポートだけを差し替える。
     * パス({@code /login/oauth2/code/google})はそのまま維持する。
     * {@code OAuth2AuthorizationRequest.from(...)} は {@code authorizationRequestUri} を引き継がないため、
     * {@code build()} 時に新しい redirect_uri を含めて Google 認可 URL が再生成される。
     */
    private OAuth2AuthorizationRequest rewrite(OAuth2AuthorizationRequest authorizationRequest,
            HttpServletRequest request) {
        if (authorizationRequest == null || publicBaseUrlConfigured) {
            return authorizationRequest;
        }
        String externalOrigin = externalOrigin(request);
        if (externalOrigin == null) {
            return authorizationRequest;
        }
        UriComponents current = UriComponentsBuilder
                .fromUriString(authorizationRequest.getRedirectUri()).build();
        String redirectUri = UriComponentsBuilder.fromUriString(externalOrigin)
                .path(current.getPath())
                .query(current.getQuery())
                .build()
                .toUriString();
        if (redirectUri.equals(authorizationRequest.getRedirectUri())) {
            return authorizationRequest;
        }
        return OAuth2AuthorizationRequest.from(authorizationRequest)
                .redirectUri(redirectUri)
                .build();
    }

    /**
     * X-Forwarded-Proto と X-Forwarded-Host(ポート込み)が揃っていればそれを優先。
     * どちらか欠けていれば {@code null} を返し、既定の {@code {baseUrl}} 展開に委ねる。
     */
    private static String externalOrigin(HttpServletRequest request) {
        String proto = firstValue(request.getHeader("X-Forwarded-Proto"));
        String host = firstValue(request.getHeader("X-Forwarded-Host"));
        if (!StringUtils.hasText(proto) || !StringUtils.hasText(host)) {
            return null;
        }
        return proto + "://" + host;
    }

    /** {@code "https, http"} のようにカンマ区切りで複数来た場合は最初の値を採る。 */
    private static String firstValue(String headerValue) {
        if (headerValue == null) {
            return null;
        }
        int comma = headerValue.indexOf(',');
        return (comma >= 0 ? headerValue.substring(0, comma) : headerValue).trim();
    }
}

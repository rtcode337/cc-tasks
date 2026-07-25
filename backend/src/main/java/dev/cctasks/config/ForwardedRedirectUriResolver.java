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
 * OAuth のリダイレクト URI のスキーム/ホスト/ポートを、リバースプロキシ配下でも
 * 「ブラウザから見た実際の値」に組み直す。
 *
 * <p>背景: Tomcat の {@code RemoteIpValve}(= {@code forward-headers-strategy: native})は
 * {@code X-Forwarded-Proto: https} を見つけると、{@code X-Forwarded-Port} が無い限り
 * サーバーポートを 443 に固定する。プロキシがポートを {@code Host} ヘッダ(例
 * {@code Host: example.me:7443})でしか伝えてこない場合、この 443 固定で {@code {baseUrl}}
 * からポートが落ち、生成される {@code redirect_uri} が Google 側の登録値と食い違ってログインが
 * 弾かれる(非標準ポート公開で顕在化)。
 *
 * <p>そこで origin を次の優先順で自前導出する(Next.js の travel-log と同じ挙動):
 * <ol>
 *   <li>スキーム = {@code X-Forwarded-Proto}</li>
 *   <li>ホスト = {@code X-Forwarded-Host}(あれば。ポート込み)、無ければ {@code Host} ヘッダ
 *       (ブラウザが送った {@code host:port} をプロキシがそのまま転送している)</li>
 * </ol>
 * {@code Host} ヘッダはポートを保持しているので、{@code X-Forwarded-Port} が無くてもポートが残る。
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
     * 自前導出した origin で redirect_uri のスキーム/ホスト/ポートだけを差し替える。
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
     * スキーム({@code X-Forwarded-Proto})とホスト({@code X-Forwarded-Host} 優先、無ければ
     * {@code Host} ヘッダ)から origin を組む。スキームが分からなければ {@code null} を返し、
     * 既定の {@code {baseUrl}} 展開に委ねる。
     */
    private static String externalOrigin(HttpServletRequest request) {
        String proto = firstValue(request.getHeader("X-Forwarded-Proto"));
        if (!StringUtils.hasText(proto)) {
            return null;
        }
        // ポートは X-Forwarded-Host かこの Host ヘッダに乗る。native の RemoteIpValve は
        // X-Forwarded-Port 欠落時にポートを 443 に固定するので、ここで元の host:port を拾う。
        String host = firstValue(request.getHeader("X-Forwarded-Host"));
        if (!StringUtils.hasText(host)) {
            host = firstValue(request.getHeader("Host"));
        }
        if (!StringUtils.hasText(host)) {
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

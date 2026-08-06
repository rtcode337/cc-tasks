package dev.cctasks.config;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * {@code Host: cctasks.example.com:8443})でしか伝えてこない場合、この 443 固定で {@code {baseUrl}}
 * からポートが落ち、生成される {@code redirect_uri} が Google 側の登録値と食い違ってログインが
 * 弾かれる(非標準ポート公開で顕在化)。
 *
 * <p>そこで origin を次の優先順で自前導出する:
 * <ol>
 *   <li>スキーム = {@code X-Forwarded-Proto}</li>
 *   <li>ホスト = {@code X-Forwarded-Host}(あれば。ポート込み)、無ければ {@code Host} ヘッダ
 *       (ブラウザが送った {@code host:port} をプロキシがそのまま転送している)</li>
 * </ol>
 * {@code Host} ヘッダはポートを保持しているので、{@code X-Forwarded-Port} が無くてもポートが残る。
 *
 * <p>{@code PUBLIC_BASE_URL} が明示設定されているときは {@code redirect-uri} テンプレートが
 * その絶対 URL になっている(= 設定を尊重する)ので、この書き換えは行わない。
 *
 * <p><b>ホストはリクエストヘッダ由来＝攻撃者が自由に付けられる</b>(Host ヘッダ注入)。
 * しかも {@code delegate} が展開する {@code {baseUrl}} 自体がその値を反映しているため、
 * 「怪しければ書き換えない」では守れない。そこで {@code ALLOWED_REDIRECT_HOSTS} が
 * 設定されているときは、<b>redirect_uri を必ず許可リスト内の origin に強制する</b>:
 * リクエストのホストが許可リストにあればそれを、無ければ許可リストの先頭(https)を使う。
 *
 * <p>許可リストが空のときは何もしない —— その場合は {@code PUBLIC_BASE_URL} を設定するか、
 * Spring 既定の {@code {baseUrl}} 展開に委ねる(起動時に {@code SecurityConfig} が警告する)。
 */
public class ForwardedRedirectUriResolver implements OAuth2AuthorizationRequestResolver {

    private static final Logger log = LoggerFactory.getLogger(ForwardedRedirectUriResolver.class);

    private final DefaultOAuth2AuthorizationRequestResolver delegate;
    private final boolean publicBaseUrlConfigured;
    private final Set<String> allowedHosts;
    /** リクエストのホストが許可リストに無いときに使う origin(許可リストの先頭 + https)。 */
    private final String fallbackOrigin;

    public ForwardedRedirectUriResolver(ClientRegistrationRepository clientRegistrationRepository,
            boolean publicBaseUrlConfigured, List<String> allowedRedirectHosts) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepository,
                OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
        this.publicBaseUrlConfigured = publicBaseUrlConfigured;
        List<String> hosts = allowedRedirectHosts.stream()
                .filter(StringUtils::hasText)
                .map(host -> host.trim().toLowerCase(Locale.ROOT))
                .toList();
        this.allowedHosts = Set.copyOf(hosts);
        // 先頭を既定にする。スキームは https 固定 —— 公開は https 前提で、
        // Google Console に登録する URI もそちらのため(http で使うなら PUBLIC_BASE_URL を設定する)
        this.fallbackOrigin = hosts.isEmpty() ? null : "https://" + hosts.get(0);
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
        if (authorizationRequest == null || publicBaseUrlConfigured || allowedHosts.isEmpty()) {
            return authorizationRequest;
        }
        // 許可リストが設定されているときは「書き換えない」では守れない ——
        // delegate が展開した {baseUrl} 自体が Host / X-Forwarded-Host 由来で、
        // すでに攻撃者のホストになっているため(RemoteIpValve と Spring が先に反映する)。
        // そこで redirect_uri を必ず許可リスト内の origin に**強制**する。
        String externalOrigin = externalOrigin(request);
        if (externalOrigin == null) {
            externalOrigin = fallbackOrigin;
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
     * スキーム({@code X-Forwarded-Proto}。無ければリクエストのスキーム)とホスト
     * ({@code X-Forwarded-Host} 優先、無ければ {@code Host} ヘッダ)から origin を組む。
     * ホストが許可リストに無ければ {@code null} を返し、呼び出し側が既定 origin へ倒す。
     */
    private String externalOrigin(HttpServletRequest request) {
        if (allowedHosts.isEmpty()) {
            return null;
        }
        String proto = firstValue(request.getHeader("X-Forwarded-Proto"));
        if (!StringUtils.hasText(proto)) {
            proto = request.isSecure() ? "https" : "http";
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
        if (!allowedHosts.contains(host.toLowerCase(Locale.ROOT))) {
            // 攻撃者が付けた Host / X-Forwarded-Host で redirect_uri を組ませない
            log.warn("許可リストに無いホストからの redirect_uri 書き換えを拒否しました");
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

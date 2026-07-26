package dev.cctasks;

import dev.cctasks.config.ForwardedRedirectUriResolver;
import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ForwardedRedirectUriResolver} が X-Forwarded-Host(ポート込み)から
 * redirect_uri のポートを保持することの検証。
 */
class ForwardedRedirectUriResolverTests {

    private final ClientRegistrationRepository repository = new InMemoryClientRegistrationRepository(
            ClientRegistration.withRegistrationId("google")
                    .clientId("client-id")
                    .clientSecret("client-secret")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/google")
                    .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                    .tokenUri("https://oauth2.googleapis.com/token")
                    .scope("openid", "email", "profile")
                    .clientName("Google")
                    .build());

    @Test
    void X_Forwarded_Host_のポートを保持して_redirect_uri_を組む() {
        ForwardedRedirectUriResolver resolver = new ForwardedRedirectUriResolver(repository, false);

        OAuth2AuthorizationRequest result = resolver.resolve(authorizeRequest(
                "https", "cctasks.example.com:8443"));

        assertThat(result).isNotNull();
        assertThat(result.getRedirectUri())
                .isEqualTo("https://cctasks.example.com:8443/login/oauth2/code/google");
        // Google 認可 URL 側の redirect_uri も差し替え後の値で再生成されること
        assertThat(result.getAuthorizationRequestUri())
                .contains("redirect_uri=https://cctasks.example.com:8443/login/oauth2/code/google");
    }

    @Test
    void X_Forwarded_Host_が無ければ_Host_ヘッダのポートを使う() {
        // 実際に遭遇したリバースプロキシの挙動: X-Forwarded-Host は送らず、
        // Host: example.me:7443 と X-Forwarded-Proto: https だけを送ってくる。
        ForwardedRedirectUriResolver resolver = new ForwardedRedirectUriResolver(repository, false);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/google");
        request.addHeader("X-Forwarded-Proto", "https");
        // MockHttpServletRequest は Host ヘッダを serverName/serverPort から自動生成しないので明示する
        request.addHeader("Host", "example.me:7443");

        OAuth2AuthorizationRequest result = resolver.resolve(request);

        assertThat(result.getRedirectUri())
                .isEqualTo("https://example.me:7443/login/oauth2/code/google");
    }

    @Test
    void 標準ポートならポート表記なしの_origin_になる() {
        ForwardedRedirectUriResolver resolver = new ForwardedRedirectUriResolver(repository, false);

        OAuth2AuthorizationRequest result = resolver.resolve(authorizeRequest(
                "https", "cctasks.example.com"));

        assertThat(result.getRedirectUri())
                .isEqualTo("https://cctasks.example.com/login/oauth2/code/google");
    }

    @Test
    void PUBLIC_BASE_URL_設定時は書き換えない() {
        // publicBaseUrlConfigured=true。テンプレートが絶対 URL 前提なので {baseUrl} 展開のまま。
        ForwardedRedirectUriResolver resolver = new ForwardedRedirectUriResolver(repository, true);

        OAuth2AuthorizationRequest result = resolver.resolve(authorizeRequest(
                "https", "cctasks.example.com:8443"));

        // MockHttpServletRequest の既定(http://localhost)から展開された値のまま
        assertThat(result.getRedirectUri()).isEqualTo("http://localhost/login/oauth2/code/google");
    }

    @Test
    void 転送ヘッダが無ければ既定の展開に委ねる() {
        ForwardedRedirectUriResolver resolver = new ForwardedRedirectUriResolver(repository, false);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/google");
        OAuth2AuthorizationRequest result = resolver.resolve(request);

        assertThat(result.getRedirectUri()).isEqualTo("http://localhost/login/oauth2/code/google");
    }

    private static MockHttpServletRequest authorizeRequest(String forwardedProto, String forwardedHost) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/google");
        request.addHeader("X-Forwarded-Proto", forwardedProto);
        request.addHeader("X-Forwarded-Host", forwardedHost);
        return request;
    }
}

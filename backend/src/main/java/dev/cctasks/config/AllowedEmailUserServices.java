package dev.cctasks.config;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.util.StringUtils;

/**
 * ログイン成功後にメールアドレスを ALLOWED_EMAIL と照合する (仕様書 §6.1)。
 * ユーザーテーブルは作らず、環境変数 1 件との一致だけを見る。
 */
final class AllowedEmailUserServices {

    static final String ACCESS_DENIED_ERROR_CODE = "email_not_allowed";

    private static final Logger log = LoggerFactory.getLogger(AllowedEmailUserServices.class);

    private AllowedEmailUserServices() {
    }

    static OAuth2UserService<OidcUserRequest, OidcUser> oidc(String allowedEmail) {
        OidcUserService delegate = new OidcUserService();
        return request -> {
            OidcUser user = delegate.loadUser(request);
            check(allowedEmail, user.getEmail());
            return user;
        };
    }

    static OAuth2UserService<OAuth2UserRequest, OAuth2User> oauth2(String allowedEmail) {
        DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
        return request -> {
            OAuth2User user = delegate.loadUser(request);
            check(allowedEmail, user.getAttribute("email"));
            return user;
        };
    }

    private static void check(String allowedEmail, String email) {
        if (!StringUtils.hasText(allowedEmail)) {
            log.error("ALLOWED_EMAIL が未設定のためログインを拒否しました");
            throw denied();
        }
        if (email == null || !email.toLowerCase(Locale.ROOT).equals(allowedEmail.trim().toLowerCase(Locale.ROOT))) {
            log.warn("許可されていないアカウントのログインを拒否しました");
            throw denied();
        }
    }

    private static OAuth2AuthenticationException denied() {
        return new OAuth2AuthenticationException(
                new OAuth2Error(ACCESS_DENIED_ERROR_CODE, "このアカウントではログインできません", null),
                "このアカウントではログインできません");
    }
}

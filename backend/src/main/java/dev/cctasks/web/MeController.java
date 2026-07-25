package dev.cctasks.web;

import dev.cctasks.web.Dtos.MeResponse;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {

    /**
     * ログイン中ユーザー情報(表示用)。dev プロファイルでは認証を通さないため
     * principal が無い場合があり、その場合はローカル開発用のダミーを返す。
     */
    @GetMapping("/api/me")
    public MeResponse me(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) {
            return new MeResponse("dev@localhost", "dev", null);
        }
        return new MeResponse(
                principal.getAttribute("email"),
                principal.getAttribute("name"),
                principal.getAttribute("picture"));
    }
}

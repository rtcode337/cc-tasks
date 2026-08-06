package dev.cctasks.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * ログイン開始・コールバックの乱打を IP 単位で抑える (仕様書 §6.3)。
 */
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private final IpRateLimiter rateLimiter;

    public LoginRateLimitFilter(IpRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith("/oauth2/authorization/") || path.startsWith("/login/oauth2/code/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // キーは request.getRemoteAddr()。X-Forwarded-For を自分で読んではいけない ——
        // ヘッダは攻撃者が自由に付けられ、プロキシは消さずに後ろへ追記する
        // (`偽装値, 本物のIP`)ため、先頭を採ると毎リクエスト別バケットになり
        // 制限が丸ごと無効化される(実測: XFF を変えながら30連打で 429 が 0 回)。
        // getRemoteAddr() は forward-headers-strategy: native の RemoteIpValve が
        // 信頼できるプロキシを判定した上で入れた値なので、詐称できない。
        if (!rateLimiter.tryConsume("login:" + request.getRemoteAddr())) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(
                    "{\"error\":{\"code\":\"rate_limited\",\"message\":\"ログイン試行が多すぎます\"}}");
            return;
        }
        filterChain.doFilter(request, response);
    }

}

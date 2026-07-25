package dev.cctasks.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * /mcp への静的 API キー認証 (仕様書 §6.2)。
 *
 * <p>照合はタイミングセーフに行い、失敗は IP 単位でレート制限する。
 * MCP_API_KEY が未設定の場合はサーバー側の設定漏れなので、通さず 503 相当で拒否する。
 */
public class McpApiKeyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(McpApiKeyFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final String expectedKey;
    private final IpRateLimiter rateLimiter;

    public McpApiKeyFilter(String expectedKey, IpRateLimiter rateLimiter) {
        this.expectedKey = expectedKey;
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (!StringUtils.hasText(expectedKey)) {
            log.error("MCP_API_KEY が未設定のため /mcp を拒否しました");
            reject(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "server_misconfigured", "MCP API キーがサーバーに設定されていません");
            return;
        }

        String clientIp = clientIp(request);
        if (!rateLimiter.tryConsume("mcp:" + clientIp)) {
            reject(response, 429, "rate_limited", "リクエストが多すぎます");
            return;
        }

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX) || !matches(header.substring(BEARER_PREFIX.length()))) {
            log.warn("MCP 認証に失敗しました (ip={})", clientIp);
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            reject(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "unauthorized", "API キーが不正です");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean matches(String presented) {
        return MessageDigest.isEqual(
                presented.trim().getBytes(StandardCharsets.UTF_8),
                expectedKey.getBytes(StandardCharsets.UTF_8));
    }

    private static String clientIp(HttpServletRequest request) {
        // ForwardedHeaderFilter がスキーム/ホストを直してくれるが、リモート IP は自前で見る
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static void reject(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                "{\"error\":{\"code\":\"%s\",\"message\":\"%s\"}}".formatted(code, message));
    }
}

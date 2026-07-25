package dev.cctasks;

import dev.cctasks.config.CcTasksProperties;
import dev.cctasks.config.IpRateLimiter;
import dev.cctasks.config.McpApiKeyFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class McpApiKeyFilterTests {

    private static final String KEY = "0123456789abcdef0123456789abcdef0123456789ab";

    private final IpRateLimiter rateLimiter =
            new IpRateLimiter(new CcTasksProperties(null, KEY, null, new CcTasksProperties.RateLimit(100)));

    @Test
    void 正しいキーなら通す() throws Exception {
        MockHttpServletRequest request = post();
        request.addHeader("Authorization", "Bearer " + KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new McpApiKeyFilter(KEY, rateLimiter).doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void キーが無ければ401でチェーンを止める() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        new McpApiKeyFilter(KEY, rateLimiter).doFilter(post(), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("unauthorized");
        verify(chain, never()).doFilter(post(), response);
    }

    @Test
    void 一文字違いでも通さない() throws Exception {
        MockHttpServletRequest request = post();
        request.addHeader("Authorization", "Bearer " + KEY.substring(0, KEY.length() - 1) + "c");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new McpApiKeyFilter(KEY, rateLimiter).doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void サーバー側にキーが設定されていなければ拒否する() throws Exception {
        MockHttpServletRequest request = post();
        request.addHeader("Authorization", "Bearer なんでも");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new McpApiKeyFilter("", rateLimiter).doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getStatus()).isEqualTo(503);
    }

    @Test
    void 連打すると429になる() throws Exception {
        IpRateLimiter limiter =
                new IpRateLimiter(new CcTasksProperties(null, KEY, null, new CcTasksProperties.RateLimit(3)));
        McpApiKeyFilter filter = new McpApiKeyFilter(KEY, limiter);

        int last = 0;
        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(post(), response, mock(FilterChain.class));
            last = response.getStatus();
        }

        assertThat(last).isEqualTo(429);
    }

    private static MockHttpServletRequest post() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
        request.setRemoteAddr("192.0.2.1");
        return request;
    }
}

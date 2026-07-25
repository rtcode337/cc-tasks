package dev.cctasks.mcp;

import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link McpTaskTools} の {@code @Tool} メソッドを MCP ツールとして公開する。
 */
@Configuration(proxyBeanMethods = false)
public class McpConfig {

    @Bean
    ToolCallbackProvider ccTasksTools(McpTaskTools tools) {
        ToolCallback[] callbacks = ToolCallbacks.from(tools);
        return ToolCallbackProvider.from(callbacks);
    }
}

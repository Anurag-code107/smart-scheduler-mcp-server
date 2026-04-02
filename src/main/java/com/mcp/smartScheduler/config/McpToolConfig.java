package com.mcp.smartScheduler.config;

import com.mcp.smartScheduler.tools.SchedulingTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpToolConfig {

    @Bean
    public ToolCallbackProvider schedulingToolCallbackProvider(SchedulingTools schedulingTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(schedulingTools)
                .build();
    }
}
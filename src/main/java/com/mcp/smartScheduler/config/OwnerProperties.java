package com.mcp.smartScheduler.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.owner")
public class OwnerProperties {
    private String name = "Calendar Owner";
    private String email = "negi.dev@gmail.com";
}
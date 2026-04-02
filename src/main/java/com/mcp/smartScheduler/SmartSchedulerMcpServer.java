package com.mcp.smartScheduler;

import com.mcp.smartScheduler.config.OwnerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(OwnerProperties.class)
public class SmartSchedulerMcpServer {

    public static void main(String[] args) {
        SpringApplication.run(SmartSchedulerMcpServer.class, args);
    }
}

package com.mcp.smartScheduler.tools;

import com.mcp.smartScheduler.dto.UserRequest;
import com.mcp.smartScheduler.entity.User;
import com.mcp.smartScheduler.service.CalendarService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;

/**
 * MCP tools for user management.
 */
@Slf4j
@RequiredArgsConstructor
public class UserTools {

    private final CalendarService calendarService;

    @Tool(
        name = "create_user",
        description = "Register a new user. The email must be unique across the system."
    )
    public User createUser(
            @ToolParam(description = "Full name of the user") String name,
            @ToolParam(description = "Email address (must be unique)") String email) {

        log.info("[MCP] create_user: name='{}' email='{}'", name, email);
        UserRequest req = new UserRequest();
        req.setName(name);
        req.setEmail(email);
        return calendarService.createUser(req);
    }

    @Tool(
        name = "get_users",
        description = "List all registered users."
    )
    public List<User> getUsers() {
        log.info("[MCP] get_users");
        return calendarService.getUsers();
    }
}

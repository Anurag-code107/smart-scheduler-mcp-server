package com.mcp.smartScheduler.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Params for the {@code createUser} MCP method.
 */
@Data
public class UserRequest {

    @NotBlank(message = "name is required")
    private String name;

    @NotBlank(message = "email is required")
    @Email(message = "email must be a valid address")
    private String email;
}

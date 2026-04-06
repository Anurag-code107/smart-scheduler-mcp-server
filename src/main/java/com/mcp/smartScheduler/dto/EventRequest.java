package com.mcp.smartScheduler.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class EventRequest {

    @NotBlank(message = "title is required")
    private String title;

    private String description;

    @NotNull(message = "startTime is required")
    private LocalDateTime startTime;

    @NotNull(message = "endTime is required")
    private LocalDateTime endTime;

    /** IANA timezone id, e.g. "Asia/Kolkata". Defaults to "UTC" if omitted. */
    private String timezone = "Asia/Kolkata";

    /** Optional list of participant emails to add at creation time. */
    private List<String> participantEmails;
}
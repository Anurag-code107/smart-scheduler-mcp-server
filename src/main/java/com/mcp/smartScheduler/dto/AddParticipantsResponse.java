package com.mcp.smartScheduler.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Response for the add_participant MCP tool.
 */
@Data
@Builder
public class AddParticipantsResponse {

    private Long eventId;
    private String eventTitle;
    private String participantName;
    private String participantEmail;
    private ParticipantStatus status;
    private String message;
    private EventResponse event;

    public enum ParticipantStatus {
        ADDED,
        ALREADY_PRESENT
    }
}
package com.mcp.smartScheduler.tools;

import com.mcp.smartScheduler.dto.AddParticipantsResponse;
import com.mcp.smartScheduler.dto.EventRequest;
import com.mcp.smartScheduler.dto.EventResponse;
import com.mcp.smartScheduler.exception.ValidationException;
import com.mcp.smartScheduler.service.SchedulingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SchedulingTools {

    private final SchedulingService schedulingService;

    @Tool(
        name = "create_event",
        description = "Create a new calendar event. Automatically prevents double-booking by checking for time conflicts."
    )
    public EventResponse createEvent(
            @ToolParam(description = "Event title") String title,
            @ToolParam(description = "ISO-8601 start datetime, e.g. 2025-06-01T10:00:00") String startTime,
            @ToolParam(description = "ISO-8601 end datetime, e.g. 2025-06-01T11:00:00") String endTime,
            @ToolParam(description = "Event description", required = false) String description,
            @ToolParam(description = "IANA timezone, e.g. Asia/Kolkata. Defaults to UTC.", required = false) String timezone,
            @ToolParam(description = "List of participant email addresses to invite", required = false) List<String> participantEmails) {

        log.info("[MCP] create_event: title='{}'", title);
        if (title == null || title.isBlank()) {
            throw new ValidationException("title must not be blank");
        }

        EventRequest req = new EventRequest();
        req.setTitle(title.trim());
        req.setDescription(description);
        req.setStartTime(parseDateTimeOrThrow(startTime, "startTime"));
        req.setEndTime(parseDateTimeOrThrow(endTime, "endTime"));
        req.setTimezone(timezone != null ? timezone : "UTC");
        req.setParticipantEmails(participantEmails);

        return schedulingService.createEvent(req);
    }

    @Tool(
        name = "get_events",
        description = "Get all calendar events within a date range, ordered by start time."
    )
    public List<EventResponse> getEvents(
            @ToolParam(description = "ISO-8601 range start, e.g. 2025-06-01T00:00:00") String startDate,
            @ToolParam(description = "ISO-8601 range end, e.g. 2025-06-30T23:59:59") String endDate) {

        log.info("[MCP] get_events: [{}, {}]", startDate, endDate);
        return schedulingService.getEvents(
                parseDateTimeOrThrow(startDate, "startDate"),
                parseDateTimeOrThrow(endDate, "endDate"));
    }

    @Tool(
        name = "check_availability",
        description = "Check if the calendar owner is free during a given time slot. Returns available=true/false and any conflicting events."
    )
    public Map<String, Object> checkAvailability(
            @ToolParam(description = "ISO-8601 slot start, e.g. 2025-06-01T14:00:00") String startTime,
            @ToolParam(description = "ISO-8601 slot end, e.g. 2025-06-01T15:00:00") String endTime) {

        log.info("[MCP] check_availability: [{}, {}]", startTime, endTime);
        return schedulingService.checkAvailability(
                parseDateTimeOrThrow(startTime, "startTime"),
                parseDateTimeOrThrow(endTime, "endTime"));
    }

    @Tool(
        name = "reschedule_meeting",
        description = "Move an existing event to a new time slot. Checks for conflicts at the new time."
    )
    public EventResponse rescheduleMeeting(
            @ToolParam(description = "ID of the event to reschedule") Long eventId,
            @ToolParam(description = "ISO-8601 new start time") String newStartTime,
            @ToolParam(description = "ISO-8601 new end time") String newEndTime) {

        log.info("[MCP] reschedule_meeting: eventId={}", eventId);
        if (eventId == null || eventId <= 0) {
            throw new ValidationException("eventId must be a positive integer");
        }
        return schedulingService.rescheduleMeeting(
                eventId,
                parseDateTimeOrThrow(newStartTime, "newStartTime"),
                parseDateTimeOrThrow(newEndTime, "newEndTime"));
    }

    @Tool(
        name = "cancel_meeting",
        description = "Cancel (delete) a calendar event and remove all its participants."
    )
    public Map<String, Object> cancelMeeting(
            @ToolParam(description = "ID of the event to cancel") Long eventId) {

        log.info("[MCP] cancel_meeting: eventId={}", eventId);
        if (eventId == null || eventId <= 0) {
            throw new ValidationException("eventId must be a positive integer");
        }
        return schedulingService.cancelMeeting(eventId);
    }

    @Tool(
        name = "add_participant",
        description = "Add an external attendee to an existing event by name and email."
    )
    public AddParticipantsResponse addParticipant(
            @ToolParam(description = "ID of the event") Long eventId,
            @ToolParam(description = "Full name of the participant") String name,
            @ToolParam(description = "Email address of the participant") String email) {

        log.info("[MCP] add_participant: eventId={} email='{}'", eventId, email);
        if (eventId == null || eventId <= 0) {
            throw new ValidationException("eventId must be a positive integer");
        }
        if (email == null || email.isBlank()) {
            throw new ValidationException("email must not be blank");
        }
        return schedulingService.addParticipant(eventId, name, email);
    }

    @Tool(
        name = "search_events",
        description = "Search for events by title keyword with pagination."
    )
    public Map<String, Object> searchEvents(
            @ToolParam(description = "Keyword to search in event titles") String title,
            @ToolParam(description = "Page number, 0-based. Defaults to 0.", required = false) Integer page,
            @ToolParam(description = "Page size, 1–100. Defaults to 10.", required = false) Integer size) {

        log.info("[MCP] search_events: title='{}'", title);
        if (title == null || title.isBlank()) {
            throw new ValidationException("title must not be blank");
        }
        int resolvedPage = page != null ? page : 0;
        int resolvedSize = size != null ? size : 10;
        if (resolvedPage < 0) throw new ValidationException("page must be >= 0");
        if (resolvedSize < 1 || resolvedSize > 100) throw new ValidationException("size must be between 1 and 100");
        return schedulingService.searchEvents(title.trim(), resolvedPage, resolvedSize);
    }

    private static LocalDateTime parseDateTimeOrThrow(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(fieldName + " must not be blank");
        }
        try {
            return LocalDateTime.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new ValidationException(
                    fieldName + " must be a valid ISO-8601 datetime (e.g. 2025-06-01T10:00:00), got: '" + value + "'");
        }
    }
}
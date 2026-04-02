package com.mcp.smartScheduler.service;

import com.mcp.smartScheduler.dto.EventRequest;
import com.mcp.smartScheduler.dto.EventResponse;
import com.mcp.smartScheduler.dto.UserRequest;
import com.mcp.smartScheduler.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for SchedulingService using an H2 in-memory database.
 * Each test runs in a transaction that is rolled back after the test.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SchedulingServiceTest {

    @Autowired
    private SchedulingService schedulingService;

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        alice = schedulingService.createUser(new UserRequest() {{
            setName("Alice");
            setEmail("alice@test.com");
        }});
        bob = schedulingService.createUser(new UserRequest() {{
            setName("Bob");
            setEmail("bob@test.com");
        }});
    }

    // ─────────────────────────────────────────────────────────────────────────
    // createUser
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("createUser – duplicate email should throw")
    void createUser_duplicateEmail_throws() {
        UserRequest dup = new UserRequest();
        dup.setName("Alice2");
        dup.setEmail("alice@test.com");

        assertThatThrownBy(() -> schedulingService.createUser(dup))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // createEvent
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("createEvent – happy path returns populated response")
    void createEvent_happyPath() {
        EventRequest req = buildEventRequest(
                "Team Standup",
                alice.getId(),
                LocalDateTime.of(2025, 6, 1, 9, 0),
                LocalDateTime.of(2025, 6, 1, 9, 30));
        req.setParticipantIds(List.of(bob.getId()));

        EventResponse resp = schedulingService.createEvent(req);

        assertThat(resp.getId()).isNotNull();
        assertThat(resp.getTitle()).isEqualTo("Team Standup");
        assertThat(resp.getCreatedById()).isEqualTo(alice.getId());
        assertThat(resp.getParticipants()).hasSize(1);
        assertThat(resp.getParticipants().get(0).getEmail()).isEqualTo("bob@test.com");
    }

    @Test
    @DisplayName("createEvent – start after end should throw")
    void createEvent_invalidTimeRange_throws() {
        EventRequest req = buildEventRequest(
                "Bad Event",
                alice.getId(),
                LocalDateTime.of(2025, 6, 1, 10, 0),
                LocalDateTime.of(2025, 6, 1, 9, 0));   // end before start

        assertThatThrownBy(() -> schedulingService.createEvent(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("startTime must be before endTime");
    }

    @Test
    @DisplayName("createEvent – overlapping event for creator should throw")
    void createEvent_overlap_throws() {
        EventRequest first = buildEventRequest(
                "Meeting A",
                alice.getId(),
                LocalDateTime.of(2025, 6, 1, 10, 0),
                LocalDateTime.of(2025, 6, 1, 11, 0));
        schedulingService.createEvent(first);

        // Overlaps with first (10:30–11:30 intersects 10:00–11:00)
        EventRequest second = buildEventRequest(
                "Meeting B",
                alice.getId(),
                LocalDateTime.of(2025, 6, 1, 10, 30),
                LocalDateTime.of(2025, 6, 1, 11, 30));

        assertThatThrownBy(() -> schedulingService.createEvent(second))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("overlapping");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getEvents
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getEvents – returns events within date range")
    void getEvents_inRange() {
        schedulingService.createEvent(buildEventRequest("E1", alice.getId(),
                LocalDateTime.of(2025, 6, 1, 9, 0),
                LocalDateTime.of(2025, 6, 1, 10, 0)));

        schedulingService.createEvent(buildEventRequest("E2", alice.getId(),
                LocalDateTime.of(2025, 6, 2, 9, 0),
                LocalDateTime.of(2025, 6, 2, 10, 0)));

        // Query only June 1
        List<EventResponse> events = schedulingService.getEvents(
                LocalDateTime.of(2025, 6, 1, 0, 0),
                LocalDateTime.of(2025, 6, 1, 23, 59),
                null);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getTitle()).isEqualTo("E1");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // checkAvailability
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("checkAvailability – free slot returns available=true")
    void checkAvailability_free() {
        Map<String, Object> result = schedulingService.checkAvailability(
                alice.getId(),
                LocalDateTime.of(2025, 7, 1, 9, 0),
                LocalDateTime.of(2025, 7, 1, 10, 0));

        assertThat(result.get("available")).isEqualTo(true);
    }

    @Test
    @DisplayName("checkAvailability – busy slot returns available=false with conflicts")
    void checkAvailability_busy() {
        schedulingService.createEvent(buildEventRequest("Busy Block", alice.getId(),
                LocalDateTime.of(2025, 7, 1, 9, 0),
                LocalDateTime.of(2025, 7, 1, 10, 0)));

        Map<String, Object> result = schedulingService.checkAvailability(
                alice.getId(),
                LocalDateTime.of(2025, 7, 1, 9, 30),
                LocalDateTime.of(2025, 7, 1, 10, 30));

        assertThat(result.get("available")).isEqualTo(false);

        @SuppressWarnings("unchecked")
        List<EventResponse> conflicts = (List<EventResponse>) result.get("conflictingEvents");
        assertThat(conflicts).isNotEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // rescheduleMeeting
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("rescheduleMeeting – moves event to free slot")
    void rescheduleMeeting_success() {
        EventResponse created = schedulingService.createEvent(buildEventRequest("Sprint Review", alice.getId(),
                LocalDateTime.of(2025, 8, 1, 9, 0),
                LocalDateTime.of(2025, 8, 1, 10, 0)));

        EventResponse rescheduled = schedulingService.rescheduleMeeting(
                created.getId(),
                LocalDateTime.of(2025, 8, 2, 14, 0),
                LocalDateTime.of(2025, 8, 2, 15, 0));

        assertThat(rescheduled.getStartTime()).isEqualTo(LocalDateTime.of(2025, 8, 2, 14, 0));
        assertThat(rescheduled.getEndTime()).isEqualTo(LocalDateTime.of(2025, 8, 2, 15, 0));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────────────────────

    private EventRequest buildEventRequest(String title, Long creatorId,
                                            LocalDateTime start, LocalDateTime end) {
        EventRequest req = new EventRequest();
        req.setTitle(title);
        req.setCreatedByUserId(creatorId);
        req.setStartTime(start);
        req.setEndTime(end);
        req.setTimezone("UTC");
        return req;
    }
}
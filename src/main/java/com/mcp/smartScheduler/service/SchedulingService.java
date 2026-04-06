package com.mcp.smartScheduler.service;

import com.mcp.smartScheduler.config.OwnerProperties;
import com.mcp.smartScheduler.dto.AddParticipantsResponse;
import com.mcp.smartScheduler.dto.AddParticipantsResponse.ParticipantStatus;
import com.mcp.smartScheduler.dto.EventRequest;
import com.mcp.smartScheduler.dto.EventResponse;
import com.mcp.smartScheduler.dto.EventResponse.ParticipantInfo;
import com.mcp.smartScheduler.dto.UserRequest;
import com.mcp.smartScheduler.entity.Event;
import com.mcp.smartScheduler.entity.Participant;
import com.mcp.smartScheduler.entity.User;
import com.mcp.smartScheduler.exception.ConflictException;
import com.mcp.smartScheduler.exception.ResourceNotFoundException;
import com.mcp.smartScheduler.exception.ValidationException;
import com.mcp.smartScheduler.repository.EventRepository;
import com.mcp.smartScheduler.repository.ParticipantRepository;
import com.mcp.smartScheduler.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SchedulingService {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final ParticipantRepository participantRepository;
    private final OwnerProperties ownerProperties;

    // ─────────────────────────────────────────────────────────────────────────
    // User management
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public User createUser(UserRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new ConflictException("A user with email '" + req.getEmail() + "' already exists");
        }
        User user = User.builder()
                .name(req.getName())
                .email(req.getEmail().trim().toLowerCase())
                .build();
        User saved = userRepository.save(user);
        log.info("Created user id={} email='{}'", saved.getId(), saved.getEmail());
        return saved;
    }

    public List<User> getUsers() {
        return userRepository.findAll();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Owner
    // ─────────────────────────────────────────────────────────────────────────

    public User getOwner() {
        return userRepository.findByEmail(ownerProperties.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Owner", 0L));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // createEvent
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public EventResponse createEvent(EventRequest req) {
        validateTimeRange(req.getStartTime(), req.getEndTime());

        User owner = getOwner();
        checkNoOverlapForOwner(owner, req.getStartTime(), req.getEndTime(), null);

        Event event = Event.builder()
                .title(req.getTitle())
                .description(req.getDescription())
                .startTime(req.getStartTime())
                .endTime(req.getEndTime())
                .createdBy(owner)
                .timezone(req.getTimezone() != null ? req.getTimezone() : "Asia/Kolkata")
                .build();
        final Event saved = eventRepository.save(event);
        log.info("Created event id={} title='{}'", saved.getId(), saved.getTitle());

        if (req.getParticipantEmails() != null) {
            for (String email : req.getParticipantEmails()) {
                if (email != null && !email.isBlank()) {
                    addParticipantInternal(saved, email.trim(), email.trim());
                }
            }
        }
        return toResponse(saved);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getEvents
    // ─────────────────────────────────────────────────────────────────────────

    public List<EventResponse> getEvents(LocalDateTime start, LocalDateTime end) {
        List<Event> events = eventRepository.findByDateRange(start, end);
        log.info("getEvents: found {} events in [{}, {}]", events.size(), start, end);
        return events.stream().map(this::toResponse).toList();
    }

    // ─────────────────────────────────────────────f────────────────────────────
    // checkAvailability
    // ─────────────────────────────────────────────────────────────────────────

    public Map<String, Object> checkAvailability(LocalDateTime start, LocalDateTime end) {
        validateTimeRange(start, end);
        User owner = getOwner();

        List<Event> conflicts = eventRepository.findOverlappingAsCreator(owner, start, end);
        List<EventResponse> conflictResponses = conflicts.stream().map(this::toResponse).toList();

        Map<String, Object> result = new HashMap<>();
        result.put("available", conflicts.isEmpty());
        result.put("startTime", start);
        result.put("endTime", end);
        result.put("conflictingEvents", conflictResponses);

        log.info("checkAvailability [{}, {}] → available={}", start, end, conflicts.isEmpty());
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // rescheduleMeeting
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public EventResponse rescheduleMeeting(Long eventId, LocalDateTime newStart, LocalDateTime newEnd) {
        validateTimeRange(newStart, newEnd);
        Event event = findEventOrThrow(eventId);
        checkNoOverlapForOwner(event.getCreatedBy(), newStart, newEnd, eventId);

        event.setStartTime(newStart);
        event.setEndTime(newEnd);
        event = eventRepository.save(event);

        log.info("Rescheduled event id={} → [{}, {}]", eventId, newStart, newEnd);
        return toResponse(event);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // cancelMeeting
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> cancelMeeting(Long eventId) {
        Event event = findEventOrThrow(eventId);
        participantRepository.deleteByEventId(eventId);
        eventRepository.delete(event);

        log.info("Cancelled event id={} title='{}'", eventId, event.getTitle());
        Map<String, Object> result = new HashMap<>();
        result.put("cancelled", true);
        result.put("eventId", eventId);
        result.put("title", event.getTitle());
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // addParticipant
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public AddParticipantsResponse addParticipant(Long eventId, String name, String email) {
        Event event = findEventOrThrow(eventId);
        String normalizedEmail = email.trim().toLowerCase();
        String resolvedName = (name != null && !name.isBlank()) ? name.trim() : normalizedEmail;

        if (participantRepository.existsByEventIdAndEmail(event.getId(), normalizedEmail)) {
            log.info("add_participant eventId={}: {} already present", eventId, normalizedEmail);
            return AddParticipantsResponse.builder()
                    .eventId(eventId)
                    .eventTitle(event.getTitle())
                    .participantName(resolvedName)
                    .participantEmail(normalizedEmail)
                    .status(ParticipantStatus.ALREADY_PRESENT)
                    .message(normalizedEmail + " is already a participant")
                    .event(toResponse(event))
                    .build();
        }

        participantRepository.save(
                Participant.builder().event(event).name(resolvedName).email(normalizedEmail).build());

        log.info("add_participant eventId={}: added {}", eventId, normalizedEmail);
        return AddParticipantsResponse.builder()
                .eventId(eventId)
                .eventTitle(event.getTitle())
                .participantName(resolvedName)
                .participantEmail(normalizedEmail)
                .status(ParticipantStatus.ADDED)
                .message(resolvedName + " (" + normalizedEmail + ") added successfully")
                .event(toResponse(eventRepository.findById(eventId).orElseThrow()))
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // searchEvents
    // ─────────────────────────────────────────────────────────────────────────

    public Map<String, Object> searchEvents(String title, int page, int size) {
        Page<Event> eventPage = eventRepository
                .findByTitleContainingIgnoreCaseOrderByStartTimeAsc(title, PageRequest.of(page, size));

        Map<String, Object> result = new HashMap<>();
        result.put("content", eventPage.getContent().stream().map(this::toResponse).toList());
        result.put("page", page);
        result.put("size", size);
        result.put("totalElements", eventPage.getTotalElements());
        result.put("totalPages", eventPage.getTotalPages());
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void addParticipantInternal(Event event, String name, String email) {
        if (!participantRepository.existsByEventIdAndEmail(event.getId(), email)) {
            participantRepository.save(
                    Participant.builder().event(event).name(name).email(email).build());
        }
    }

    private void checkNoOverlapForOwner(User owner, LocalDateTime start, LocalDateTime end, Long excludeEventId) {
        List<Event> conflicts = excludeEventId == null
                ? eventRepository.findOverlappingAsCreator(owner, start, end)
                : eventRepository.findOverlappingAsCreatorExcluding(owner, start, end, excludeEventId);

        if (!conflicts.isEmpty()) {
            throw new ConflictException(
                    "You already have " + conflicts.size() + " event(s) overlapping that time slot");
        }
    }

    private void validateTimeRange(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            throw new ValidationException("startTime and endTime must not be null");
        }
        if (!start.isBefore(end)) {
            throw new ValidationException("startTime must be before endTime");
        }
    }

    private Event findEventOrThrow(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId));
    }

    private EventResponse toResponse(Event event) {
        List<ParticipantInfo> participantInfos = participantRepository.findByEventId(event.getId())
                .stream()
                .map(p -> ParticipantInfo.builder()
                        .name(p.getName())
                        .email(p.getEmail())
                        .build())
                .toList();

        return EventResponse.builder()
                .id(event.getId())
                .title(event.getTitle())
                .description(event.getDescription())
                .startTime(event.getStartTime())
                .endTime(event.getEndTime())
                .timezone(event.getTimezone())
                .participants(participantInfos)
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .build();
    }
}
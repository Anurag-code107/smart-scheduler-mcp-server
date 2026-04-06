package com.mcp.smartScheduler.config;

import com.mcp.smartScheduler.entity.Event;
import com.mcp.smartScheduler.entity.Participant;
import com.mcp.smartScheduler.entity.User;
import com.mcp.smartScheduler.repository.EventRepository;
import com.mcp.smartScheduler.repository.ParticipantRepository;
import com.mcp.smartScheduler.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Seeds the owner account and sample events on every startup.
 * All data is wiped on shutdown because ddl-auto=create-drop.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final ParticipantRepository participantRepository;
    private final OwnerProperties ownerProperties;

    @Override
    public void run(String... args) {
        log.info("Seeding owner and sample events...");

        // ── Owner (the single calendar user) ─────────────────────────────────
        User owner = userRepository.save(
                User.builder()
                        .name(ownerProperties.getName())
                        .email(ownerProperties.getEmail())
                        .build());

        // ── 5 Sample Events ───────────────────────────────────────────────────
        LocalDateTime base = LocalDateTime.now().plusDays(1)
                .withHour(0).withMinute(0).withSecond(0).withNano(0);

        Event standup = eventRepository.save(Event.builder()
                .title("Morning Standup")
                .description("Daily team sync — blockers, progress, plan.")
                .startTime(base.withHour(9))
                .endTime(base.withHour(9).withMinute(30))
                .createdBy(owner).timezone("Asia/Kolkata").build());

        Event productReview = eventRepository.save(Event.builder()
                .title("Product Review")
                .description("Q2 roadmap review with the product team.")
                .startTime(base.withHour(10))
                .endTime(base.withHour(11))
                .createdBy(owner).timezone("Asia/Kolkata").build());

        Event clientCall = eventRepository.save(Event.builder()
                .title("Client Call — Acme Corp")
                .description("Quarterly check-in with Acme Corp.")
                .startTime(base.withHour(13))
                .endTime(base.withHour(14))
                .createdBy(owner).timezone("Asia/Kolkata").build());

        Event designReview = eventRepository.save(Event.builder()
                .title("Design Review")
                .description("Review new dashboard mockups.")
                .startTime(base.plusDays(1).withHour(11))
                .endTime(base.plusDays(1).withHour(12))
                .createdBy(owner).timezone("Asia/Kolkata").build());

        Event sprintPlanning = eventRepository.save(Event.builder()
                .title("Sprint Planning")
                .description("Plan tasks and story points for next sprint.")
                .startTime(base.plusDays(1).withHour(14))
                .endTime(base.plusDays(1).withHour(16))
                .createdBy(owner).timezone("Asia/Kolkata").build());

        // ── 5 Sample Participants (external attendees) ────────────────────────
        participantRepository.save(Participant.builder().event(standup).name("Alice Johnson").email("alice@team.com").build());
        participantRepository.save(Participant.builder().event(standup).name("Bob Smith").email("bob@team.com").build());
        participantRepository.save(Participant.builder().event(clientCall).name("John Doe").email("john@acme.com").build());
        participantRepository.save(Participant.builder().event(designReview).name("Sara Lee").email("sara@design.com").build());
        participantRepository.save(Participant.builder().event(sprintPlanning).name("Dev Team").email("devteam@company.com").build());

        log.info("Seeded: 1 owner, {} events, {} participants",
                eventRepository.count(), participantRepository.count());
    }
}
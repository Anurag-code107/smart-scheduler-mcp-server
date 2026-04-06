package com.mcp.smartScheduler.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Represents a calendar event / meeting.
 * Each event has a creator (User) and zero or more Participants.
 */
@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private LocalDateTime endTime;

    /**
     * The user who created / owns this event.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    /**
     * IANA timezone identifier, e.g. "Asia/Kolkata", "UTC", "America/New_York".
     */
    @Column(nullable = false)
    @Builder.Default
    private String timezone = "Asia/Kolkata";

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

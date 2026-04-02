package com.mcp.smartScheduler.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * An external attendee invited to a calendar event.
 * Identified by email — not a system user.
 */
@Entity
@Table(
    name = "participants",
    uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "email"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Participant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;
}
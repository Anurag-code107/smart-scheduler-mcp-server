package com.mcp.smartScheduler.repository;

import com.mcp.smartScheduler.entity.Participant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    List<Participant> findByEventId(Long eventId);

    boolean existsByEventIdAndEmail(Long eventId, String email);

    void deleteByEventId(Long eventId);
}
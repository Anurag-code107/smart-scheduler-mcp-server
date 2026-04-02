package com.mcp.smartScheduler.repository;

import com.mcp.smartScheduler.entity.Event;
import com.mcp.smartScheduler.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {

    // ─── Date-range queries ───────────────────────────────────────────────────

    @Query("SELECT e FROM Event e WHERE e.startTime < :end AND e.endTime > :start ORDER BY e.startTime ASC")
    List<Event> findByDateRange(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    // ─── Search by title ──────────────────────────────────────────────────────

    Page<Event> findByTitleContainingIgnoreCaseOrderByStartTimeAsc(String title, Pageable pageable);

    // ─── Overlap detection (owner only) ──────────────────────────────────────

    @Query("""
            SELECT e FROM Event e
            WHERE e.createdBy = :user
              AND e.startTime < :end AND e.endTime > :start
            """)
    List<Event> findOverlappingAsCreator(
            @Param("user") User user,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("""
            SELECT e FROM Event e
            WHERE e.createdBy = :user
              AND e.id <> :excludeEventId
              AND e.startTime < :end AND e.endTime > :start
            """)
    List<Event> findOverlappingAsCreatorExcluding(
            @Param("user") User user,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("excludeEventId") Long excludeEventId);
}
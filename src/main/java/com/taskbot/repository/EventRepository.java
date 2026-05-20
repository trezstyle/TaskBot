package com.taskbot.repository;

import com.taskbot.entity.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {

    Page<Event> findByUserIdOrderByEventDateAscEventTimeAsc(Long userId, Pageable pageable);

    List<Event> findByUserIdAndEventDate(Long userId, LocalDate date);

    List<Event> findByUserIdAndEventDateBetweenOrderByEventDateAscEventTimeAsc(
            Long userId, LocalDate start, LocalDate end);

    List<Event> findByUserIdAndEventDateGreaterThanEqualOrderByEventDateAscEventTimeAsc(
            Long userId, LocalDate date);

    List<Event> findByUserIdAndEventDateLessThanOrderByEventDateDescEventTimeDesc(
            Long userId, LocalDate date);

    void deleteAllByUserIdAndEventDateLessThan(Long userId, LocalDate date);

    @Query(value = "SELECT * FROM events e WHERE e.reminder_sent = false " +
            "AND e.reminder_minutes_before IS NOT NULL " +
            "AND e.event_date = :today AND e.event_time IS NOT NULL " +
            "AND EXTRACT(EPOCH FROM (CAST(:now AS timestamp) - (CAST(e.event_date AS date) + CAST(e.event_time AS time)))) / 60 >= e.reminder_minutes_before",
            nativeQuery = true)
    List<Event> findDueReminders(@Param("today") LocalDate today, @Param("now") java.time.LocalDateTime now);

    long countByUserId(Long userId);
}

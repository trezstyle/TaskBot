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

    @Query("SELECT e FROM Event e WHERE e.reminderSent = false AND e.reminderMinutesBefore IS NOT NULL " +
            "AND e.eventDate = :today AND e.eventTime IS NOT NULL " +
            "AND FUNCTION('EXTRACT', EPOCH FROM (:now - (e.eventDate + e.eventTime))) / 60 >= e.reminderMinutesBefore")
    List<Event> findDueReminders(@Param("today") LocalDate today, @Param("now") java.time.LocalDateTime now);

    long countByUserId(Long userId);
}

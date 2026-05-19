package com.taskbot.repository;

import com.taskbot.entity.Meeting;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface MeetingRepository extends JpaRepository<Meeting, Long> {

    Page<Meeting> findByUserIdOrderByMeetingDateAscMeetingTimeAsc(Long userId, Pageable pageable);

    List<Meeting> findByUserIdAndMeetingDateGreaterThanEqualOrderByMeetingDateAsc(
            Long userId, LocalDate date);

    @Query("SELECT m FROM Meeting m WHERE m.user.id = :userId " +
           "AND m.meetingDate = :date AND m.meetingTime >= :time " +
           "ORDER BY m.meetingTime ASC")
    List<Meeting> findUpcomingToday(@Param("userId") Long userId,
                                     @Param("date") LocalDate date,
                                     @Param("time") LocalTime time);

    List<Meeting> findByReminderSentFalseAndMeetingDateLessThanEqual(LocalDate date);
}

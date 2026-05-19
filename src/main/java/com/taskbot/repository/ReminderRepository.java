package com.taskbot.repository;

import com.taskbot.entity.Reminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ReminderRepository extends JpaRepository<Reminder, Long> {

    List<Reminder> findByIsSentFalseAndRemindAtLessThanEqual(LocalDateTime now);

    List<Reminder> findByUserIdOrderByRemindAtAsc(Long userId);

    List<Reminder> findByUserIdAndIsSentFalseOrderByRemindAtAsc(Long userId);

    @Query("SELECT r FROM Reminder r WHERE r.user.id = :userId " +
           "AND r.remindAt BETWEEN :start AND :end AND r.isSent = false " +
           "ORDER BY r.remindAt ASC")
    List<Reminder> findPendingInRange(@Param("userId") Long userId,
                                       @Param("start") LocalDateTime start,
                                       @Param("end") LocalDateTime end);
}

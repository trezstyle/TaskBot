package com.taskbot.repository;

import com.taskbot.entity.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    Page<Task> findByUserIdOrderBySortOrderAscCreatedAtDesc(Long userId, Pageable pageable);

    List<Task> findByUserIdAndStatusOrderByPriorityDescDueDateAsc(
            Long userId, Task.Status status);

    List<Task> findByUserIdOrderByDueDateAsc(Long userId);

    @Query("SELECT t FROM Task t WHERE t.user.id = :userId " +
           "AND (:status IS NULL OR t.status = :status) " +
           "AND (:priority IS NULL OR t.priority = :priority) " +
           "ORDER BY t.sortOrder ASC, t.createdAt DESC")
    List<Task> findByFilters(@Param("userId") Long userId,
                              @Param("status") Task.Status status,
                              @Param("priority") Task.Priority priority);

    List<Task> findByUserIdAndDueDateBeforeAndStatusNot(
            Long userId, LocalDate date, Task.Status status);

    long countByUserIdAndStatusNot(Long userId, Task.Status status);
}

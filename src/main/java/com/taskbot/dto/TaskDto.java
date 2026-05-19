package com.taskbot.dto;

import com.taskbot.entity.Task;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskDto {
    private Long id;
    private Long userId;
    private String title;
    private String description;
    private Task.Priority priority;
    private Task.Status status;
    private LocalDate dueDate;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

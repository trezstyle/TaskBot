package com.taskbot.dto.request;

import com.taskbot.entity.Task;
import jakarta.validation.constraints.Size;
import lombok.*;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateTaskRequest {

    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

    @Size(max = 4000, message = "Description must not exceed 4000 characters")
    private String description;

    private Task.Priority priority;

    private Task.Status status;

    private LocalDate dueDate;

    private Integer sortOrder;
}

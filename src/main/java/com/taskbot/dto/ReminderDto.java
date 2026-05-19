package com.taskbot.dto;

import com.taskbot.entity.Reminder;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReminderDto {
    private Long id;
    private Long userId;
    private Reminder.ReminderType reminderType;
    private Long referenceId;
    private String message;
    private LocalDateTime remindAt;
    private Boolean isSent;
    private String cronExpression;
    private Boolean isRecurring;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

package com.taskbot.dto;

import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetingDto {
    private Long id;
    private Long userId;
    private String title;
    private String description;
    private LocalDate meetingDate;
    private LocalTime meetingTime;
    private String location;
    private String participants;
    private Boolean reminderSent;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

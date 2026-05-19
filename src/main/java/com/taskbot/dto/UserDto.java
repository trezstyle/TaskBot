package com.taskbot.dto;

import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDto {
    private Long id;
    private Long telegramId;
    private String username;
    private String firstName;
    private String lastName;
    private String languageCode;
    private String timezone;
    private Boolean notificationsEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

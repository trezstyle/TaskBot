package com.taskbot.dto.request;

import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateUserSettingsRequest {

    @Size(max = 10)
    private String languageCode;

    @Size(max = 50)
    private String timezone;

    private Boolean notificationsEnabled;
}

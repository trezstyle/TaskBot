package com.taskbot.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateMeetingRequest {

    @NotBlank(message = "Meeting title is required")
    @Size(max = 255)
    private String title;

    @Size(max = 4000)
    private String description;

    @NotNull(message = "Meeting date is required")
    private LocalDate meetingDate;

    @NotNull(message = "Meeting time is required")
    private LocalTime meetingTime;

    @Size(max = 255)
    private String location;

    private String participants;
}

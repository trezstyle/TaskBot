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
public class DoctorAppointmentDto {
    private Long id;
    private Long userId;
    private String doctorName;
    private String clinicName;
    private String address;
    private LocalDate appointmentDate;
    private LocalTime appointmentTime;
    private String notes;
    private Boolean reminderSent;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

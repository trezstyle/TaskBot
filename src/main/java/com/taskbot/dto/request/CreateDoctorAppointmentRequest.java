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
public class CreateDoctorAppointmentRequest {

    @NotBlank(message = "Doctor name is required")
    @Size(max = 255)
    private String doctorName;

    @Size(max = 255)
    private String clinicName;

    @Size(max = 500)
    private String address;

    @NotNull(message = "Appointment date is required")
    private LocalDate appointmentDate;

    @NotNull(message = "Appointment time is required")
    private LocalTime appointmentTime;

    @Size(max = 4000)
    private String notes;
}

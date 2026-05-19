package com.taskbot.mapper;

import com.taskbot.dto.DoctorAppointmentDto;
import com.taskbot.dto.request.CreateDoctorAppointmentRequest;
import com.taskbot.entity.DoctorAppointment;
import com.taskbot.entity.User;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DoctorAppointmentMapper {

    DoctorAppointmentDto toDto(DoctorAppointment appointment);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", source = "user")
    @Mapping(target = "reminderSent", constant = "false")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    DoctorAppointment toEntity(CreateDoctorAppointmentRequest request, User user);
}

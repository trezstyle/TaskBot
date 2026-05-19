package com.taskbot.mapper;

import com.taskbot.dto.MeetingDto;
import com.taskbot.dto.request.CreateMeetingRequest;
import com.taskbot.entity.Meeting;
import com.taskbot.entity.User;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MeetingMapper {

    MeetingDto toDto(Meeting meeting);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", source = "user")
    @Mapping(target = "reminderSent", constant = "false")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Meeting toEntity(CreateMeetingRequest request, User user);
}

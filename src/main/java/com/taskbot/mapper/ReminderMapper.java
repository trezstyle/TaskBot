package com.taskbot.mapper;

import com.taskbot.dto.ReminderDto;
import com.taskbot.entity.Reminder;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ReminderMapper {

    ReminderDto toDto(Reminder reminder);
}

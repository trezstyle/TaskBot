package com.taskbot.mapper;

import com.taskbot.dto.UserDto;
import com.taskbot.dto.request.UpdateUserSettingsRequest;
import com.taskbot.entity.User;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {

    UserDto toDto(User user);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateUserFromSettings(UpdateUserSettingsRequest request, @MappingTarget User user);
}

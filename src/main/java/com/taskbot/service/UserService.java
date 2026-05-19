package com.taskbot.service;

import com.taskbot.dto.UserDto;
import com.taskbot.dto.request.UpdateUserSettingsRequest;
import com.taskbot.entity.User;
import com.taskbot.exception.ResourceNotFoundException;
import com.taskbot.mapper.UserMapper;
import com.taskbot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Transactional
    public User findOrCreateUser(Long telegramId, String username, String firstName, String lastName) {
        return userRepository.findByTelegramId(telegramId)
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .telegramId(telegramId)
                            .username(username != null ? username : String.valueOf(telegramId))
                            .firstName(firstName)
                            .lastName(lastName)
                            .languageCode("en")
                            .timezone("UTC")
                            .notificationsEnabled(true)
                            .build();
                    User saved = userRepository.save(newUser);
                    log.info("New user registered: telegramId={}, username={}", telegramId, username);
                    return saved;
                });
    }

    @Transactional(readOnly = true)
    public User getUserByTelegramId(Long telegramId) {
        return userRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new ResourceNotFoundException("User", telegramId));
    }

    @Transactional(readOnly = true)
    public UserDto getUserDto(Long telegramId) {
        User user = getUserByTelegramId(telegramId);
        return userMapper.toDto(user);
    }

    @Transactional
    public UserDto updateSettings(Long telegramId, UpdateUserSettingsRequest request) {
        User user = getUserByTelegramId(telegramId);
        userMapper.updateUserFromSettings(request, user);
        userRepository.save(user);
        log.info("User settings updated: telegramId={}", telegramId);
        return userMapper.toDto(user);
    }
}

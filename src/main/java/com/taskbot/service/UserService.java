package com.taskbot.service;

import com.taskbot.dto.UserDto;
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
                            .languageCode("ru")
                            .timezone("Europe/Moscow")
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
    public void updateLanguage(Long telegramId, String languageCode) {
        User user = getUserByTelegramId(telegramId);
        user.setLanguageCode(languageCode);
        userRepository.save(user);
        log.info("User language updated: telegramId={}", telegramId);
    }

    @Transactional
    public void updateTimezone(Long telegramId, String timezone) {
        User user = getUserByTelegramId(telegramId);
        user.setTimezone(timezone);
        userRepository.save(user);
        log.info("User timezone updated: telegramId={}", telegramId);
    }

    @Transactional
    public void toggleNotifications(Long telegramId) {
        User user = getUserByTelegramId(telegramId);
        user.setNotificationsEnabled(!user.getNotificationsEnabled());
        userRepository.save(user);
        log.info("User notifications toggled: telegramId={}", telegramId);
    }
}

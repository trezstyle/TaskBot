package com.taskbot.service;

import com.taskbot.entity.Reminder;
import com.taskbot.entity.User;
import com.taskbot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulerService {

    private final ReminderService reminderService;
    private final TelegramBotService telegramBotService;
    private final UserRepository userRepository;

    @Scheduled(fixedRate = 30_000)
    @Transactional
    public void processDueReminders() {
        List<Reminder> dueReminders = reminderService.getDueReminders();
        for (Reminder reminder : dueReminders) {
            try {
                User user = reminder.getUser();
                if (Boolean.TRUE.equals(user.getNotificationsEnabled())) {
                    telegramBotService.sendNotification(user.getTelegramId(), reminder.getMessage());
                }
                reminderService.markAsSent(reminder.getId());
                log.debug("Reminder sent: id={}, userId={}", reminder.getId(), user.getId());
            } catch (Exception e) {
                log.error("Failed to send reminder id={}", reminder.getId(), e);
            }
        }
    }

    @Scheduled(cron = "0 0 8 * * ?")
    @Transactional
    public void sendDailySummary() {
        List<User> users = userRepository.findAll();
        for (User user : users) {
            if (Boolean.FALSE.equals(user.getNotificationsEnabled())) {
                continue;
            }
            try {
                LocalDate today = LocalDate.now(ZoneId.of(user.getTimezone()));
                StringBuilder summary = new StringBuilder();
                summary.append("Good morning! Here is your daily summary for ")
                        .append(today).append(":\n\n");

                summary.append("Have a productive day!");
                telegramBotService.sendNotification(user.getTelegramId(), summary.toString());
                log.debug("Daily summary sent to user: {}", user.getTelegramId());
            } catch (Exception e) {
                log.error("Failed to send daily summary to user: {}", user.getTelegramId(), e);
            }
        }
    }

    @Scheduled(cron = "0 0 9 * * ?")
    @Transactional
    public void sendOverdueNotifications() {
    }
}

package com.taskbot.service;

import com.taskbot.entity.Event;
import com.taskbot.entity.User;
import com.taskbot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulerService {

    private final EventService eventService;
    private final TelegramBotService telegramBotService;
    private final UserRepository userRepository;

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void processEventReminders() {
        List<Event> dueEvents = eventService.getDueReminders();
        for (Event event : dueEvents) {
            try {
                User user = event.getUser();
                if (Boolean.TRUE.equals(user.getNotificationsEnabled())) {
                    String timeStr = event.getEventTime() != null
                            ? " at " + event.getEventTime()
                            : "";
                    String msg = String.format("🔔 Reminder: %s%s", event.getTitle(), timeStr);
                    telegramBotService.sendNotification(user.getTelegramId(), msg);
                }
                eventService.markReminderSent(event.getId());
                log.debug("Event reminder sent: id={}, userId={}", event.getId(), user.getId());
            } catch (Exception e) {
                log.error("Failed to send event reminder id={}", event.getId(), e);
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
                Long telegramId = user.getTelegramId();
                LocalDate today = LocalDate.now();
                List<com.taskbot.dto.EventDto> todayEvents = eventService.getEventsByDate(telegramId, today);
                List<com.taskbot.dto.EventDto> upcoming = eventService.getUpcomingEvents(telegramId);

                StringBuilder sb = new StringBuilder();
                sb.append("☀️ Good morning! Summary for ").append(today).append(":\n\n");

                if (todayEvents.isEmpty()) {
                    sb.append("✅ No events today.");
                } else {
                    sb.append("📅 Today (").append(todayEvents.size()).append("):\n");
                    for (com.taskbot.dto.EventDto e : todayEvents) {
                        String timeStr = e.getEventTime() != null ? " 🕐 " + e.getEventTime() : "";
                        sb.append("  • ").append(e.getTitle()).append(timeStr).append("\n");
                    }
                }

                if (!upcoming.isEmpty()) {
                    sb.append("\n📌 Upcoming:\n");
                    upcoming.stream().limit(3).forEach(e ->
                            sb.append("  • ").append(e.getTitle())
                                    .append(" (").append(e.getEventDate()).append(")\n"));
                }

                telegramBotService.sendNotification(telegramId, sb.toString());
                log.debug("Daily summary sent to user: {}", telegramId);
            } catch (Exception e) {
                log.error("Failed to send daily summary to user: {}", user.getTelegramId(), e);
            }
        }
    }
}

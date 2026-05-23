package com.taskbot.service;

import com.taskbot.entity.Event;
import com.taskbot.entity.User;
import com.taskbot.handler.CalendarHandler;
import com.taskbot.handler.EventCreationHandler;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulerService {

    private final EventService eventService;
    private final TelegramBotService telegramBotService;
    private final UserRepository userRepository;
    private final EventCreationHandler creationHandler;
    private final CalendarHandler calendarHandler;

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void processEventReminders() {
        List<User> users = userRepository.findAll();
        for (User user : users) {
            try {
                ZoneId userZone = user.getZoneId();
                LocalDate today = LocalDate.now(userZone);
                LocalDateTime now = LocalDateTime.now(userZone);
                List<Event> dueEvents = eventService.getDueRemindersForUser(user.getId(), today, now);
                for (Event event : dueEvents) {
                    try {
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
            } catch (Exception e) {
                log.error("Failed to process reminders for user: {}", user.getTelegramId(), e);
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
                ZoneId userZone = user.getZoneId();
                LocalDate today = LocalDate.now(userZone);

                // Only send when it's ~8:00 in the user's timezone
                // (this cron runs at server 8:00, but users may be in different zones)
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

    /** Clean up in-memory maps to prevent memory leaks */
    @Scheduled(fixedRate = 30 * 60_000) // every 30 minutes
    public void cleanupStaleData() {
        creationHandler.cleanupStaleSessions();
    }
}

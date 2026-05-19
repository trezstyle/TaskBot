package com.taskbot.service;

import com.taskbot.dto.MeetingDto;
import com.taskbot.dto.TaskDto;
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
    private final TaskService taskService;
    private final MeetingService meetingService;
    private final DoctorAppointmentService doctorAppointmentService;

    @Scheduled(fixedRate = 30_000)
    @Transactional
    public void processDueReminders() {
        List<Reminder> dueReminders = reminderService.getDueReminders();
        for (Reminder reminder : dueReminders) {
            try {
                reminder.setIsSent(true);
                reminderRepositorySave(reminder);
                User user = reminder.getUser();
                if (Boolean.TRUE.equals(user.getNotificationsEnabled())) {
                    telegramBotService.sendNotification(user.getTelegramId(), reminder.getMessage());
                }
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
                Long telegramId = user.getTelegramId();
                LocalDate today = LocalDate.now(ZoneId.of(user.getTimezone()));
                StringBuilder summary = new StringBuilder();
                summary.append("☀️ Доброе утро! Сводка на ").append(today).append(":\n\n");

                List<TaskDto> todayTasks = taskService.getTasksByStatus(telegramId, com.taskbot.entity.Task.Status.TODO);
                List<TaskDto> inProgress = taskService.getTasksByStatus(telegramId, com.taskbot.entity.Task.Status.IN_PROGRESS);
                long overdueCount = taskService.getOverdueTasks(telegramId).size();
                List<MeetingDto> todayMeetings = meetingService.getUpcomingMeetings(telegramId);
                List<com.taskbot.dto.DoctorAppointmentDto> upcomingDoctors = doctorAppointmentService.getUpcomingAppointments(telegramId);

                if (overdueCount > 0) {
                    summary.append("⏰ Просроченных задач: ").append(overdueCount).append("\n");
                }

                if (!inProgress.isEmpty()) {
                    summary.append("\n🔵 В процессе:\n");
                    inProgress.forEach(t -> summary.append("  • ").append(t.getTitle()).append("\n"));
                }

                if (!todayTasks.isEmpty()) {
                    summary.append("\n🟡 Ожидают (").append(todayTasks.size()).append("):\n");
                    todayTasks.stream().limit(5).forEach(t -> summary.append("  • ").append(t.getTitle()).append("\n"));
                    if (todayTasks.size() > 5) {
                        summary.append("  ...и ещё ").append(todayTasks.size() - 5).append("\n");
                    }
                }

                if (!todayMeetings.isEmpty()) {
                    summary.append("\n📅 Встречи сегодня:\n");
                    todayMeetings.forEach(m -> summary.append("  • ").append(m.getTitle())
                            .append(" в ").append(m.getMeetingTime()).append("\n"));
                }

                if (!upcomingDoctors.isEmpty()) {
                    summary.append("\n👨‍⚕️ Ближайшие записи:\n");
                    upcomingDoctors.forEach(a -> summary.append("  • ").append(a.getDoctorName())
                            .append(" (").append(a.getClinicName()).append(")\n"));
                }

                if (overdueCount == 0 && todayTasks.isEmpty() && todayMeetings.isEmpty() && upcomingDoctors.isEmpty()) {
                    summary.append("✅ На сегодня задач нет. Отличный день!");
                }

                telegramBotService.sendNotification(telegramId, summary.toString());
                log.debug("Daily summary sent to user: {}", telegramId);
            } catch (Exception e) {
                log.error("Failed to send daily summary to user: {}", user.getTelegramId(), e);
            }
        }
    }

    @Scheduled(cron = "0 0 9 * * ?")
    @Transactional
    public void sendOverdueNotifications() {
        List<User> users = userRepository.findAll();
        for (User user : users) {
            if (Boolean.FALSE.equals(user.getNotificationsEnabled())) {
                continue;
            }
            try {
                Long telegramId = user.getTelegramId();
                List<TaskDto> overdueTasks = taskService.getOverdueTasks(telegramId);
                if (overdueTasks.isEmpty()) {
                    continue;
                }
                StringBuilder msg = new StringBuilder();
                msg.append("⚠️ У вас просроченных задач: ").append(overdueTasks.size()).append("\n\n");
                overdueTasks.forEach(t ->
                        msg.append("🔴 ").append(t.getTitle())
                                .append(" (дедлайн: ").append(t.getDueDate()).append(")\n"));
                msg.append("\nНе забудьте обновить статус или удалить ненужные задачи!");
                telegramBotService.sendNotification(telegramId, msg.toString());
                log.debug("Overdue notification sent to user: {}", telegramId);
            } catch (Exception e) {
                log.error("Failed to send overdue notification to user: {}", user.getTelegramId(), e);
            }
        }
    }

    private void reminderRepositorySave(Reminder reminder) {
        reminderService.markAsSent(reminder.getId());
    }
}

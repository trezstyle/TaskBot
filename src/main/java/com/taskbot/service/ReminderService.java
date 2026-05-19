package com.taskbot.service;

import com.taskbot.dto.ReminderDto;
import com.taskbot.entity.Meeting;
import com.taskbot.entity.Reminder;
import com.taskbot.entity.Task;
import com.taskbot.entity.User;
import com.taskbot.exception.ResourceNotFoundException;
import com.taskbot.exception.UnauthorizedException;
import com.taskbot.mapper.ReminderMapper;
import com.taskbot.repository.MeetingRepository;
import com.taskbot.repository.ReminderRepository;
import com.taskbot.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderService {

    private final ReminderRepository reminderRepository;
    private final TaskRepository taskRepository;
    private final MeetingRepository meetingRepository;
    private final ReminderMapper reminderMapper;
    private final UserService userService;

    @Transactional
    public Reminder createReminder(User user, Reminder.ReminderType type,
                                    Long referenceId, String message,
                                    LocalDateTime remindAt) {
        Reminder reminder = Reminder.builder()
                .user(user)
                .reminderType(type)
                .referenceId(referenceId)
                .message(message)
                .remindAt(remindAt)
                .isSent(false)
                .isRecurring(false)
                .build();
        Reminder saved = reminderRepository.save(reminder);
        log.info("Reminder created: id={}, type={}, userId={}", saved.getId(), type, user.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ReminderDto> getUserReminders(Long telegramId) {
        User user = userService.getUserByTelegramId(telegramId);
        return reminderRepository.findByUserIdAndIsSentFalseOrderByRemindAtAsc(user.getId())
                .stream()
                .map(reminderMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Reminder> getDueReminders() {
        return reminderRepository.findByIsSentFalseAndRemindAtLessThanEqual(LocalDateTime.now());
    }

    @Transactional
    public void markAsSent(Long reminderId) {
        reminderRepository.findById(reminderId).ifPresent(reminder -> {
            reminder.setIsSent(true);
            reminderRepository.save(reminder);
        });
    }

    @Transactional
    public void deleteReminder(Long reminderId, Long telegramId) {
        Reminder reminder = reminderRepository.findById(reminderId)
                .orElseThrow(() -> new ResourceNotFoundException("Reminder", reminderId));
        User user = userService.getUserByTelegramId(telegramId);
        if (!reminder.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("Access denied");
        }
        reminderRepository.delete(reminder);
    }

    public void createTaskReminders(Task task) {
        if (task.getDueDate() != null) {
            LocalDateTime remindAt = task.getDueDate().atTime(9, 0);
            createReminder(task.getUser(), Reminder.ReminderType.TASK, task.getId(),
                    "Task due: " + task.getTitle(), remindAt);
        }
    }

    public void createMeetingReminder(Meeting meeting) {
        LocalDateTime remindAt = LocalDateTime.of(meeting.getMeetingDate(), meeting.getMeetingTime())
                .minusHours(1);
        createReminder(meeting.getUser(), Reminder.ReminderType.MEETING, meeting.getId(),
                "Meeting: " + meeting.getTitle() + " at " + meeting.getLocation(),
                remindAt);
    }
}

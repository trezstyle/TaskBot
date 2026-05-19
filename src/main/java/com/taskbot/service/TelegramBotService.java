package com.taskbot.service;

import com.taskbot.dto.MeetingDto;
import com.taskbot.dto.NoteDto;
import com.taskbot.dto.TaskDto;
import com.taskbot.dto.request.CreateTaskRequest;
import com.taskbot.dto.request.UpdateTaskRequest;
import com.taskbot.dto.request.UpdateUserSettingsRequest;
import com.taskbot.entity.Reminder;
import com.taskbot.entity.Task;
import com.taskbot.entity.User;
import com.taskbot.security.RateLimitingService;
import com.taskbot.util.TelegramMenuBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramBotService {

    private final TelegramClient telegramClient;
    private final UserService userService;
    private final TaskService taskService;
    private final MeetingService meetingService;
    private final DoctorAppointmentService doctorAppointmentService;
    private final NoteService noteService;
    private final ReminderService reminderService;
    private final RateLimitingService rateLimitingService;

    private final Map<Long, String> userStates = new ConcurrentHashMap<>();
    private final Map<Long, Map<String, String>> userTempData = new ConcurrentHashMap<>();

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    public void processUpdate(Update update) {
        try {
            if (update.hasCallbackQuery()) {
                processCallbackQuery(update.getCallbackQuery());
            } else if (update.hasMessage() && update.getMessage().hasText()) {
                processMessage(update.getMessage());
            }
        } catch (Exception e) {
            log.error("Error processing update", e);
        }
    }

    private void processMessage(Message message) {
        Long telegramId = message.getFrom().getId();
        String text = message.getText().trim();

        rateLimitingService.checkLimit(telegramId);

        User user = userService.findOrCreateUser(
                telegramId,
                message.getFrom().getUserName(),
                message.getFrom().getFirstName(),
                message.getFrom().getLastName()
        );

        String state = userStates.get(telegramId);

        if (state != null) {
            handleStateInput(telegramId, text, state);
            return;
        }

        if ("/start".equals(text)) {
            sendMainMenu(telegramId, "Welcome to TaskBot! Choose an option:");
        } else if ("/tasks".equals(text)) {
            sendMessage(telegramId, "Tasks menu:", TelegramMenuBuilder.buildTasksMenu());
        } else if ("/help".equals(text)) {
            sendMessage(telegramId, """
                    TaskBot Commands:
                    /start - Main menu
                    /tasks - Task management
                    /help - This help message
                    
                    Use the inline buttons to navigate.
                    """, null);
        } else {
            sendMessage(telegramId, "Use /start to see the main menu.", null);
        }
    }

    private void processCallbackQuery(CallbackQuery callbackQuery) {
        Long telegramId = callbackQuery.getFrom().getId();
        String data = callbackQuery.getData();
        Integer messageId = callbackQuery.getMessage().getMessageId();

        rateLimitingService.checkLimit(telegramId);
        userService.findOrCreateUser(
                telegramId,
                callbackQuery.getFrom().getUserName(),
                callbackQuery.getFrom().getFirstName(),
                callbackQuery.getFrom().getLastName()
        );

        if (data.equals("MENU_MAIN")) {
            userStates.remove(telegramId);
            editMessage(telegramId, messageId, "Main menu:", TelegramMenuBuilder.buildMainMenu());
            return;
        }

        if (data.equals("MENU_TASKS")) {
            editMessage(telegramId, messageId, "Tasks:", TelegramMenuBuilder.buildTasksMenu());
            return;
        }

        if (data.equals("MENU_MEETINGS")) {
            editMessage(telegramId, messageId, "Meetings:", TelegramMenuBuilder.buildMeetingsMenu());
            return;
        }

        if (data.equals("MENU_NOTES")) {
            editMessage(telegramId, messageId, "Notes:", TelegramMenuBuilder.buildNotesMenu());
            return;
        }

        if (data.equals("MENU_DOCTORS")) {
            editMessage(telegramId, messageId, "Doctors:", TelegramMenuBuilder.buildDoctorsMenu());
            return;
        }

        if (data.equals("MENU_REMINDERS")) {
            editMessage(telegramId, messageId, "Reminders:", TelegramMenuBuilder.buildRemindersMenu());
            return;
        }

        if (data.equals("MENU_SETTINGS")) {
            showSettings(telegramId, messageId);
            return;
        }

        if (data.equals("TASKS_LIST")) {
            showTasksList(telegramId, messageId, 0);
            return;
        }

        if (data.equals("TASKS_CREATE")) {
            userStates.put(telegramId, "AWAITING_TASK_TITLE");
            editMessage(telegramId, messageId, "Enter task title:", null);
            return;
        }

        if (data.equals("TASKS_OVERDUE")) {
            showOverdueTasks(telegramId, messageId);
            return;
        }

        if (data.equals("TASKS_BY_STATUS")) {
            showTaskStatusFilter(telegramId, messageId);
            return;
        }

        if (data.startsWith("TASK_STATUS_")) {
            String[] parts = data.split("_");
            Long taskId = Long.parseLong(parts[2]);
            Task.Status status = Task.Status.valueOf(parts[3]);
            UpdateTaskRequest request = UpdateTaskRequest.builder().status(status).build();
            taskService.updateTask(taskId, telegramId, request);
            showTasksList(telegramId, messageId, 0);
            return;
        }

        if (data.startsWith("TASK_DELETE_")) {
            Long taskId = Long.parseLong(data.split("_")[2]);
            taskService.deleteTask(taskId, telegramId);
            editMessage(telegramId, messageId, "Task deleted.", TelegramMenuBuilder.buildTasksMenu());
            return;
        }

        if (data.startsWith("TASK_VIEW_")) {
            Long taskId = Long.parseLong(data.split("_")[2]);
            TaskDto task = taskService.getTask(taskId, telegramId);
            String taskInfo = formatTask(task);
            editMessage(telegramId, messageId, taskInfo, TelegramMenuBuilder.buildTaskStatusKeyboard(taskId));
            return;
        }

        if (data.startsWith("TASKS_PAGE_")) {
            int page = Integer.parseInt(data.split("_")[2]);
            showTasksList(telegramId, messageId, page);
            return;
        }

        if (data.equals("MEETINGS_LIST")) {
            showMeetingsList(telegramId, messageId, 0);
            return;
        }

        if (data.equals("MEETINGS_CREATE")) {
            userStates.put(telegramId, "AWAITING_MEETING_TITLE");
            editMessage(telegramId, messageId, "Enter meeting title:", null);
            return;
        }

        if (data.equals("NOTES_LIST")) {
            showNotesList(telegramId, messageId, 0);
            return;
        }

        if (data.equals("NOTES_CREATE")) {
            userStates.put(telegramId, "AWAITING_NOTE_TITLE");
            editMessage(telegramId, messageId, "Enter note title:", null);
            return;
        }

        if (data.equals("NOTES_SEARCH")) {
            userStates.put(telegramId, "AWAITING_NOTE_SEARCH");
            editMessage(telegramId, messageId, "Enter search query:", null);
            return;
        }

        if (data.equals("NOTES_CATEGORIES")) {
            showNoteCategories(telegramId, messageId);
            return;
        }

        if (data.equals("DOCTORS_LIST")) {
            showDoctorAppointments(telegramId, messageId, 0);
            return;
        }

        if (data.equals("DOCTORS_CREATE")) {
            userStates.put(telegramId, "AWAITING_DOCTOR_NAME");
            editMessage(telegramId, messageId, "Enter doctor name:", null);
            return;
        }

        if (data.equals("REMINDERS_LIST")) {
            showRemindersList(telegramId, messageId);
            return;
        }

        if (data.equals("SETTINGS_LANGUAGE")) {
            userStates.put(telegramId, "AWAITING_LANGUAGE");
            editMessage(telegramId, messageId, "Enter language code (en, ru, etc.):", null);
            return;
        }

        if (data.equals("SETTINGS_TIMEZONE")) {
            userStates.put(telegramId, "AWAITING_TIMEZONE");
            editMessage(telegramId, messageId, "Enter timezone (e.g. Europe/Moscow, America/New_York):", null);
            return;
        }

        if (data.equals("SETTINGS_NOTIFICATIONS")) {
            User user = userService.getUserByTelegramId(telegramId);
            UpdateUserSettingsRequest request = UpdateUserSettingsRequest.builder()
                    .notificationsEnabled(!user.getNotificationsEnabled())
                    .build();
            userService.updateSettings(telegramId, request);
            showSettings(telegramId, messageId);
            return;
        }

        log.warn("Unknown callback data: {}", data);
    }

    private void handleStateInput(Long telegramId, String text, String state) {
        switch (state) {
            case "AWAITING_TASK_TITLE" -> {
                Map<String, String> data = new HashMap<>();
                data.put("title", text);
                userTempData.put(telegramId, data);
                userStates.put(telegramId, "AWAITING_TASK_DESC");
                sendMessage(telegramId, "Enter task description (or /skip):", null);
            }
            case "AWAITING_TASK_DESC" -> {
                Map<String, String> data = userTempData.get(telegramId);
                if (!"/skip".equals(text)) {
                    data.put("description", text);
                }
                userStates.put(telegramId, "AWAITING_TASK_PRIORITY");
                sendMessage(telegramId, "Enter priority (LOW, MEDIUM, HIGH) or /skip:", buildPriorityKeyboard());
            }
            case "AWAITING_TASK_PRIORITY" -> {
                Map<String, String> data = userTempData.get(telegramId);
                try {
                    data.put("priority", text.toUpperCase());
                } catch (Exception e) {
                    data.put("priority", "MEDIUM");
                }
                userStates.put(telegramId, "AWAITING_TASK_DUEDATE");
                sendMessage(telegramId, "Enter due date (yyyy-MM-dd) or /skip:", null);
            }
            case "AWAITING_TASK_DUEDATE" -> {
                Map<String, String> data = userTempData.get(telegramId);
                LocalDate dueDate = null;
                if (!"/skip".equals(text)) {
                    try {
                        dueDate = LocalDate.parse(text, DATE_FMT);
                    } catch (Exception e) {
                        sendMessage(telegramId, "Invalid date format. Task created without due date.", null);
                    }
                }
                CreateTaskRequest request = CreateTaskRequest.builder()
                        .title(data.get("title"))
                        .description(data.get("description"))
                        .priority(data.containsKey("priority") ?
                                Task.Priority.valueOf(data.get("priority")) : Task.Priority.MEDIUM)
                        .dueDate(dueDate)
                        .build();
                TaskDto task = taskService.createTask(telegramId, request);
                userStates.remove(telegramId);
                userTempData.remove(telegramId);
                sendMessage(telegramId, "Task created:\n" + formatTask(task), TelegramMenuBuilder.buildTasksMenu());
            }
            case "AWAITING_MEETING_TITLE" -> {
                Map<String, String> data = new HashMap<>();
                data.put("title", text);
                userTempData.put(telegramId, data);
                userStates.put(telegramId, "AWAITING_MEETING_DATE");
                sendMessage(telegramId, "Enter meeting date (yyyy-MM-dd):", null);
            }
            case "AWAITING_MEETING_DATE" -> {
                Map<String, String> data = userTempData.get(telegramId);
                try {
                    LocalDate.parse(text, DATE_FMT);
                    data.put("date", text);
                    userStates.put(telegramId, "AWAITING_MEETING_TIME");
                    sendMessage(telegramId, "Enter meeting time (HH:mm):", null);
                } catch (Exception e) {
                    sendMessage(telegramId, "Invalid date format. Use yyyy-MM-dd:", null);
                }
            }
            case "AWAITING_MEETING_TIME" -> {
                Map<String, String> data = userTempData.get(telegramId);
                try {
                    LocalTime.parse(text, TIME_FMT);
                    data.put("time", text);
                    userStates.put(telegramId, "AWAITING_MEETING_LOCATION");
                    sendMessage(telegramId, "Enter location (or /skip):", null);
                } catch (Exception e) {
                    sendMessage(telegramId, "Invalid time format. Use HH:mm:", null);
                }
            }
            case "AWAITING_MEETING_LOCATION" -> {
                Map<String, String> data = userTempData.get(telegramId);
                if (!"/skip".equals(text)) {
                    data.put("location", text);
                }
                userStates.remove(telegramId);
                userTempData.remove(telegramId);
                sendMessage(telegramId, "Meeting created!", TelegramMenuBuilder.buildMeetingsMenu());
            }
            case "AWAITING_NOTE_TITLE" -> {
                Map<String, String> data = new HashMap<>();
                data.put("title", text);
                userTempData.put(telegramId, data);
                userStates.put(telegramId, "AWAITING_NOTE_CONTENT");
                sendMessage(telegramId, "Enter note content:", null);
            }
            case "AWAITING_NOTE_CONTENT" -> {
                Map<String, String> data = userTempData.get(telegramId);
                data.put("content", text);
                userStates.put(telegramId, "AWAITING_NOTE_CATEGORY");
                sendMessage(telegramId, "Enter category (or /skip):", null);
            }
            case "AWAITING_NOTE_CATEGORY" -> {
                Map<String, String> data = userTempData.get(telegramId);
                String category = "/skip".equals(text) ? null : text;
                NoteDto note = noteService.createNote(telegramId,
                        com.taskbot.dto.request.CreateNoteRequest.builder()
                                .title(data.get("title"))
                                .content(data.get("content"))
                                .category(category)
                                .build());
                userStates.remove(telegramId);
                userTempData.remove(telegramId);
                sendMessage(telegramId, "Note created! Title: " + note.getTitle(),
                        TelegramMenuBuilder.buildNotesMenu());
            }
            case "AWAITING_NOTE_SEARCH" -> {
                userStates.remove(telegramId);
                Page<NoteDto> results = noteService.searchNotes(telegramId, text, 0, 10);
                StringBuilder sb = new StringBuilder("Search results:\n");
                results.getContent().forEach(n ->
                        sb.append("- ").append(n.getTitle())
                                .append(n.getIsPinned() ? " Pinned" : "")
                                .append("\n"));
                if (results.getContent().isEmpty()) {
                    sb.append("No notes found.");
                }
                sendMessage(telegramId, sb.toString(), TelegramMenuBuilder.buildNotesMenu());
            }
            case "AWAITING_DOCTOR_NAME" -> {
                Map<String, String> data = new HashMap<>();
                data.put("doctorName", text);
                userTempData.put(telegramId, data);
                userStates.put(telegramId, "AWAITING_DOCTOR_CLINIC");
                sendMessage(telegramId, "Enter clinic name (or /skip):", null);
            }
            case "AWAITING_DOCTOR_CLINIC" -> {
                Map<String, String> data = userTempData.get(telegramId);
                if (!"/skip".equals(text)) {
                    data.put("clinic", text);
                }
                userStates.put(telegramId, "AWAITING_DOCTOR_DATE");
                sendMessage(telegramId, "Enter appointment date (yyyy-MM-dd):", null);
            }
            case "AWAITING_DOCTOR_DATE" -> {
                Map<String, String> data = userTempData.get(telegramId);
                try {
                    LocalDate.parse(text, DATE_FMT);
                    data.put("date", text);
                    userStates.put(telegramId, "AWAITING_DOCTOR_TIME");
                    sendMessage(telegramId, "Enter appointment time (HH:mm):", null);
                } catch (Exception e) {
                    sendMessage(telegramId, "Invalid date format. Use yyyy-MM-dd:", null);
                }
            }
            case "AWAITING_DOCTOR_TIME" -> {
                Map<String, String> data = userTempData.get(telegramId);
                try {
                    LocalTime.parse(text, TIME_FMT);
                    data.put("time", text);
                    userStates.remove(telegramId);
                    userTempData.remove(telegramId);
                    sendMessage(telegramId, "Doctor appointment created!", TelegramMenuBuilder.buildDoctorsMenu());
                } catch (Exception e) {
                    sendMessage(telegramId, "Invalid time format. Use HH:mm:", null);
                }
            }
            case "AWAITING_LANGUAGE" -> {
                UpdateUserSettingsRequest request = UpdateUserSettingsRequest.builder()
                        .languageCode(text)
                        .build();
                userService.updateSettings(telegramId, request);
                userStates.remove(telegramId);
                sendMessage(telegramId, "Language updated!", TelegramMenuBuilder.buildMainMenu());
            }
            case "AWAITING_TIMEZONE" -> {
                UpdateUserSettingsRequest request = UpdateUserSettingsRequest.builder()
                        .timezone(text)
                        .build();
                userService.updateSettings(telegramId, request);
                userStates.remove(telegramId);
                sendMessage(telegramId, "Timezone updated!", TelegramMenuBuilder.buildMainMenu());
            }
            default -> {
                userStates.remove(telegramId);
                sendMessage(telegramId, "Unknown state. Returning to main menu.",
                        TelegramMenuBuilder.buildMainMenu());
            }
        }
    }

    private void showTasksList(Long telegramId, Integer messageId, int page) {
        Page<TaskDto> tasks = taskService.getUserTasks(telegramId, page, 5);
        StringBuilder sb = new StringBuilder("Your tasks:\n\n");
        List<TaskDto> content = tasks.getContent();
        if (content.isEmpty()) {
            sb.append("No tasks yet.");
        } else {
            for (TaskDto t : content) {
                sb.append(formatTaskShort(t)).append("\n");
            }
        }
        sb.append("\nPage ").append(page + 1).append(" of ").append(tasks.getTotalPages());

        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (TaskDto t : content) {
            rows.add(new InlineKeyboardRow(
                    InlineKeyboardButton.builder()
                            .text("View: " + t.getTitle())
                            .callbackData("TASK_VIEW_" + t.getId())
                            .build()
            ));
        }
        List<InlineKeyboardButton> navButtons = new ArrayList<>();
        if (tasks.hasPrevious()) {
            navButtons.add(InlineKeyboardButton.builder()
                    .text("Previous")
                    .callbackData("TASKS_PAGE_" + (page - 1))
                    .build());
        }
        if (tasks.hasNext()) {
            navButtons.add(InlineKeyboardButton.builder()
                    .text("Next")
                    .callbackData("TASKS_PAGE_" + (page + 1))
                    .build());
        }
        if (!navButtons.isEmpty()) {
            rows.add(new InlineKeyboardRow(navButtons));
        }
        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text("Back")
                        .callbackData("MENU_TASKS")
                        .build()
        ));

        editMessage(telegramId, messageId, sb.toString(),
                InlineKeyboardMarkup.builder().keyboard(rows).build());
    }

    private void showTaskStatusFilter(Long telegramId, Integer messageId) {
        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboard(List.of(
                        new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text("TODO")
                                        .callbackData("TASKS_FILTER_TODO")
                                        .build(),
                                InlineKeyboardButton.builder()
                                        .text("IN PROGRESS")
                                        .callbackData("TASKS_FILTER_IN_PROGRESS")
                                        .build(),
                                InlineKeyboardButton.builder()
                                        .text("DONE")
                                        .callbackData("TASKS_FILTER_DONE")
                                        .build()
                        ),
                        new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text("Back")
                                        .callbackData("MENU_TASKS")
                                        .build()
                        )
                ))
                .build();
        editMessage(telegramId, messageId, "Select status filter:", keyboard);
    }

    private void showOverdueTasks(Long telegramId, Integer messageId) {
        List<TaskDto> overdue = taskService.getOverdueTasks(telegramId);
        StringBuilder sb = new StringBuilder("Overdue tasks:\n");
        if (overdue.isEmpty()) {
            sb.append("No overdue tasks!");
        } else {
            overdue.forEach(t -> sb.append(formatTaskShort(t)).append("\n"));
        }
        editMessage(telegramId, messageId, sb.toString(), TelegramMenuBuilder.buildTasksMenu());
    }

    private void showMeetingsList(Long telegramId, Integer messageId, int page) {
        Page<MeetingDto> meetings = meetingService.getUserMeetings(telegramId, page, 5);
        StringBuilder sb = new StringBuilder("Your meetings:\n\n");
        if (meetings.getContent().isEmpty()) {
            sb.append("No meetings yet.");
        } else {
            meetings.getContent().forEach(m -> {
                sb.append("- ").append(m.getTitle())
                        .append(" on ").append(m.getMeetingDate())
                        .append(" at ").append(m.getMeetingTime())
                        .append("\n");
            });
        }
        editMessage(telegramId, messageId, sb.toString(), TelegramMenuBuilder.buildMeetingsMenu());
    }

    private void showNotesList(Long telegramId, Integer messageId, int page) {
        Page<NoteDto> notes = noteService.getUserNotes(telegramId, page, 5);
        StringBuilder sb = new StringBuilder("Your notes:\n\n");
        if (notes.getContent().isEmpty()) {
            sb.append("No notes yet.");
        } else {
            notes.getContent().forEach(n ->
                    sb.append("- ").append(n.getTitle())
                            .append(n.getIsPinned() ? " Pinned" : "")
                            .append(" [").append(n.getCategory() != null ? n.getCategory() : "no category").append("]")
                            .append("\n"));
        }
        editMessage(telegramId, messageId, sb.toString(), TelegramMenuBuilder.buildNotesMenu());
    }

    private void showNoteCategories(Long telegramId, Integer messageId) {
        List<String> categories = noteService.getCategories(telegramId);
        StringBuilder sb = new StringBuilder("Your note categories:\n");
        if (categories.isEmpty()) {
            sb.append("No categories yet.");
        } else {
            categories.forEach(c -> sb.append("- ").append(c).append("\n"));
        }
        editMessage(telegramId, messageId, sb.toString(), TelegramMenuBuilder.buildNotesMenu());
    }

    private void showDoctorAppointments(Long telegramId, Integer messageId, int page) {
        Page<com.taskbot.dto.DoctorAppointmentDto> appointments =
                doctorAppointmentService.getUserAppointments(telegramId, page, 5);
        StringBuilder sb = new StringBuilder("Your doctor appointments:\n\n");
        if (appointments.getContent().isEmpty()) {
            sb.append("No appointments yet.");
        } else {
            appointments.getContent().forEach(a ->
                    sb.append("- Dr. ").append(a.getDoctorName())
                            .append(" on ").append(a.getAppointmentDate())
                            .append(" at ").append(a.getAppointmentTime())
                            .append("\n"));
        }
        editMessage(telegramId, messageId, sb.toString(), TelegramMenuBuilder.buildDoctorsMenu());
    }

    private void showRemindersList(Long telegramId, Integer messageId) {
        List<com.taskbot.dto.ReminderDto> reminders = reminderService.getUserReminders(telegramId);
        StringBuilder sb = new StringBuilder("Pending reminders:\n\n");
        if (reminders.isEmpty()) {
            sb.append("No pending reminders.");
        } else {
            reminders.forEach(r ->
                    sb.append("- ").append(r.getMessage())
                            .append(" at ").append(r.getRemindAt())
                            .append("\n"));
        }
        editMessage(telegramId, messageId, sb.toString(), TelegramMenuBuilder.buildRemindersMenu());
    }

    private void showSettings(Long telegramId, Integer messageId) {
        User user = userService.getUserByTelegramId(telegramId);
        String text = String.format("""
                Settings:
                
                Language: %s
                Timezone: %s
                Notifications: %s
                """,
                user.getLanguageCode(),
                user.getTimezone(),
                user.getNotificationsEnabled() ? "ON" : "OFF");
        editMessage(telegramId, messageId, text,
                TelegramMenuBuilder.buildSettingsMenu(
                        user.getLanguageCode(),
                        user.getTimezone(),
                        user.getNotificationsEnabled()));
    }

    private String formatTask(TaskDto task) {
        return String.format("""
                Task: %s
                Status: %s
                Priority: %s
                Due: %s
                Description: %s
                """,
                task.getTitle(),
                task.getStatus(),
                task.getPriority(),
                task.getDueDate() != null ? task.getDueDate() : "No deadline",
                task.getDescription() != null ? task.getDescription() : "No description");
    }

    private String formatTaskShort(TaskDto task) {
        return String.format("- %s [%s] %s%s",
                task.getTitle(),
                task.getStatus(),
                task.getPriority(),
                task.getDueDate() != null ? " Due: " + task.getDueDate() : "");
    }

    private InlineKeyboardMarkup buildPriorityKeyboard() {
        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(
                        new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text("LOW")
                                        .callbackData("PRIORITY_LOW")
                                        .build(),
                                InlineKeyboardButton.builder()
                                        .text("MEDIUM")
                                        .callbackData("PRIORITY_MEDIUM")
                                        .build(),
                                InlineKeyboardButton.builder()
                                        .text("HIGH")
                                        .callbackData("PRIORITY_HIGH")
                                        .build()
                        )
                ))
                .build();
    }

    public void sendMainMenu(Long telegramId, String text) {
        sendMessage(telegramId, text, TelegramMenuBuilder.buildMainMenu());
    }

    public void sendNotification(Long telegramId, String text) {
        try {
            SendMessage message = SendMessage.builder()
                    .chatId(telegramId)
                    .text(text)
                    .build();
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send notification to user: {}", telegramId, e);
        }
    }

    private void sendMessage(Long chatId, String text, InlineKeyboardMarkup keyboard) {
        try {
            SendMessage message = SendMessage.builder()
                    .chatId(chatId)
                    .text(text)
                    .replyMarkup(keyboard)
                    .build();
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send message to chat: {}", chatId, e);
        }
    }

    private void editMessage(Long chatId, Integer messageId, String text, InlineKeyboardMarkup keyboard) {
        try {
            EditMessageText message = EditMessageText.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .text(text)
                    .replyMarkup(keyboard)
                    .build();
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to edit message: {}", messageId, e);
        }
    }
}

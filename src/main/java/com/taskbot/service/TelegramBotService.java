package com.taskbot.service;

import com.taskbot.dto.TaskDto;
import com.taskbot.dto.request.CreateTaskRequest;
import com.taskbot.dto.request.UpdateTaskRequest;
import com.taskbot.dto.request.UpdateUserSettingsRequest;
import com.taskbot.entity.Task;
import com.taskbot.entity.User;
import com.taskbot.security.RateLimitingService;
import com.taskbot.util.CalendarBuilder;
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

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    public void processUpdate(Update update) {
        try {
            if (update.hasCallbackQuery()) {
                processCallbackQuery(update.getCallbackQuery());
            } else if (update.hasMessage() && update.getMessage().hasText()) {
                processMessage(update.getMessage());
            }
        } catch (com.taskbot.exception.RateLimitExceededException e) {
            Long telegramId = update.hasMessage() ? update.getMessage().getFrom().getId() : update.getCallbackQuery().getFrom().getId();
            sendMessage(telegramId, "⚠️ Слишком много запросов. Подождите немного.", null);
        } catch (Exception e) {
            log.error("Error processing update", e);
            Long telegramId = update.hasMessage() ? update.getMessage().getFrom().getId() : update.getCallbackQuery().getFrom().getId();
            sendMessage(telegramId, "❌ Произошла ошибка. Попробуйте ещё раз.", null);
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

        if (state != null && !"/cancel".equals(text)) {
            handleStateInput(telegramId, text, state);
            return;
        }

        if ("/cancel".equals(text)) {
            userStates.remove(telegramId);
            userTempData.remove(telegramId);
            sendMainMenu(telegramId, "❌ Действие отменено. Возвращаю в главное меню.");
            return;
        }

        if ("/start".equals(text)) {
            sendMainMenu(telegramId, "👋 Добро пожаловать в TaskBot!\n\nУправляйте задачами, встречами, записями к врачу и заметками в одном месте.");
        } else if ("/tasks".equals(text)) {
            sendMessage(telegramId, "📋 Управление задачами:", TelegramMenuBuilder.buildTasksMenu());
        } else if ("/help".equals(text)) {
            sendMessage(telegramId, """
                    📖 Доступные команды:
                    
                    /start - Главное меню
                    /tasks - Управление задачами
                    /cancel - Отменить текущее действие
                    /help - Эта справка
                    
                    💡 Используйте кнопки для навигации.
                    """, null);
        } else {
            sendMessage(telegramId, "🤔 Неизвестная команда. Используйте /start для главного меню.", null);
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

        switch (data) {
            case "MENU_MAIN" -> {
                userStates.remove(telegramId);
                editMessage(telegramId, messageId, "🏠 Главное меню:", TelegramMenuBuilder.buildMainMenu());
            }
            case "MENU_TASKS" -> editMessage(telegramId, messageId, "📋 Управление задачами:", TelegramMenuBuilder.buildTasksMenu());
            case "MENU_MEETINGS" -> editMessage(telegramId, messageId, "📅 Управление встречами:", TelegramMenuBuilder.buildMeetingsMenu());
            case "MENU_NOTES" -> editMessage(telegramId, messageId, "📝 Управление заметками:", TelegramMenuBuilder.buildNotesMenu());
            case "MENU_DOCTORS" -> editMessage(telegramId, messageId, "👨‍⚕️ Управление записями:", TelegramMenuBuilder.buildDoctorsMenu());
            case "MENU_REMINDERS" -> editMessage(telegramId, messageId, "🔔 Напоминания:", TelegramMenuBuilder.buildRemindersMenu());
            case "MENU_SETTINGS" -> showSettings(telegramId, messageId);
            case "TASKS_LIST" -> showTasksList(telegramId, messageId, 0);
            case "TASKS_CREATE" -> {
                userStates.put(telegramId, "AWAITING_TASK_TITLE");
                editMessage(telegramId, messageId, "📝 Введите название задачи:", null);
            }
            case "TASKS_OVERDUE" -> showOverdueTasks(telegramId, messageId);
            case "TASKS_FILTER_TODO" -> showTasksByStatus(telegramId, messageId, Task.Status.TODO);
            case "TASKS_FILTER_IN_PROGRESS" -> showTasksByStatus(telegramId, messageId, Task.Status.IN_PROGRESS);
            case "TASKS_FILTER_DONE" -> showTasksByStatus(telegramId, messageId, Task.Status.DONE);
            case "MEETINGS_LIST" -> showMeetingsList(telegramId, messageId, 0);
            case "MEETINGS_CREATE" -> {
                userStates.put(telegramId, "AWAITING_MEETING_TITLE");
                editMessage(telegramId, messageId, "📅 Введите название встречи:", null);
            }
            case "NOTES_LIST" -> showNotesList(telegramId, messageId, 0);
            case "NOTES_CREATE" -> {
                userStates.put(telegramId, "AWAITING_NOTE_TITLE");
                editMessage(telegramId, messageId, "📝 Введите название заметки:", null);
            }
            case "NOTES_SEARCH" -> {
                userStates.put(telegramId, "AWAITING_NOTE_SEARCH");
                editMessage(telegramId, messageId, "🔍 Введите поисковый запрос:", null);
            }
            case "NOTES_CATEGORIES" -> showNoteCategories(telegramId, messageId);
            case "DOCTORS_LIST" -> showDoctorAppointments(telegramId, messageId, 0);
            case "DOCTORS_CREATE" -> {
                userStates.put(telegramId, "AWAITING_DOCTOR_NAME");
                editMessage(telegramId, messageId, "👨‍⚕️ Введите имя врача:", null);
            }
            case "REMINDERS_LIST" -> showRemindersList(telegramId, messageId);
            case "SETTINGS_LANGUAGE" -> {
                userStates.put(telegramId, "AWAITING_LANGUAGE");
                editMessage(telegramId, messageId, "🌐 Введите код языка (ru, en, de и т.д.):", null);
            }
            case "SETTINGS_TIMEZONE" -> {
                userStates.put(telegramId, "AWAITING_TIMEZONE");
                editMessage(telegramId, messageId, "🕐 Введите часовой пояс (например: Europe/Moscow):", null);
            }
            case "SETTINGS_NOTIFICATIONS" -> {
                User user = userService.getUserByTelegramId(telegramId);
                UpdateUserSettingsRequest request = UpdateUserSettingsRequest.builder()
                        .notificationsEnabled(!user.getNotificationsEnabled())
                        .build();
                userService.updateSettings(telegramId, request);
                showSettings(telegramId, messageId);
            }
            default -> {
                if (data.startsWith("TASK_STATUS_")) {
                    handleTaskStatusChange(telegramId, messageId, data);
                } else if (data.startsWith("TASK_DELETE_")) {
                    handleTaskDelete(telegramId, messageId, data);
                } else if (data.startsWith("TASK_VIEW_")) {
                    handleTaskView(telegramId, messageId, data);
                } else if (data.startsWith("TASKS_PAGE_")) {
                    int page = Integer.parseInt(data.split("_")[2]);
                    showTasksList(telegramId, messageId, page);
                } else if (data.startsWith("CALENDAR_")) {
                    handleCalendarAction(telegramId, messageId, data);
                } else if (data.startsWith("TIME_")) {
                    handleTimeAction(telegramId, messageId, data);
                } else if (data.startsWith("PRIORITY_")) {
                    handlePrioritySelection(telegramId, messageId, data);
                } else if (data.startsWith("REMINDER_DELETE_")) {
                    handleReminderDelete(telegramId, messageId, data);
                } else if (data.startsWith("MEETING_DELETE_")) {
                    handleMeetingDelete(telegramId, messageId, data);
                } else if (data.startsWith("DOCTOR_DELETE_")) {
                    handleDoctorDelete(telegramId, messageId, data);
                } else if (data.startsWith("NOTE_DELETE_")) {
                    handleNoteDelete(telegramId, messageId, data);
                } else if (data.startsWith("NOTE_VIEW_")) {
                    handleNoteView(telegramId, messageId, data);
                } else if (data.startsWith("NOTE_PIN_")) {
                    handleNotePin(telegramId, messageId, data);
                } else if (data.startsWith("MEETING_UPCOMING")) {
                    showUpcomingMeetings(telegramId, messageId);
                } else if (data.startsWith("DOCTOR_UPCOMING")) {
                    showUpcomingDoctors(telegramId, messageId);
                }
            }
        }
    }

    private void handleCalendarAction(Long telegramId, Integer messageId, String data) {
        String[] parts = data.split("_");
        String action = parts[1];
        String context = parts.length > 2 ? parts[2] : "";

        Map<String, String> tempData = userTempData.get(telegramId);
        LocalDate currentDate = tempData != null && tempData.containsKey("calendarDate")
                ? LocalDate.parse(tempData.get("calendarDate"))
                : LocalDate.now();

        switch (action) {
            case "PREV_MONTH" -> {
                currentDate = currentDate.minusMonths(1);
                if (tempData != null) tempData.put("calendarDate", currentDate.toString());
                editMessage(telegramId, messageId, "📅 Выберите дату:", CalendarBuilder.buildCalendar(currentDate, "CALENDAR"));
            }
            case "NEXT_MONTH" -> {
                currentDate = currentDate.plusMonths(1);
                if (tempData != null) tempData.put("calendarDate", currentDate.toString());
                editMessage(telegramId, messageId, "📅 Выберите дату:", CalendarBuilder.buildCalendar(currentDate, "CALENDAR"));
            }
            case "SELECT" -> {
                LocalDate selectedDate = LocalDate.parse(parts[3]);
                if (tempData != null) {
                    tempData.put("selectedDate", selectedDate.toString());
                    String step = tempData.get("nextStep");
                    if ("MEETING_TIME".equals(step)) {
                        tempData.put("calendarMode", "MEETING");
                        editMessage(telegramId, messageId, "🕐 Выберите время:", CalendarBuilder.buildTimePicker("TIME"));
                    } else if ("DOCTOR_TIME".equals(step)) {
                        tempData.put("calendarMode", "DOCTOR");
                        editMessage(telegramId, messageId, "🕐 Выберите время:", CalendarBuilder.buildTimePicker("TIME"));
                    } else if ("TASK_DUE".equals(step)) {
                        createTaskWithCalendarDate(telegramId, messageId, selectedDate);
                    }
                }
            }
        }
    }

    private void handleTimeAction(Long telegramId, Integer messageId, String data) {
        String[] parts = data.split("_");
        String action = parts[1];
        String value = parts.length > 2 ? parts[2] : "";

        Map<String, String> tempData = userTempData.get(telegramId);
        if (tempData == null) return;

        switch (action) {
            case "HOUR" -> {
                tempData.put("selectedHour", value);
                editMessage(telegramId, messageId, "🕐 Выберите минуты:", CalendarBuilder.buildTimePicker("TIME"));
            }
            case "MINUTE" -> {
                String hour = tempData.get("selectedHour");
                String minute = value;
                LocalTime time = LocalTime.of(Integer.parseInt(hour), Integer.parseInt(minute));
                tempData.put("selectedTime", time.toString());

                String mode = tempData.get("calendarMode");
                if ("MEETING".equals(mode)) {
                    createMeetingWithCalendarData(telegramId, messageId);
                } else if ("DOCTOR".equals(mode)) {
                    createDoctorWithCalendarData(telegramId, messageId);
                }
            }
        }
    }

    private void handlePrioritySelection(Long telegramId, Integer messageId, String data) {
        String priority = data.split("_")[1];
        Map<String, String> tempData = userTempData.get(telegramId);
        if (tempData != null) {
            tempData.put("priority", priority);
            showCalendarForTaskDueDate(telegramId, messageId);
        }
    }

    private void showCalendarForTaskDueDate(Long telegramId, Integer messageId) {
        Map<String, String> tempData = userTempData.get(telegramId);
        if (tempData == null) return;

        tempData.put("nextStep", "TASK_DUE");
        editMessage(telegramId, messageId, "📅 Выберите дедлайн:", CalendarBuilder.buildCalendar(LocalDate.now(), "CALENDAR"));
    }

    private void createTaskWithCalendarDate(Long telegramId, Integer messageId, LocalDate dueDate) {
        Map<String, String> tempData = userTempData.get(telegramId);
        if (tempData == null) return;

        CreateTaskRequest request = CreateTaskRequest.builder()
                .title(tempData.get("title"))
                .description(tempData.get("description"))
                .priority(tempData.containsKey("priority") ?
                        Task.Priority.valueOf(tempData.get("priority")) : Task.Priority.MEDIUM)
                .dueDate(dueDate)
                .build();

        TaskDto task = taskService.createTask(telegramId, request);
        userStates.remove(telegramId);
        userTempData.remove(telegramId);
        sendMessage(telegramId, "✅ Задача создана!\n\n" + formatTask(task), TelegramMenuBuilder.buildTasksMenu());
    }

    private void createMeetingWithCalendarData(Long telegramId, Integer messageId) {
        Map<String, String> tempData = userTempData.get(telegramId);
        if (tempData == null) return;

        LocalDate date = LocalDate.parse(tempData.get("selectedDate"));
        LocalTime time = LocalTime.parse(tempData.get("selectedTime"));

        // Create meeting
        com.taskbot.dto.request.CreateMeetingRequest request = com.taskbot.dto.request.CreateMeetingRequest.builder()
                .title(tempData.get("title"))
                .meetingDate(date)
                .meetingTime(time)
                .location(tempData.get("location"))
                .build();

        meetingService.createMeeting(telegramId, request);
        userStates.remove(telegramId);
        userTempData.remove(telegramId);
        sendMessage(telegramId, "✅ Встреча создана!\n\n📅 " + date.format(DATE_FMT) + " 🕐 " + time.format(TIME_FMT),
                TelegramMenuBuilder.buildMeetingsMenu());
    }

    private void createDoctorWithCalendarData(Long telegramId, Integer messageId) {
        Map<String, String> tempData = userTempData.get(telegramId);
        if (tempData == null) return;

        LocalDate date = LocalDate.parse(tempData.get("selectedDate"));
        LocalTime time = LocalTime.parse(tempData.get("selectedTime"));

        com.taskbot.dto.request.CreateDoctorAppointmentRequest request = com.taskbot.dto.request.CreateDoctorAppointmentRequest.builder()
                .doctorName(tempData.get("doctorName"))
                .clinicName(tempData.get("clinic"))
                .appointmentDate(date)
                .appointmentTime(time)
                .build();

        doctorAppointmentService.createAppointment(telegramId, request);
        userStates.remove(telegramId);
        userTempData.remove(telegramId);
        sendMessage(telegramId, "✅ Запись к врачу создана!\n\n📅 " + date.format(DATE_FMT) + " 🕐 " + time.format(TIME_FMT),
                TelegramMenuBuilder.buildDoctorsMenu());
    }

    private void handleStateInput(Long telegramId, String text, String state) {
        switch (state) {
            case "AWAITING_TASK_TITLE" -> {
                Map<String, String> data = new HashMap<>();
                data.put("title", text);
                userTempData.put(telegramId, data);
                userStates.put(telegramId, "AWAITING_TASK_DESC");
                sendMessage(telegramId, "📝 Введите описание задачи (или /skip):", null);
            }
            case "AWAITING_TASK_DESC" -> {
                Map<String, String> data = userTempData.get(telegramId);
                if (!"/skip".equals(text)) {
                    data.put("description", text);
                }
                userStates.put(telegramId, "AWAITING_TASK_PRIORITY");
                sendMessage(telegramId, "🎯 Выберите приоритет:", buildPriorityKeyboard());
            }
            case "AWAITING_TASK_PRIORITY" -> {
                sendMessage(telegramId, "⚠️ Нажмите одну из кнопок выше для выбора приоритета:", buildPriorityKeyboard());
            }
            case "AWAITING_MEETING_TITLE" -> {
                Map<String, String> data = new HashMap<>();
                data.put("title", text);
                userTempData.put(telegramId, data);
                userStates.put(telegramId, "AWAITING_MEETING_LOCATION");
                sendMessage(telegramId, "📍 Введите место встречи (или /skip):", null);
            }
            case "AWAITING_MEETING_LOCATION" -> {
                Map<String, String> data = userTempData.get(telegramId);
                if (!"/skip".equals(text)) {
                    data.put("location", text);
                }
                userStates.put(telegramId, "AWAITING_MEETING_DATE_CALENDAR");
                data.put("nextStep", "MEETING_TIME");
                sendMessage(telegramId, "📅 Выберите дату:", CalendarBuilder.buildCalendar(LocalDate.now(), "CALENDAR"));
            }
            case "AWAITING_NOTE_TITLE" -> {
                Map<String, String> data = new HashMap<>();
                data.put("title", text);
                userTempData.put(telegramId, data);
                userStates.put(telegramId, "AWAITING_NOTE_CONTENT");
                sendMessage(telegramId, "📝 Введите содержимое заметки:", null);
            }
            case "AWAITING_NOTE_CONTENT" -> {
                Map<String, String> data = userTempData.get(telegramId);
                data.put("content", text);
                userStates.put(telegramId, "AWAITING_NOTE_CATEGORY");
                sendMessage(telegramId, "📂 Введите категорию (или /skip):", null);
            }
            case "AWAITING_NOTE_CATEGORY" -> {
                Map<String, String> data = userTempData.get(telegramId);
                String category = "/skip".equals(text) ? null : text;
                noteService.createNote(telegramId,
                        com.taskbot.dto.request.CreateNoteRequest.builder()
                                .title(data.get("title"))
                                .content(data.get("content"))
                                .category(category)
                                .build());
                userStates.remove(telegramId);
                userTempData.remove(telegramId);
                sendMessage(telegramId, "✅ Заметка создана!", TelegramMenuBuilder.buildNotesMenu());
            }
            case "AWAITING_NOTE_SEARCH" -> {
                userStates.remove(telegramId);
                Page<com.taskbot.dto.NoteDto> results = noteService.searchNotes(telegramId, text, 0, 10);
                StringBuilder sb = new StringBuilder("🔍 Результаты поиска:\n\n");
                results.getContent().forEach(n ->
                        sb.append("📌 ").append(n.getTitle())
                                .append(n.getIsPinned() ? " 🔒" : "")
                                .append("\n"));
                if (results.getContent().isEmpty()) {
                    sb.append("📭 Заметки не найдены.");
                }
                sendMessage(telegramId, sb.toString(), TelegramMenuBuilder.buildNotesMenu());
            }
            case "AWAITING_DOCTOR_NAME" -> {
                Map<String, String> data = new HashMap<>();
                data.put("doctorName", text);
                userTempData.put(telegramId, data);
                userStates.put(telegramId, "AWAITING_DOCTOR_CLINIC");
                sendMessage(telegramId, "🏥 Введите название клиники (или /skip):", null);
            }
            case "AWAITING_DOCTOR_CLINIC" -> {
                Map<String, String> data = userTempData.get(telegramId);
                if (!"/skip".equals(text)) {
                    data.put("clinic", text);
                }
                userStates.put(telegramId, "AWAITING_DOCTOR_DATE_CALENDAR");
                data.put("nextStep", "DOCTOR_TIME");
                sendMessage(telegramId, "📅 Выберите дату:", CalendarBuilder.buildCalendar(LocalDate.now(), "CALENDAR"));
            }
            case "AWAITING_LANGUAGE" -> {
                UpdateUserSettingsRequest request = UpdateUserSettingsRequest.builder()
                        .languageCode(text)
                        .build();
                userService.updateSettings(telegramId, request);
                userStates.remove(telegramId);
                sendMessage(telegramId, "✅ Язык обновлен!", TelegramMenuBuilder.buildMainMenu());
            }
            case "AWAITING_TIMEZONE" -> {
                UpdateUserSettingsRequest request = UpdateUserSettingsRequest.builder()
                        .timezone(text)
                        .build();
                userService.updateSettings(telegramId, request);
                userStates.remove(telegramId);
                sendMessage(telegramId, "✅ Часовой пояс обновлен!", TelegramMenuBuilder.buildMainMenu());
            }
            default -> {
                userStates.remove(telegramId);
                sendMessage(telegramId, "🤔 Неизвестное состояние. Возвращаю в главное меню.",
                        TelegramMenuBuilder.buildMainMenu());
            }
        }
    }

    private void handleTaskStatusChange(Long telegramId, Integer messageId, String data) {
        String[] parts = data.split("_");
        Long taskId = Long.parseLong(parts[2]);
        Task.Status status = Task.Status.valueOf(parts[3]);
        UpdateTaskRequest request = UpdateTaskRequest.builder().status(status).build();
        taskService.updateTask(taskId, telegramId, request);
        showTasksList(telegramId, messageId, 0);
    }

    private void handleTaskDelete(Long telegramId, Integer messageId, String data) {
        Long taskId = Long.parseLong(data.split("_")[2]);
        taskService.deleteTask(taskId, telegramId);
        editMessage(telegramId, messageId, "✅ Задача удалена.", TelegramMenuBuilder.buildTasksMenu());
    }

    private void handleTaskView(Long telegramId, Integer messageId, String data) {
        Long taskId = Long.parseLong(data.split("_")[2]);
        TaskDto task = taskService.getTask(taskId, telegramId);
        String taskInfo = formatTask(task);
        editMessage(telegramId, messageId, taskInfo, TelegramMenuBuilder.buildTaskStatusKeyboard(taskId));
    }

    private void showTasksList(Long telegramId, Integer messageId, int page) {
        Page<TaskDto> tasks = taskService.getUserTasks(telegramId, page, 5);
        StringBuilder sb = new StringBuilder("📋 Ваши задачи:\n\n");
        List<TaskDto> content = tasks.getContent();
        if (content.isEmpty()) {
            sb.append("📭 Задач пока нет.\n\nНажмите \"➕ Новая задача\" чтобы создать.");
        } else {
            for (TaskDto t : content) {
                sb.append(formatTaskShort(t)).append("\n");
            }
        }
        sb.append("\n📄 Страница ").append(page + 1).append(" из ").append(tasks.getTotalPages());

        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (TaskDto t : content) {
            rows.add(new InlineKeyboardRow(
                    InlineKeyboardButton.builder()
                            .text("👁 " + t.getTitle())
                            .callbackData("TASK_VIEW_" + t.getId())
                            .build()
            ));
        }
        List<InlineKeyboardButton> navButtons = new ArrayList<>();
        if (tasks.hasPrevious()) {
            navButtons.add(InlineKeyboardButton.builder()
                    .text("⬅️ Назад")
                    .callbackData("TASKS_PAGE_" + (page - 1))
                    .build());
        }
        if (tasks.hasNext()) {
            navButtons.add(InlineKeyboardButton.builder()
                    .text("Вперед ➡️")
                    .callbackData("TASKS_PAGE_" + (page + 1))
                    .build());
        }
        if (!navButtons.isEmpty()) {
            rows.add(new InlineKeyboardRow(navButtons));
        }
        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text("⬅️ Назад")
                        .callbackData("MENU_TASKS")
                        .build()
        ));

        editMessage(telegramId, messageId, sb.toString(),
                InlineKeyboardMarkup.builder().keyboard(rows).build());
    }

    private void showTasksByStatus(Long telegramId, Integer messageId, Task.Status status) {
        List<TaskDto> tasks = taskService.getTasksByStatus(telegramId, status);
        StringBuilder sb = new StringBuilder("📋 Задачи со статусом ")
                .append(getStatusEmoji(status)).append(" ").append(status).append(":\n\n");
        if (tasks.isEmpty()) {
            sb.append("📭 Задач с таким статусом нет.");
        } else {
            tasks.forEach(t -> sb.append(formatTaskShort(t)).append("\n"));
        }
        editMessage(telegramId, messageId, sb.toString(), TelegramMenuBuilder.buildTasksMenu());
    }

    private void showOverdueTasks(Long telegramId, Integer messageId) {
        List<TaskDto> overdue = taskService.getOverdueTasks(telegramId);
        StringBuilder sb = new StringBuilder("⏰ Просроченные задачи:\n");
        if (overdue.isEmpty()) {
            sb.append("✅ Просроченных задач нет!");
        } else {
            overdue.forEach(t -> sb.append(formatTaskShort(t)).append("\n"));
        }
        editMessage(telegramId, messageId, sb.toString(), TelegramMenuBuilder.buildTasksMenu());
    }

    private void showMeetingsList(Long telegramId, Integer messageId, int page) {
        Page<com.taskbot.dto.MeetingDto> meetings = meetingService.getUserMeetings(telegramId, page, 5);
        StringBuilder sb = new StringBuilder("📅 Ваши встречи:\n\n");
        List<InlineKeyboardRow> rows = new ArrayList<>();
        if (meetings.getContent().isEmpty()) {
            sb.append("📭 Встреч пока нет.");
        } else {
            for (com.taskbot.dto.MeetingDto m : meetings.getContent()) {
                sb.append("📌 ").append(m.getTitle())
                        .append("\n📅 ").append(m.getMeetingDate().format(DATE_FMT))
                        .append(" 🕐 ").append(m.getMeetingTime().format(TIME_FMT))
                        .append("\n📍 ").append(m.getLocation() != null ? m.getLocation() : "Не указано")
                        .append("\n\n");
                rows.add(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text("🗑 Удалить")
                                .callbackData("MEETING_DELETE_" + m.getId())
                                .build()
                ));
            }
        }
        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text("⬅️ Назад")
                        .callbackData("MENU_MEETINGS")
                        .build()
        ));
        editMessage(telegramId, messageId, sb.toString(),
                InlineKeyboardMarkup.builder().keyboard(rows).build());
    }

    private void showNotesList(Long telegramId, Integer messageId, int page) {
        Page<com.taskbot.dto.NoteDto> notes = noteService.getUserNotes(telegramId, page, 5);
        StringBuilder sb = new StringBuilder("📝 Ваши заметки:\n\n");
        List<InlineKeyboardRow> rows = new ArrayList<>();
        if (notes.getContent().isEmpty()) {
            sb.append("📭 Заметок пока нет.");
        } else {
            for (com.taskbot.dto.NoteDto n : notes.getContent()) {
                sb.append(n.getIsPinned() ? "🔒 " : "📝 ")
                        .append(n.getTitle())
                        .append(" [").append(n.getCategory() != null ? n.getCategory() : "без категории").append("]")
                        .append("\n");
                rows.add(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text("👁 Просмотр")
                                .callbackData("NOTE_VIEW_" + n.getId())
                                .build()
                ));
            }
        }
        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text("⬅️ Назад")
                        .callbackData("MENU_NOTES")
                        .build()
        ));
        editMessage(telegramId, messageId, sb.toString(),
                InlineKeyboardMarkup.builder().keyboard(rows).build());
    }

    private void showNoteCategories(Long telegramId, Integer messageId) {
        List<String> categories = noteService.getCategories(telegramId);
        StringBuilder sb = new StringBuilder("📂 Категории заметок:\n");
        if (categories.isEmpty()) {
            sb.append("📭 Категорий пока нет.");
        } else {
            categories.forEach(c -> sb.append("📌 ").append(c).append("\n"));
        }
        editMessage(telegramId, messageId, sb.toString(), TelegramMenuBuilder.buildNotesMenu());
    }

    private void showDoctorAppointments(Long telegramId, Integer messageId, int page) {
        Page<com.taskbot.dto.DoctorAppointmentDto> appointments =
                doctorAppointmentService.getUserAppointments(telegramId, page, 5);
        StringBuilder sb = new StringBuilder("👨‍⚕️ Ваши записи к врачу:\n\n");
        List<InlineKeyboardRow> rows = new ArrayList<>();
        if (appointments.getContent().isEmpty()) {
            sb.append("📭 Записей пока нет.");
        } else {
            for (com.taskbot.dto.DoctorAppointmentDto a : appointments.getContent()) {
                sb.append("👨‍⚕️ Др. ").append(a.getDoctorName())
                        .append("\n🏥 ").append(a.getClinicName() != null ? a.getClinicName() : "Не указано")
                        .append("\n📅 ").append(a.getAppointmentDate().format(DATE_FMT))
                        .append(" 🕐 ").append(a.getAppointmentTime().format(TIME_FMT))
                        .append("\n\n");
                rows.add(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text("🗑 Удалить")
                                .callbackData("DOCTOR_DELETE_" + a.getId())
                                .build()
                ));
            }
        }
        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text("⬅️ Назад")
                        .callbackData("MENU_DOCTORS")
                        .build()
        ));
        editMessage(telegramId, messageId, sb.toString(),
                InlineKeyboardMarkup.builder().keyboard(rows).build());
    }

    private void showRemindersList(Long telegramId, Integer messageId) {
        List<com.taskbot.dto.ReminderDto> reminders = reminderService.getUserReminders(telegramId);
        StringBuilder sb = new StringBuilder("🔔 Ваши напоминания:\n\n");
        List<InlineKeyboardRow> rows = new ArrayList<>();
        if (reminders.isEmpty()) {
            sb.append("📭 Напоминаний пока нет.");
        } else {
            for (com.taskbot.dto.ReminderDto r : reminders) {
                sb.append("📌 ").append(r.getMessage())
                        .append("\n⏰ ").append(r.getRemindAt().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")))
                        .append("\n\n");
                rows.add(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text("🗑 Удалить")
                                .callbackData("REMINDER_DELETE_" + r.getId())
                                .build()
                ));
            }
        }
        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text("⬅️ Назад")
                        .callbackData("MENU_MAIN")
                        .build()
        ));
        editMessage(telegramId, messageId, sb.toString(),
                InlineKeyboardMarkup.builder().keyboard(rows).build());
    }

    private void handleReminderDelete(Long telegramId, Integer messageId, String data) {
        Long reminderId = Long.parseLong(data.split("_")[2]);
        reminderService.deleteReminder(reminderId, telegramId);
        showRemindersList(telegramId, messageId);
    }

    private void handleMeetingDelete(Long telegramId, Integer messageId, String data) {
        Long meetingId = Long.parseLong(data.split("_")[2]);
        meetingService.deleteMeeting(meetingId, telegramId);
        editMessage(telegramId, messageId, "✅ Встреча удалена.", TelegramMenuBuilder.buildMeetingsMenu());
    }

    private void handleDoctorDelete(Long telegramId, Integer messageId, String data) {
        Long appointmentId = Long.parseLong(data.split("_")[2]);
        doctorAppointmentService.deleteAppointment(appointmentId, telegramId);
        editMessage(telegramId, messageId, "✅ Запись к врачу удалена.", TelegramMenuBuilder.buildDoctorsMenu());
    }

    private void handleNoteDelete(Long telegramId, Integer messageId, String data) {
        Long noteId = Long.parseLong(data.split("_")[2]);
        noteService.deleteNote(noteId, telegramId);
        showNotesList(telegramId, messageId, 0);
    }

    private void handleNoteView(Long telegramId, Integer messageId, String data) {
        Long noteId = Long.parseLong(data.split("_")[2]);
        String content = noteService.getDecryptedContent(noteId, telegramId);
        com.taskbot.dto.NoteDto note = noteService.getUserNotes(telegramId, 0, 100).getContent().stream()
                .filter(n -> n.getId().equals(noteId)).findFirst().orElse(null);
        if (note != null) {
            String text = String.format("📝 %s\n📂 %s\n🏷 %s\n\n%s",
                    note.getTitle(),
                    note.getCategory() != null ? note.getCategory() : "без категории",
                    note.getTags() != null ? note.getTags() : "без тегов",
                    content);
            List<InlineKeyboardRow> rows = new ArrayList<>();
            rows.add(new InlineKeyboardRow(
                    InlineKeyboardButton.builder()
                            .text(note.getIsPinned() ? "📌 Открепить" : "📌 Закрепить")
                            .callbackData("NOTE_PIN_" + noteId)
                            .build(),
                    InlineKeyboardButton.builder()
                            .text("🗑 Удалить")
                            .callbackData("NOTE_DELETE_" + noteId)
                            .build()
            ));
            rows.add(new InlineKeyboardRow(
                    InlineKeyboardButton.builder()
                            .text("⬅️ Назад")
                            .callbackData("NOTES_LIST")
                            .build()
            ));
            editMessage(telegramId, messageId, text,
                    InlineKeyboardMarkup.builder().keyboard(rows).build());
        }
    }

    private void handleNotePin(Long telegramId, Integer messageId, String data) {
        Long noteId = Long.parseLong(data.split("_")[2]);
        noteService.togglePin(noteId, telegramId);
        handleNoteView(telegramId, messageId, "NOTE_VIEW_" + noteId);
    }

    private void showUpcomingMeetings(Long telegramId, Integer messageId) {
        List<com.taskbot.dto.MeetingDto> meetings = meetingService.getUpcomingMeetings(telegramId);
        StringBuilder sb = new StringBuilder("📅 Ближайшие встречи:\n\n");
        List<InlineKeyboardRow> rows = new ArrayList<>();
        if (meetings.isEmpty()) {
            sb.append("📭 Встреч пока нет.");
        } else {
            for (com.taskbot.dto.MeetingDto m : meetings) {
                sb.append("📌 ").append(m.getTitle())
                        .append("\n📅 ").append(m.getMeetingDate().format(DATE_FMT))
                        .append(" 🕐 ").append(m.getMeetingTime().format(TIME_FMT))
                        .append("\n📍 ").append(m.getLocation() != null ? m.getLocation() : "Не указано")
                        .append("\n\n");
                rows.add(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text("🗑 Удалить")
                                .callbackData("MEETING_DELETE_" + m.getId())
                                .build()
                ));
            }
        }
        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text("⬅️ Назад")
                        .callbackData("MENU_MEETINGS")
                        .build()
        ));
        editMessage(telegramId, messageId, sb.toString(),
                InlineKeyboardMarkup.builder().keyboard(rows).build());
    }

    private void showUpcomingDoctors(Long telegramId, Integer messageId) {
        List<com.taskbot.dto.DoctorAppointmentDto> appointments = doctorAppointmentService.getUpcomingAppointments(telegramId);
        StringBuilder sb = new StringBuilder("👨‍⚕️ Ближайшие записи:\n\n");
        List<InlineKeyboardRow> rows = new ArrayList<>();
        if (appointments.isEmpty()) {
            sb.append("📭 Записей пока нет.");
        } else {
            for (com.taskbot.dto.DoctorAppointmentDto a : appointments) {
                sb.append("👨‍⚕️ Др. ").append(a.getDoctorName())
                        .append("\n🏥 ").append(a.getClinicName() != null ? a.getClinicName() : "Не указано")
                        .append("\n📅 ").append(a.getAppointmentDate().format(DATE_FMT))
                        .append(" 🕐 ").append(a.getAppointmentTime().format(TIME_FMT))
                        .append("\n\n");
                rows.add(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text("🗑 Удалить")
                                .callbackData("DOCTOR_DELETE_" + a.getId())
                                .build()
                ));
            }
        }
        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text("⬅️ Назад")
                        .callbackData("MENU_DOCTORS")
                        .build()
        ));
        editMessage(telegramId, messageId, sb.toString(),
                InlineKeyboardMarkup.builder().keyboard(rows).build());
    }

    private void showSettings(Long telegramId, Integer messageId) {
        User user = userService.getUserByTelegramId(telegramId);
        String text = String.format("""
                ⚙️ Настройки:
                
                🌐 Язык: %s
                🕐 Часовой пояс: %s
                🔔 Уведомления: %s
                """,
                user.getLanguageCode().toUpperCase(),
                user.getTimezone(),
                user.getNotificationsEnabled() ? "ВКЛ" : "ВЫКЛ");
        editMessage(telegramId, messageId, text,
                TelegramMenuBuilder.buildSettingsMenu(
                        user.getLanguageCode(),
                        user.getTimezone(),
                        user.getNotificationsEnabled()));
    }

    private String formatTask(TaskDto task) {
        return String.format("""
                📌 %s
                
                📊 Статус: %s
                🎯 Приоритет: %s
                📅 Дедлайн: %s
                📝 Описание: %s
                """,
                task.getTitle(),
                getStatusEmoji(task.getStatus()) + " " + task.getStatus(),
                getPriorityEmoji(task.getPriority()) + " " + task.getPriority(),
                task.getDueDate() != null ? task.getDueDate().format(DATE_FMT) : "Не установлен",
                task.getDescription() != null ? task.getDescription() : "Нет описания");
    }

    private String formatTaskShort(TaskDto task) {
        return String.format("%s %s [%s] %s%s",
                getStatusEmoji(task.getStatus()),
                task.getTitle(),
                task.getStatus(),
                getPriorityEmoji(task.getPriority()),
                task.getDueDate() != null ? " 📅 " + task.getDueDate().format(DATE_FMT) : "");
    }

    private String getStatusEmoji(Task.Status status) {
        return switch (status) {
            case TODO -> "🟡";
            case IN_PROGRESS -> "🔵";
            case DONE -> "✅";
        };
    }

    private String getPriorityEmoji(Task.Priority priority) {
        return switch (priority) {
            case LOW -> "🟢";
            case MEDIUM -> "🟡";
            case HIGH -> "🔴";
        };
    }

    private InlineKeyboardMarkup buildPriorityKeyboard() {
        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(
                        new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text("🟢 Низкий")
                                        .callbackData("PRIORITY_LOW")
                                        .build(),
                                InlineKeyboardButton.builder()
                                        .text("🟡 Средний")
                                        .callbackData("PRIORITY_MEDIUM")
                                        .build(),
                                InlineKeyboardButton.builder()
                                        .text("🔴 Высокий")
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

package com.taskbot.service;

import com.taskbot.dto.EventDto;
import com.taskbot.entity.User;
import com.taskbot.security.RateLimitingService;
import com.taskbot.util.CalendarBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final EventService eventService;
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
            sendMainMenu(telegramId, "❌ Действие отменено.");
            return;
        }

        if ("/start".equals(text)) {
            userStates.remove(telegramId);
            userTempData.remove(telegramId);
            showCalendar(telegramId, LocalDate.now());
        } else if ("/help".equals(text)) {
            sendMessage(telegramId, """
                    📖 Доступные команды:
                    
                    /start - Календарь
                    /cancel - Отменить действие
                    /help - Эта справка
                    
                    💡 Нажмите на дату чтобы увидеть события.
                    """, null);
        } else {
            sendMessage(telegramId, "🤔 Используйте /start для календаря.", null);
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
                userTempData.remove(telegramId);
                showCalendar(telegramId, LocalDate.now());
            }
            case "CAL_PREV", "ADD_PREV" -> showCalendarPrevMonth(telegramId, messageId, data.startsWith("ADD"));
            case "CAL_NEXT", "ADD_NEXT" -> showCalendarNextMonth(telegramId, messageId, data.startsWith("ADD"));
            case "CAL_ADD" -> startCreateEvent(telegramId, messageId);
            default -> {
                if (data.startsWith("CAL_DAY_")) {
                    handleDaySelection(telegramId, messageId, data);
                } else if (data.startsWith("ADD_DAY_")) {
                    handleAddDaySelection(telegramId, messageId, data);
                } else if (data.startsWith("EVT_VIEW_")) {
                    handleEventView(telegramId, messageId, data);
                } else if (data.startsWith("EVT_DELETE_")) {
                    handleEventDelete(telegramId, messageId, data);
                } else if (data.startsWith("EVT_BACK_")) {
                    handleBackToDate(telegramId, messageId, data);
                } else if (data.startsWith("CREATE_TIME_")) {
                    handleTimeSelection(telegramId, messageId, data);
                } else if (data.startsWith("CREATE_COLOR_")) {
                    handleColorSelection(telegramId, messageId, data);
                } else if (data.startsWith("CREATE_REM_")) {
                    handleReminderSelection(telegramId, messageId, data);
                } else if ("CREATE_SKIP_TIME".equals(data)) {
                    handleSkipTime(telegramId, messageId);
                } else if ("CREATE_BACK_DATE".equals(data)) {
                    handleBackToDateSelection(telegramId, messageId);
                }
            }
        }
    }

    private void showCalendar(Long telegramId, LocalDate date) {
        showCalendar(telegramId, date, false);
    }

    private void showCalendar(Long telegramId, LocalDate date, boolean addMode) {
        Map<LocalDate, Integer> eventCounts = new HashMap<>();
        LocalDate start = date.withDayOfMonth(1);
        LocalDate end = date.withDayOfMonth(date.lengthOfMonth());
        List<EventDto> events = eventService.getEventsInRange(telegramId, start, end);
        for (EventDto e : events) {
            eventCounts.merge(e.getEventDate(), 1, Integer::sum);
        }

        String prefix = addMode ? "ADD" : "CAL";
        String monthName = date.getMonth().getDisplayName(java.time.format.TextStyle.FULL, new Locale("ru"));
        String text = String.format("📅 %s %d\n\nНажмите на дату для просмотра событий.\n【】 - сегодня\n• - есть события",
                monthName, date.getYear());

        sendMessage(telegramId, text, CalendarBuilder.buildCalendar(date, eventCounts, prefix));
    }

    private void showCalendarPrevMonth(Long telegramId, Integer messageId, boolean addMode) {
        Map<String, String> temp = userTempData.computeIfAbsent(telegramId, k -> new HashMap<>());
        LocalDate current = temp.containsKey("calendarDate")
                ? LocalDate.parse(temp.get("calendarDate"))
                : LocalDate.now();
        current = current.minusMonths(1);
        temp.put("calendarDate", current.toString());
        showCalendar(telegramId, current, addMode);
    }

    private void showCalendarNextMonth(Long telegramId, Integer messageId, boolean addMode) {
        Map<String, String> temp = userTempData.computeIfAbsent(telegramId, k -> new HashMap<>());
        LocalDate current = temp.containsKey("calendarDate")
                ? LocalDate.parse(temp.get("calendarDate"))
                : LocalDate.now();
        current = current.plusMonths(1);
        temp.put("calendarDate", current.toString());
        showCalendar(telegramId, current, addMode);
    }

    private void handleDaySelection(Long telegramId, Integer messageId, String data) {
        LocalDate date = LocalDate.parse(data.split("_")[2]);
        List<EventDto> events = eventService.getEventsByDate(telegramId, date);

        StringBuilder sb = new StringBuilder();
        sb.append("📅 ").append(date.format(DATE_FMT)).append("\n\n");

        List<InlineKeyboardRow> rows = new ArrayList<>();

        if (events.isEmpty()) {
            sb.append("📭 Нет событий на этот день.");
        } else {
            for (EventDto e : events) {
                String timeStr = e.getEventTime() != null ? " 🕐 " + e.getEventTime().format(TIME_FMT) : "";
                sb.append(getColorEmoji(e.getColor())).append(" ").append(e.getTitle()).append(timeStr).append("\n");
                rows.add(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text("👁 Просмотр")
                                .callbackData("EVT_VIEW_" + e.getId())
                                .build()
                ));
            }
        }

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text("➕ Добавить событие")
                        .callbackData("CAL_ADD")
                        .build()
        ));

        Map<String, String> temp = userTempData.computeIfAbsent(telegramId, k -> new HashMap<>());
        temp.put("selectedDate", date.toString());

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text("📅 Календарь")
                        .callbackData("MENU_MAIN")
                        .build()
        ));

        editMessage(telegramId, messageId, sb.toString(),
                InlineKeyboardMarkup.builder().keyboard(rows).build());
    }

    private void handleAddDaySelection(Long telegramId, Integer messageId, String data) {
        LocalDate date = LocalDate.parse(data.split("_")[2]);
        Map<String, String> temp = userTempData.computeIfAbsent(telegramId, k -> new HashMap<>());
        temp.put("selectedDate", date.toString());
        showTimePicker(telegramId, messageId);
    }

    private void showTimePicker(Long telegramId, Integer messageId) {
        Map<String, String> temp = userTempData.get(telegramId);
        LocalDate date = temp != null ? LocalDate.parse(temp.get("selectedDate")) : LocalDate.now();

        List<InlineKeyboardRow> rows = new ArrayList<>();

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("📅 " + date.format(DATE_FMT)).callbackData("NONE").build()
        ));

        InlineKeyboardRow hourHeader = new InlineKeyboardRow();
        hourHeader.add(InlineKeyboardButton.builder().text("🕐 Час:").callbackData("NONE").build());
        rows.add(hourHeader);

        InlineKeyboardRow hourRow1 = new InlineKeyboardRow();
        InlineKeyboardRow hourRow2 = new InlineKeyboardRow();
        for (int h = 0; h < 24; h++) {
            String label = String.format("%02d", h);
            InlineKeyboardButton btn = InlineKeyboardButton.builder()
                    .text(label)
                    .callbackData("CREATE_TIME_H_" + label)
                    .build();
            if (h < 12) hourRow1.add(btn);
            else hourRow2.add(btn);
        }
        rows.add(hourRow1);
        rows.add(hourRow2);

        InlineKeyboardRow minuteHeader = new InlineKeyboardRow();
        minuteHeader.add(InlineKeyboardButton.builder().text("⏱ Минуты:").callbackData("NONE").build());
        rows.add(minuteHeader);

        InlineKeyboardRow minuteRow = new InlineKeyboardRow();
        for (int m = 0; m < 60; m += 5) {
            minuteRow.add(InlineKeyboardButton.builder()
                    .text(String.format("%02d", m))
                    .callbackData("CREATE_TIME_M_" + String.format("%02d", m))
                    .build());
        }
        rows.add(minuteRow);

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("⏭ Без времени").callbackData("CREATE_SKIP_TIME").build()
        ));

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("⬅️ Другая дата").callbackData("CREATE_BACK_DATE").build()
        ));

        editMessage(telegramId, messageId, "🕐 Выберите время события:",
                InlineKeyboardMarkup.builder().keyboard(rows).build());
    }

    private void handleTimeSelection(Long telegramId, Integer messageId, String data) {
        String[] parts = data.split("_");
        String type = parts[2];
        String value = parts[3];

        Map<String, String> temp = userTempData.get(telegramId);
        if (temp == null) return;

        if ("H".equals(type)) {
            temp.put("selectedHour", value);
            editMessage(telegramId, messageId, "🕐 Час: " + value + ". Теперь выберите минуты:",
                    buildMinutePicker());
        } else if ("M".equals(type)) {
            String hour = temp.get("selectedHour");
            if (hour == null) return;
            LocalTime time = LocalTime.of(Integer.parseInt(hour), Integer.parseInt(value));
            temp.put("selectedTime", time.toString());
            showColorPicker(telegramId, messageId);
        }
    }

    private InlineKeyboardMarkup buildMinutePicker() {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        InlineKeyboardRow minuteRow = new InlineKeyboardRow();
        for (int m = 0; m < 60; m += 5) {
            minuteRow.add(InlineKeyboardButton.builder()
                    .text(String.format("%02d", m))
                    .callbackData("CREATE_TIME_M_" + String.format("%02d", m))
                    .build());
        }
        rows.add(minuteRow);
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    private void handleSkipTime(Long telegramId, Integer messageId) {
        Map<String, String> temp = userTempData.get(telegramId);
        if (temp != null) temp.remove("selectedTime");
        showColorPicker(telegramId, messageId);
    }

    private void handleBackToDateSelection(Long telegramId, Integer messageId) {
        Map<String, String> temp = userTempData.get(telegramId);
        LocalDate date = temp != null && temp.containsKey("selectedDate")
                ? LocalDate.parse(temp.get("selectedDate"))
                : LocalDate.now();
        showCalendar(telegramId, date, true);
    }

    private void showColorPicker(Long telegramId, Integer messageId) {
        List<InlineKeyboardRow> rows = new ArrayList<>();

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("🔵 Синий").callbackData("CREATE_COLOR_BLUE").build(),
                InlineKeyboardButton.builder().text("🔴 Красный").callbackData("CREATE_COLOR_RED").build(),
                InlineKeyboardButton.builder().text("🟢 Зелёный").callbackData("CREATE_COLOR_GREEN").build()
        ));

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("🟡 Жёлтый").callbackData("CREATE_COLOR_YELLOW").build(),
                InlineKeyboardButton.builder().text("🟣 Фиолет").callbackData("CREATE_COLOR_PURPLE").build(),
                InlineKeyboardButton.builder().text("🟠 Оранж").callbackData("CREATE_COLOR_ORANGE").build()
        ));

        editMessage(telegramId, messageId, "🎨 Выберите цвет события:",
                InlineKeyboardMarkup.builder().keyboard(rows).build());
    }

    private void handleColorSelection(Long telegramId, Integer messageId, String data) {
        String color = data.substring("CREATE_COLOR_".length());
        Map<String, String> temp = userTempData.get(telegramId);
        if (temp != null) {
            temp.put("color", color);
            showReminderPicker(telegramId, messageId);
        }
    }

    private void showReminderPicker(Long telegramId, Integer messageId) {
        List<InlineKeyboardRow> rows = new ArrayList<>();

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("🔕 Без напоминания").callbackData("CREATE_REM_0").build()
        ));
        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("5 мин").callbackData("CREATE_REM_5").build(),
                InlineKeyboardButton.builder().text("15 мин").callbackData("CREATE_REM_15").build(),
                InlineKeyboardButton.builder().text("30 мин").callbackData("CREATE_REM_30").build()
        ));
        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("1 час").callbackData("CREATE_REM_60").build(),
                InlineKeyboardButton.builder().text("2 часа").callbackData("CREATE_REM_120").build(),
                InlineKeyboardButton.builder().text("1 день").callbackData("CREATE_REM_1440").build()
        ));

        editMessage(telegramId, messageId, "🔔 Напоминание заранее:",
                InlineKeyboardMarkup.builder().keyboard(rows).build());
    }

    private void handleReminderSelection(Long telegramId, Integer messageId, String data) {
        int minutes = Integer.parseInt(data.split("_")[2]);
        Map<String, String> temp = userTempData.get(telegramId);
        if (temp != null) {
            temp.put("reminder", String.valueOf(minutes));
            createEventFromTempData(telegramId, messageId);
        }
    }

    private void handleEventView(Long telegramId, Integer messageId, String data) {
        Long eventId = Long.parseLong(data.split("_")[2]);
        EventDto event = eventService.getEvent(eventId, telegramId);

        String timeStr = event.getEventTime() != null ? " 🕐 " + event.getEventTime().format(TIME_FMT) : "";
        String reminderStr = event.getReminderMinutesBefore() != null && event.getReminderMinutesBefore() > 0
                ? formatReminder(event.getReminderMinutesBefore())
                : "Без напоминания";

        String text = String.format("%s %s\n\n📅 %s%s\n🎨 %s\n🔔 %s\n\n📝 %s",
                getColorEmoji(event.getColor()),
                event.getTitle(),
                event.getEventDate().format(DATE_FMT),
                timeStr,
                getColorName(event.getColor()),
                reminderStr,
                event.getDescription() != null ? event.getDescription() : "Без описания");

        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("🗑 Удалить").callbackData("EVT_DELETE_" + eventId).build()
        ));

        Map<String, String> temp = userTempData.computeIfAbsent(telegramId, k -> new HashMap<>());
        String selectedDate = temp.getOrDefault("selectedDate", event.getEventDate().toString());
        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("⬅️ Назад").callbackData("EVT_BACK_" + selectedDate).build()
        ));

        editMessage(telegramId, messageId, text,
                InlineKeyboardMarkup.builder().keyboard(rows).build());
    }

    private void handleEventDelete(Long telegramId, Integer messageId, String data) {
        Long eventId = Long.parseLong(data.split("_")[2]);
        eventService.deleteEvent(eventId, telegramId);
        editMessage(telegramId, messageId, "✅ Событие удалено.", CalendarBuilder.buildCalendar(LocalDate.now(), "CAL"));
    }

    private void handleBackToDate(Long telegramId, Integer messageId, String data) {
        String dateStr = data.split("_", 3)[2];
        LocalDate date = LocalDate.parse(dateStr);
        handleDaySelection(telegramId, messageId, "CAL_DAY_" + dateStr);
    }

    private void startCreateEvent(Long telegramId, Integer messageId) {
        Map<String, String> temp = userTempData.computeIfAbsent(telegramId, k -> new HashMap<>());
        temp.put("selectedDate", LocalDate.now().toString());
        userStates.put(telegramId, "CREATE_TITLE");
        editMessage(telegramId, messageId, "✏️ Введите название события:", null);
    }

    private void handleStateInput(Long telegramId, String text, String state) {
        Map<String, String> temp = userTempData.computeIfAbsent(telegramId, k -> new HashMap<>());

        switch (state) {
            case "CREATE_TITLE" -> {
                temp.put("title", text);
                userStates.put(telegramId, "CREATE_DESC");
                sendMessage(telegramId, "📝 Введите описание (или /skip):", null);
            }
            case "CREATE_DESC" -> {
                if (!"/skip".equals(text)) {
                    temp.put("description", text);
                }
                userStates.remove(telegramId);
                LocalDate selected = LocalDate.parse(temp.get("selectedDate"));
                showCalendar(telegramId, selected, true);
            }
            default -> {
                userStates.remove(telegramId);
                sendMessage(telegramId, "🤔 Неизвестное состояние. /start", CalendarBuilder.buildCalendar(LocalDate.now(), "CAL"));
            }
        }
    }

    private void createEventFromTempData(Long telegramId, Integer messageId) {
        Map<String, String> temp = userTempData.get(telegramId);
        if (temp == null) return;

        LocalDate date = LocalDate.parse(temp.get("selectedDate"));
        LocalTime time = temp.containsKey("selectedTime") ? LocalTime.parse(temp.get("selectedTime")) : null;
        int reminder = Integer.parseInt(temp.getOrDefault("reminder", "0"));

        EventDto event = eventService.createEvent(telegramId,
                temp.get("title"),
                temp.get("description"),
                date,
                time,
                temp.get("color"),
                reminder > 0 ? reminder : null);

        userStates.remove(telegramId);
        userTempData.remove(telegramId);

        String timeStr = time != null ? " 🕐 " + time.format(TIME_FMT) : "";
        sendMessage(telegramId, String.format("✅ Событие создано!\n\n%s %s\n📅 %s%s",
                getColorEmoji(event.getColor()), event.getTitle(), date.format(DATE_FMT), timeStr),
                CalendarBuilder.buildCalendar(date, "CAL"));
    }

    private String getColorEmoji(String color) {
        return switch (color) {
            case "RED" -> "🔴";
            case "GREEN" -> "🟢";
            case "YELLOW" -> "🟡";
            case "PURPLE" -> "🟣";
            case "ORANGE" -> "🟠";
            default -> "🔵";
        };
    }

    private String getColorName(String color) {
        return switch (color) {
            case "RED" -> "Красный";
            case "GREEN" -> "Зелёный";
            case "YELLOW" -> "Жёлтый";
            case "PURPLE" -> "Фиолетовый";
            case "ORANGE" -> "Оранжевый";
            default -> "Синий";
        };
    }

    private String formatReminder(int minutes) {
        if (minutes >= 1440) return (minutes / 1440) + " дн. до";
        if (minutes >= 60) return (minutes / 60) + " ч. до";
        return minutes + " мин. до";
    }

    public void sendMainMenu(Long telegramId, String text) {
        sendMessage(telegramId, text, CalendarBuilder.buildCalendar(LocalDate.now(), "CAL"));
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
        if (messageId == null) {
            sendMessage(chatId, text, keyboard);
            return;
        }
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
            sendMessage(chatId, text, keyboard);
        }
    }
}

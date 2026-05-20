package com.taskbot.handler;

import com.taskbot.dto.EventDto;
import com.taskbot.service.EventService;
import com.taskbot.util.CalendarBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventCreationHandler {

    private final TelegramClient telegramClient;
    private final EventService eventService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private static final String STATE_DATE = "CREATE_DATE";
    private static final String STATE_TITLE = "CREATE_TITLE";

    private final Map<Long, String> states = new ConcurrentHashMap<>();
    private final Map<Long, Map<String, String>> tempData = new ConcurrentHashMap<>();

    public boolean isInCreationFlow(Long telegramId) {
        String state = states.get(telegramId);
        return STATE_DATE.equals(state) || STATE_TITLE.equals(state);
    }

    public void cancelCreation(Long telegramId) {
        states.remove(telegramId);
        tempData.remove(telegramId);
    }

    public void startCreateEvent(Long telegramId, Integer messageId) {
        Map<String, String> temp = getTemp(telegramId);
        temp.put("selectedDate", LocalDate.now().toString());
        states.put(telegramId, STATE_DATE);
        updateCalendar(telegramId, messageId, temp);
    }

    public boolean handleCallback(Long telegramId, Integer messageId, String data) {
        if (!isInCreationFlow(telegramId)) return false;

        if (data.equals("CREATE_BACK_DATE")) {
            handleBackToDate(telegramId, messageId);
            return true;
        }
        if (data.startsWith("CREATE_DAY_")) {
            handleDateSelected(telegramId, messageId, data);
            return true;
        }
        if (data.equals("CREATE_PREV") || data.equals("CREATE_NEXT")) {
            handleCalendarNavigation(telegramId, messageId, data);
            return true;
        }
        if (data.startsWith("CREATE_TIME_")) {
            handleTimeSelection(telegramId, messageId, data);
            return true;
        }
        if (data.equals("CREATE_SAVE")) {
            promptTitle(telegramId, messageId);
            return true;
        }
        return false;
    }

    public boolean handleTextInput(Long telegramId, Integer messageId, String text) {
        String state = states.get(telegramId);
        if (!STATE_TITLE.equals(state)) return false;

        handleTitleInput(telegramId, messageId, text);
        return true;
    }

    private void updateCalendar(Long telegramId, Integer messageId, Map<String, String> temp) {
        LocalDate date = temp.containsKey("calendarDate")
                ? LocalDate.parse(temp.get("calendarDate"))
                : LocalDate.parse(temp.getOrDefault("selectedDate", LocalDate.now().toString()));

        String monthName = date.getMonth().getDisplayName(java.time.format.TextStyle.FULL, new java.util.Locale("ru"));
        String text = String.format("➕ Новое событие\n📅 %s %d\n\nВыберите дату:",
                monthName, date.getYear());

        editMessage(telegramId, messageId, text,
                CalendarBuilder.buildCalendar(date, CalendarBuilder.countEventsForUser(date, telegramId, eventService), "CREATE"));
    }

    private void handleBackToDate(Long telegramId, Integer messageId) {
        Map<String, String> temp = getTemp(telegramId);
        LocalDate date = temp.containsKey("selectedDate")
                ? LocalDate.parse(temp.get("selectedDate"))
                : LocalDate.now();
        temp.put("calendarDate", date.toString());
        updateCalendar(telegramId, messageId, temp);
    }

    private void handleCalendarNavigation(Long telegramId, Integer messageId, String data) {
        Map<String, String> temp = getTemp(telegramId);
        LocalDate current = temp.containsKey("calendarDate")
                ? LocalDate.parse(temp.get("calendarDate"))
                : LocalDate.parse(temp.getOrDefault("selectedDate", LocalDate.now().toString()));

        current = data.endsWith("_PREV") ? current.minusMonths(1) : current.plusMonths(1);
        temp.put("calendarDate", current.toString());
        updateCalendar(telegramId, messageId, temp);
    }

    private void handleDateSelected(Long telegramId, Integer messageId, String data) {
        LocalDate date = LocalDate.parse(data.substring("CREATE_DAY_".length()));
        Map<String, String> temp = getTemp(telegramId);
        temp.put("selectedDate", date.toString());
        temp.remove("calendarDate");
        showQuickTimePicker(telegramId, messageId, date);
    }

    private void showQuickTimePicker(Long telegramId, Integer messageId, LocalDate date) {
        var rows = new java.util.ArrayList<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow>();

        rows.add(new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow(
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                        .text("📅 " + date.format(DATE_FMT))
                        .callbackData("CREATE_BACK_DATE")
                        .build()
        ));

        rows.add(new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow(
                btn("08:00", "CREATE_TIME_08:00"), btn("09:00", "CREATE_TIME_09:00"),
                btn("10:00", "CREATE_TIME_10:00"), btn("11:00", "CREATE_TIME_11:00")
        ));
        rows.add(new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow(
                btn("12:00", "CREATE_TIME_12:00"), btn("13:00", "CREATE_TIME_13:00"),
                btn("14:00", "CREATE_TIME_14:00"), btn("15:00", "CREATE_TIME_15:00")
        ));
        rows.add(new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow(
                btn("16:00", "CREATE_TIME_16:00"), btn("17:00", "CREATE_TIME_17:00"),
                btn("18:00", "CREATE_TIME_18:00"), btn("19:00", "CREATE_TIME_19:00")
        ));
        rows.add(new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow(
                btn("20:00", "CREATE_TIME_20:00"),
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                        .text("🕐 Свой час")
                        .callbackData("CREATE_TIME_CUSTOM")
                        .build(),
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                        .text("⏭ Без времени")
                        .callbackData("CREATE_TIME_SKIP")
                        .build()
        ));
        rows.add(new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow(
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                        .text("💾 Сохранить сейчас")
                        .callbackData("CREATE_SAVE")
                        .build()
        ));

        editMessage(telegramId, messageId,
                "🕐 " + date.format(DATE_FMT) + "\n\nВыберите время или пропустите:",
                InlineKeyboardMarkup.builder().keyboard(rows).build());
    }

    private void showCustomTimePicker(Long telegramId, Integer messageId) {
        var rows = new java.util.ArrayList<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow>();

        var hourHeader = new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow();
        hourHeader.add(btnDummy("🕐 Час:"));
        rows.add(hourHeader);

        var hourRow1 = new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow();
        var hourRow2 = new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow();
        for (int h = 0; h < 24; h++) {
            String label = String.format("%02d", h);
            var b = btn(label, "CREATE_TIME_H_" + label);
            if (h < 12) hourRow1.add(b);
            else hourRow2.add(b);
        }
        rows.add(hourRow1);
        rows.add(hourRow2);

        var minuteHeader = new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow();
        minuteHeader.add(btnDummy("⏱ Минуты:"));
        rows.add(minuteHeader);

        var minuteRow = new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow();
        for (int m = 0; m < 60; m += 5) {
            minuteRow.add(btn(String.format("%02d", m), "CREATE_TIME_M_" + String.format("%02d", m)));
        }
        rows.add(minuteRow);

        rows.add(new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow(
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                        .text("⬅️ Назад")
                        .callbackData("CREATE_BACK_DATE")
                        .build(),
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                        .text("⏭ Пропустить")
                        .callbackData("CREATE_SAVE")
                        .build()
        ));

        Map<String, String> temp = getTemp(telegramId);
        LocalDate date = LocalDate.parse(temp.get("selectedDate"));

        editMessage(telegramId, messageId,
                "🕐 " + date.format(DATE_FMT) + "\n\nВыберите час, затем минуты:",
                InlineKeyboardMarkup.builder().keyboard(rows).build());
    }

    private void handleTimeSelection(Long telegramId, Integer messageId, String data) {
        Map<String, String> temp = getTemp(telegramId);

        if (data.equals("CREATE_TIME_CUSTOM")) {
            showCustomTimePicker(telegramId, messageId);
            return;
        }

        if (data.equals("CREATE_TIME_SKIP")) {
            temp.remove("selectedTime");
            promptTitle(telegramId, messageId);
            return;
        }

        if (data.startsWith("CREATE_TIME_H_")) {
            String hour = data.substring("CREATE_TIME_H_".length());
            temp.put("selectedHour", hour);
            showMinutePickerOnly(telegramId, messageId, hour);
            return;
        }

        if (data.startsWith("CREATE_TIME_M_")) {
            String hour = temp.get("selectedHour");
            if (hour == null) return;
            String minute = data.substring("CREATE_TIME_M_".length());
            LocalTime time = LocalTime.of(Integer.parseInt(hour), Integer.parseInt(minute));
            temp.put("selectedTime", time.toString());
            promptTitle(telegramId, messageId);
            return;
        }

        LocalTime time = LocalTime.parse(data.substring("CREATE_TIME_".length()));
        temp.put("selectedTime", time.toString());
        promptTitle(telegramId, messageId);
    }

    private void showMinutePickerOnly(Long telegramId, Integer messageId, String hour) {
        var rows = new java.util.ArrayList<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow>();

        var header = new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow();
        header.add(btnDummy("🕐 Час: " + hour + " → выберите минуты:"));
        rows.add(header);

        var minuteRow = new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow();
        for (int m = 0; m < 60; m += 5) {
            minuteRow.add(btn(String.format("%02d", m), "CREATE_TIME_M_" + String.format("%02d", m)));
        }
        rows.add(minuteRow);

        rows.add(new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow(
                btn("⬅️ Назад", "CREATE_TIME_CUSTOM")
        ));

        editMessage(telegramId, messageId,
                "🕐 Час: " + hour + ". Теперь выберите минуты:",
                InlineKeyboardMarkup.builder().keyboard(rows).build());
    }

    private void promptTitle(Long telegramId, Integer messageId) {
        states.put(telegramId, STATE_TITLE);

        Map<String, String> temp = getTemp(telegramId);
        LocalDate date = LocalDate.parse(temp.get("selectedDate"));
        String timeInfo = temp.containsKey("selectedTime")
                ? " в " + LocalTime.parse(temp.get("selectedTime")).format(TIME_FMT)
                : " (без времени)";

        var rows = new java.util.ArrayList<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow>();
        rows.add(new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow(
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                        .text("✖️ Отмена")
                        .callbackData("MENU_MAIN")
                        .build()
        ));

        editMessage(telegramId, messageId,
                "📅 " + date.format(DATE_FMT) + timeInfo + "\n\n✏️ Опишите событие одной фразой (название):",
                InlineKeyboardMarkup.builder().keyboard(rows).build());
    }

    private void handleTitleInput(Long telegramId, Integer messageId, String title) {
        Map<String, String> temp = tempData.get(telegramId);
        if (temp == null || title == null || title.isBlank()) return;

        temp.put("title", title.trim());
        createEvent(telegramId, messageId);
    }

    private void createEvent(Long telegramId, Integer messageId) {
        Map<String, String> temp = tempData.get(telegramId);
        if (temp == null) return;

        LocalDate date = LocalDate.parse(temp.get("selectedDate"));
        LocalTime time = temp.containsKey("selectedTime") ? LocalTime.parse(temp.get("selectedTime")) : null;
        String title = temp.getOrDefault("title", "Без названия");

        EventDto event = eventService.createEvent(telegramId,
                title, null, date, time, "BLUE", null);

        states.remove(telegramId);
        tempData.remove(telegramId);

        String timeStr = time != null ? " 🕐 " + time.format(TIME_FMT) : "";
        String msg = String.format("✅ Событие создано!\n\n🔵 %s\n📅 %s%s",
                event.getTitle(), date.format(DATE_FMT), timeStr);

        editMessage(telegramId, messageId, msg, null);
    }

    public void sendCreationSuccessMessage(Long telegramId, EventDto event) {
        String timeStr = event.getEventTime() != null ? " 🕐 " + event.getEventTime().format(TIME_FMT) : "";
        String msg = String.format("✅ Событие создано!\n\n🔵 %s\n📅 %s%s",
                event.getTitle(), event.getEventDate().format(DATE_FMT), timeStr);

        try {
            SendMessage message = SendMessage.builder()
                    .chatId(telegramId)
                    .text(msg)
                    .build();
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send creation success msg", e);
        }
    }

    private Map<String, String> getTemp(Long telegramId) {
        return tempData.computeIfAbsent(telegramId, k -> new ConcurrentHashMap<>());
    }

    private static org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton btn(
            String text, String callbackData) {
        return org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                .text(text)
                .callbackData(callbackData)
                .build();
    }

    private static org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton btnDummy(
            String text) {
        return org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                .text(text)
                .callbackData("NONE")
                .build();
    }

    private void editMessage(Long chatId, Integer messageId, String text, InlineKeyboardMarkup keyboard) {
        try {
            var msg = EditMessageText.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .text(text)
                    .replyMarkup(keyboard)
                    .build();
            telegramClient.execute(msg);
        } catch (TelegramApiException e) {
            log.error("Failed to edit message {}: {}", messageId, e.getMessage());
        }
    }
}

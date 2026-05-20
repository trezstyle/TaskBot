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

    /**
     * Start quick event creation from a voice message.
     * Pre-fills the title and puts user in date-selection state.
     */
    public void startQuickEventFromVoice(Long telegramId, String title) {
        Map<String, String> temp = getTemp(telegramId);
        temp.put("selectedDate", LocalDate.now().toString());
        temp.put("title", title);
        states.put(telegramId, STATE_DATE);
        // The user will see calendar from CalendarHandler.showCalendar
        // called after this method in TelegramBotService
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
        if (data.startsWith("CREATE_TIME_") || data.equals("CREATE_BACK_TIME")) {
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

        String monthName = date.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH);
String text = String.format("➕ New event\n📅 %s %d\n\nSelect date:",
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

        // Morning: 06-11
        rows.add(new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow(
                btn("06", "CREATE_TIME_H_06"), btn("07", "CREATE_TIME_H_07"),
                btn("08", "CREATE_TIME_H_08"), btn("09", "CREATE_TIME_H_09"),
                btn("10", "CREATE_TIME_H_10"), btn("11", "CREATE_TIME_H_11")
        ));
        // Day: 12-17
        rows.add(new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow(
                btn("12", "CREATE_TIME_H_12"), btn("13", "CREATE_TIME_H_13"),
                btn("14", "CREATE_TIME_H_14"), btn("15", "CREATE_TIME_H_15"),
                btn("16", "CREATE_TIME_H_16"), btn("17", "CREATE_TIME_H_17")
        ));
        // Evening: 18-23
        rows.add(new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow(
                btn("18", "CREATE_TIME_H_18"), btn("19", "CREATE_TIME_H_19"),
                btn("20", "CREATE_TIME_H_20"), btn("21", "CREATE_TIME_H_21"),
                btn("22", "CREATE_TIME_H_22"), btn("23", "CREATE_TIME_H_23")
        ));
        // Night + controls
        rows.add(new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow(
                btn("00", "CREATE_TIME_H_00"), btn("01", "CREATE_TIME_H_01"),
                btn("02", "CREATE_TIME_H_02"), btn("03", "CREATE_TIME_H_03"),
                btn("04", "CREATE_TIME_H_04"), btn("05", "CREATE_TIME_H_05")
        ));
        rows.add(new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow(
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                        .text("⏭ No time")
                        .callbackData("CREATE_TIME_SKIP")
                        .build()
        ));

        editMessage(telegramId, messageId,
                "🕐 " + date.format(DATE_FMT) + "\n\nSelect hour:",
                InlineKeyboardMarkup.builder().keyboard(rows).build());
    }

    private void showMinutePicker(Long telegramId, Integer messageId, String hour, LocalDate date) {
        var rows = new java.util.ArrayList<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow>();

        rows.add(new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow(
                btnDummy("🕐 " + hour + ":__  " + date.format(DATE_FMT))
        ));

        // :00, :15, :30, :45 — most common
        rows.add(new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow(
                btn(hour + ":00", "CREATE_TIME_" + hour + ":00"),
                btn(hour + ":15", "CREATE_TIME_" + hour + ":15"),
                btn(hour + ":30", "CREATE_TIME_" + hour + ":30"),
                btn(hour + ":45", "CREATE_TIME_" + hour + ":45")
        ));

        // :05, :10, :20, :25, :35, :40, :50, :55
        rows.add(new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow(
                btn(":05", "CREATE_TIME_" + hour + ":05"),
                btn(":10", "CREATE_TIME_" + hour + ":10"),
                btn(":20", "CREATE_TIME_" + hour + ":20"),
                btn(":25", "CREATE_TIME_" + hour + ":25")
        ));
        rows.add(new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow(
                btn(":35", "CREATE_TIME_" + hour + ":35"),
                btn(":40", "CREATE_TIME_" + hour + ":40"),
                btn(":50", "CREATE_TIME_" + hour + ":50"),
                btn(":55", "CREATE_TIME_" + hour + ":55")
        ));

        rows.add(new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow(
                btn("⬅️ Back to hours", "CREATE_BACK_TIME"),
                btn("⏭ No time", "CREATE_TIME_SKIP")
        ));

        editMessage(telegramId, messageId,
                "🕐 " + hour + ":__\n\nSelect minutes:",
                InlineKeyboardMarkup.builder().keyboard(rows).build());
    }

    private void handleTimeSelection(Long telegramId, Integer messageId, String data) {
        Map<String, String> temp = getTemp(telegramId);

        if (data.equals("CREATE_TIME_SKIP")) {
            temp.remove("selectedTime");
            promptTitle(telegramId, messageId);
            return;
        }

        if (data.equals("CREATE_BACK_TIME")) {
            LocalDate date = LocalDate.parse(temp.get("selectedDate"));
            showQuickTimePicker(telegramId, messageId, date);
            return;
        }

        if (data.startsWith("CREATE_TIME_H_")) {
            String hour = data.substring("CREATE_TIME_H_".length());
            temp.put("selectedHour", hour);
            LocalDate date = LocalDate.parse(temp.get("selectedDate"));
            showMinutePicker(telegramId, messageId, hour, date);
            return;
        }

        // CREATE_TIME_HH:MM — final time selection
        LocalTime time = LocalTime.parse(data.substring("CREATE_TIME_".length()));
        temp.put("selectedTime", time.toString());
        promptTitle(telegramId, messageId);
    }

    private void promptTitle(Long telegramId, Integer messageId) {
        states.put(telegramId, STATE_TITLE);

        Map<String, String> temp = getTemp(telegramId);
        LocalDate date = LocalDate.parse(temp.get("selectedDate"));
        String timeInfo = temp.containsKey("selectedTime")
                ? " at " + LocalTime.parse(temp.get("selectedTime")).format(TIME_FMT)
                : " (no time)";

        var rows = new java.util.ArrayList<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow>();
        rows.add(new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow(
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                        .text("✖️ Cancel")
                        .callbackData("MENU_MAIN")
                        .build(),
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                        .text("📋 List")
                        .callbackData("LIST_UPCOMING")
                        .build()
        ));

        editMessage(telegramId, messageId,
                "📅 " + date.format(DATE_FMT) + timeInfo + "\n\n✏️ Describe the event (title):",
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
        String title = temp.getOrDefault("title", "Untitled");

        EventDto event = eventService.createEvent(telegramId,
                title, null, date, time, "BLUE", null);

        states.remove(telegramId);
        tempData.remove(telegramId);

        String timeStr = time != null ? " 🕐 " + time.format(TIME_FMT) : "";
        String msg = String.format("✅ Event created!\n\n🔵 %s\n📅 %s%s",
                event.getTitle(), date.format(DATE_FMT), timeStr);

        try {
            SendMessage message = SendMessage.builder()
                    .chatId(telegramId)
                    .text(msg)
                    .build();
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send event creation confirmation", e);
        }
    }

    public void sendCreationSuccessMessage(Long telegramId, EventDto event) {
        String timeStr = event.getEventTime() != null ? " 🕐 " + event.getEventTime().format(TIME_FMT) : "";
        String msg = String.format("✅ Event created!\n\n🔵 %s\n📅 %s%s",
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
            if (messageId == null) {
                var msg = SendMessage.builder()
                        .chatId(chatId)
                        .text(text)
                        .replyMarkup(keyboard)
                        .build();
                telegramClient.execute(msg);
            } else {
                var msg = EditMessageText.builder()
                        .chatId(chatId)
                        .messageId(messageId)
                        .text(text)
                        .replyMarkup(keyboard)
                        .build();
                telegramClient.execute(msg);
            }
        } catch (TelegramApiException e) {
            log.error("Failed to edit/send message: {}", e.getMessage());
        }
    }
}

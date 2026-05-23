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
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventEditHandler {

    private final TelegramClient telegramClient;
    private final EventService eventService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    // Edit states
    private static final String STATE_EDIT_TITLE = "EDIT_TITLE";
    private static final String STATE_EDIT_DATE = "EDIT_DATE";
    private static final String STATE_EDIT_TIME = "EDIT_TIME";
    private static final String STATE_EDIT_COLOR = "EDIT_COLOR";
    private static final String STATE_EDIT_REMIND = "EDIT_REMIND";

    // Track which event is being edited and at which message
    private final Map<Long, Long> editingEventIds = new ConcurrentHashMap<>();
    private final Map<Long, Integer> editingMessageIds = new ConcurrentHashMap<>();
    private final Map<Long, String> editStates = new ConcurrentHashMap<>();
    private final Map<Long, String> editCalendarDates = new ConcurrentHashMap<>();

    public boolean isInEditFlow(Long telegramId) {
        String state = editStates.get(telegramId);
        return state != null;
    }

    public void cancelEdit(Long telegramId) {
        editingEventIds.remove(telegramId);
        editingMessageIds.remove(telegramId);
        editStates.remove(telegramId);
        editCalendarDates.remove(telegramId);
    }

    /**
     * Show the edit menu for an event.
     */
    public void showEditMenu(Long telegramId, Integer messageId, Long eventId) {
        EventDto event = eventService.getEvent(eventId, telegramId);

        editingEventIds.put(telegramId, eventId);
        editingMessageIds.put(telegramId, messageId);
        editStates.remove(telegramId);
        editCalendarDates.remove(telegramId);

        String timeStr = event.getEventTime() != null ? " 🕐 " + event.getEventTime().format(TIME_FMT) : "";
        String reminderStr = event.getReminderMinutesBefore() != null && event.getReminderMinutesBefore() > 0
                ? formatReminder(event.getReminderMinutesBefore())
                : "No reminder";

        String text = String.format("✏️ Edit event\n\n%s %s\n📅 %s%s\n🎨 %s\n🔔 %s\n\nWhat would you like to change?",
                getColorEmoji(event.getColor()),
                event.getTitle(),
                event.getEventDate().format(DATE_FMT),
                timeStr,
                getColorName(event.getColor()),
                reminderStr);

        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("📝 Title").callbackData("EVT_EDIT_TITLE_" + eventId).build(),
                InlineKeyboardButton.builder().text("📅 Date").callbackData("EVT_EDIT_DATE_" + eventId).build()
        ));
        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("🕐 Time").callbackData("EVT_EDIT_TIME_" + eventId).build(),
                InlineKeyboardButton.builder().text("🎨 Color").callbackData("EVT_EDIT_CLR_" + eventId).build()
        ));
        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("🔔 Reminder").callbackData("EVT_EDIT_REM_" + eventId).build()
        ));
        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("👁 View").callbackData("EVT_VIEW_" + eventId).build(),
                InlineKeyboardButton.builder().text("❌ Cancel").callbackData("EVT_VIEW_" + eventId).build()
        ));

        editMessage(telegramId, messageId, text, InlineKeyboardMarkup.builder().keyboard(rows).build());
    }

    public boolean handleCallback(Long telegramId, Integer messageId, String data) {
        if (data.startsWith("EVT_EDIT_TITLE_")) {
            handleEditTitle(telegramId, messageId, data);
            return true;
        }
        if (data.startsWith("EVT_EDIT_DATE_")) {
            handleEditDate(telegramId, messageId, data);
            return true;
        }
        if (data.startsWith("EVT_EDIT_TIME_")) {
            handleEditTime(telegramId, messageId, data);
            return true;
        }
        // EVT_EDIT_CLR_<id> — enter color edit for event
        if (data.startsWith("EVT_EDIT_CLR_")) {
            Long eventId = Long.parseLong(data.substring("EVT_EDIT_CLR_".length()));
            enterColorEdit(telegramId, messageId, eventId);
            return true;
        }
        // EVT_EDIT_REM_<id> — enter reminder edit for event
        if (data.startsWith("EVT_EDIT_REM_")) {
            Long eventId = Long.parseLong(data.substring("EVT_EDIT_REM_".length()));
            enterReminderEdit(telegramId, messageId, eventId);
            return true;
        }
        // EVT_EDIT_<id> — show edit menu
        if (data.startsWith("EVT_EDIT_")) {
            Long eventId = Long.parseLong(data.substring("EVT_EDIT_".length()));
            showEditMenu(telegramId, messageId, eventId);
            return true;
        }

        // Handle sub-flows
        String state = editStates.get(telegramId);
        if (state == null) return false;

        switch (state) {
            case STATE_EDIT_DATE:
                if (data.equals("EDIT_CAL_PREV") || data.equals("EDIT_CAL_NEXT")) {
                    handleEditCalendarNavigation(telegramId, messageId, data);
                    return true;
                }
                if (data.startsWith("EDIT_CAL_DAY_")) {
                    handleEditDateSelected(telegramId, messageId, data);
                    return true;
                }
                break;
            case STATE_EDIT_TIME:
                if (data.startsWith("EDIT_TIME_H_") || data.equals("EDIT_TIME_BACK_H")) {
                    handleEditTimeHour(telegramId, messageId, data);
                    return true;
                }
                if (data.startsWith("EDIT_TIME_") || data.equals("EDIT_TIME_SKIP")) {
                    handleEditTimeSelected(telegramId, messageId, data);
                    return true;
                }
                break;
            case STATE_EDIT_COLOR:
                if (data.startsWith("EVT_EDIT_COLOR_")) {
                    handleColorSelected(telegramId, messageId, data);
                    return true;
                }
                break;
            case STATE_EDIT_REMIND:
                if (data.startsWith("EVT_EDIT_REMIND_")) {
                    handleReminderSelected(telegramId, messageId, data);
                    return true;
                }
                break;
        }

        return false;
    }

    public boolean handleTextInput(Long telegramId, Integer messageId, String text) {
        String state = editStates.get(telegramId);
        if (!STATE_EDIT_TITLE.equals(state)) return false;

        Long eventId = editingEventIds.get(telegramId);
        if (eventId == null) return false;

        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            sendMessage(telegramId, "Title cannot be empty. Please enter a title:", null);
            return true;
        }
        if (trimmed.length() > 255) {
            trimmed = trimmed.substring(0, 255);
        }

        EventDto event = eventService.getEvent(eventId, telegramId);
        EventDto updateDto = EventDto.builder()
                .id(eventId)
                .title(trimmed)
                .build();
        eventService.updateEvent(eventId, telegramId, updateDto);

        editStates.remove(telegramId);
        sendMessage(telegramId, "✅ Title updated!", null);
        showEditMenu(telegramId, editingMessageIds.getOrDefault(telegramId, messageId), eventId);
        return true;
    }

    // --- Title edit ---
    private void handleEditTitle(Long telegramId, Integer messageId, String data) {
        Long eventId = Long.parseLong(data.substring("EVT_EDIT_TITLE_".length()));
        editingEventIds.put(telegramId, eventId);
        editingMessageIds.put(telegramId, messageId);
        editStates.put(telegramId, STATE_EDIT_TITLE);

        EventDto event = eventService.getEvent(eventId, telegramId);

        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("❌ Cancel").callbackData("EVT_EDIT_" + eventId).build()
        ));

        editMessage(telegramId, messageId,
                "✏️ Edit title\n\nCurrent title: " + event.getTitle() + "\n\nType the new title:",
                InlineKeyboardMarkup.builder().keyboard(rows).build());
    }

    // --- Date edit ---
    private void handleEditDate(Long telegramId, Integer messageId, String data) {
        Long eventId = Long.parseLong(data.substring("EVT_EDIT_DATE_".length()));
        editingEventIds.put(telegramId, eventId);
        editingMessageIds.put(telegramId, messageId);
        editStates.put(telegramId, STATE_EDIT_DATE);

        EventDto event = eventService.getEvent(eventId, telegramId);
        editCalendarDates.put(telegramId, event.getEventDate().toString());

        showDateCalendar(telegramId, messageId, event);
    }

    private void showDateCalendar(Long telegramId, Integer messageId, EventDto event) {
        LocalDate date = editCalendarDates.containsKey(telegramId)
                ? LocalDate.parse(editCalendarDates.get(telegramId))
                : event.getEventDate();

        String monthName = date.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH);
        String text = String.format("✏️ Edit date\n\n%s %s — %s %s\n\nSelect new date:",
                getColorEmoji(event.getColor()), event.getTitle(),
                monthName, date.getYear());

        editMessage(telegramId, messageId, text,
                CalendarBuilder.buildCalendar(date, CalendarBuilder.countEventsForUser(date, telegramId, eventService), "EDIT_CAL"));
    }

    private void handleEditCalendarNavigation(Long telegramId, Integer messageId, String data) {
        LocalDate current = editCalendarDates.containsKey(telegramId)
                ? LocalDate.parse(editCalendarDates.get(telegramId))
                : LocalDate.now();
        current = data.equals("EDIT_CAL_NEXT") ? current.plusMonths(1) : current.minusMonths(1);
        editCalendarDates.put(telegramId, current.toString());

        Long eventId = editingEventIds.get(telegramId);
        EventDto event = eventService.getEvent(eventId, telegramId);
        showDateCalendar(telegramId, messageId, event);
    }

    private void handleEditDateSelected(Long telegramId, Integer messageId, String data) {
        LocalDate newDate = LocalDate.parse(data.substring("EDIT_CAL_DAY_".length()));
        Long eventId = editingEventIds.get(telegramId);

        EventDto updateDto = EventDto.builder()
                .id(eventId)
                .eventDate(newDate)
                .build();
        // Reset reminderSent when date changes so reminder can fire again
        eventService.updateEvent(eventId, telegramId, updateDto);
        // Also clear reminderSent
        eventService.markReminderSentReset(eventId);

        editStates.remove(telegramId);
        editCalendarDates.remove(telegramId);
        sendMessage(telegramId, "✅ Date updated!", null);
        showEditMenu(telegramId, editingMessageIds.getOrDefault(telegramId, messageId), eventId);
    }

    // --- Time edit ---
    private void handleEditTime(Long telegramId, Integer messageId, String data) {
        Long eventId = Long.parseLong(data.substring("EVT_EDIT_TIME_".length()));
        editingEventIds.put(telegramId, eventId);
        editingMessageIds.put(telegramId, messageId);
        editStates.put(telegramId, STATE_EDIT_TIME);

        EventDto event = eventService.getEvent(eventId, telegramId);
        showTimePicker(telegramId, messageId, event);
    }

    private void showTimePicker(Long telegramId, Integer messageId, EventDto event) {
        var rows = new ArrayList<InlineKeyboardRow>();

        String currentTime = event.getEventTime() != null ? event.getEventTime().format(TIME_FMT) : "no time";
        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text("📅 " + event.getEventDate().format(DATE_FMT) + "  🕐 " + currentTime)
                        .callbackData("NONE")
                        .build()
        ));

        // Morning: 06-11
        rows.add(new InlineKeyboardRow(
                btn("06", "EDIT_TIME_H_06"), btn("07", "EDIT_TIME_H_07"),
                btn("08", "EDIT_TIME_H_08"), btn("09", "EDIT_TIME_H_09"),
                btn("10", "EDIT_TIME_H_10"), btn("11", "EDIT_TIME_H_11")
        ));
        // Day: 12-17
        rows.add(new InlineKeyboardRow(
                btn("12", "EDIT_TIME_H_12"), btn("13", "EDIT_TIME_H_13"),
                btn("14", "EDIT_TIME_H_14"), btn("15", "EDIT_TIME_H_15"),
                btn("16", "EDIT_TIME_H_16"), btn("17", "EDIT_TIME_H_17")
        ));
        // Evening: 18-23
        rows.add(new InlineKeyboardRow(
                btn("18", "EDIT_TIME_H_18"), btn("19", "EDIT_TIME_H_19"),
                btn("20", "EDIT_TIME_H_20"), btn("21", "EDIT_TIME_H_21"),
                btn("22", "EDIT_TIME_H_22"), btn("23", "EDIT_TIME_H_23")
        ));
        // Night: 00-05
        rows.add(new InlineKeyboardRow(
                btn("00", "EDIT_TIME_H_00"), btn("01", "EDIT_TIME_H_01"),
                btn("02", "EDIT_TIME_H_02"), btn("03", "EDIT_TIME_H_03"),
                btn("04", "EDIT_TIME_H_04"), btn("05", "EDIT_TIME_H_05")
        ));
        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text("⏭ Remove time")
                        .callbackData("EDIT_TIME_SKIP")
                        .build()
        ));
        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text("❌ Cancel")
                        .callbackData("EVT_EDIT_" + event.getId())
                        .build()
        ));

        editMessage(telegramId, messageId,
                "✏️ Edit time\n\nSelect hour:",
                InlineKeyboardMarkup.builder().keyboard(rows).build());
    }

    private void showMinutePicker(Long telegramId, Integer messageId, String hour, EventDto event) {
        var rows = new ArrayList<InlineKeyboardRow>();

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text("🕐 " + hour + ":__  " + event.getEventDate().format(DATE_FMT))
                        .callbackData("NONE")
                        .build()
        ));

        rows.add(new InlineKeyboardRow(
                btn(hour + ":00", "EDIT_TIME_" + hour + ":00"),
                btn(hour + ":15", "EDIT_TIME_" + hour + ":15"),
                btn(hour + ":30", "EDIT_TIME_" + hour + ":30"),
                btn(hour + ":45", "EDIT_TIME_" + hour + ":45")
        ));
        rows.add(new InlineKeyboardRow(
                btn(":05", "EDIT_TIME_" + hour + ":05"),
                btn(":10", "EDIT_TIME_" + hour + ":10"),
                btn(":20", "EDIT_TIME_" + hour + ":20"),
                btn(":25", "EDIT_TIME_" + hour + ":25")
        ));
        rows.add(new InlineKeyboardRow(
                btn(":35", "EDIT_TIME_" + hour + ":35"),
                btn(":40", "EDIT_TIME_" + hour + ":40"),
                btn(":50", "EDIT_TIME_" + hour + ":50"),
                btn(":55", "EDIT_TIME_" + hour + ":55")
        ));
        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("⬅️ Back to hours").callbackData("EDIT_TIME_BACK_H").build(),
                InlineKeyboardButton.builder().text("❌ Cancel").callbackData("EVT_EDIT_" + event.getId()).build()
        ));

        editMessage(telegramId, messageId,
                "🕐 " + hour + ":__\n\nSelect minutes:",
                InlineKeyboardMarkup.builder().keyboard(rows).build());
    }

    private void handleEditTimeHour(Long telegramId, Integer messageId, String data) {
        if (data.equals("EDIT_TIME_BACK_H")) {
            Long eventId = editingEventIds.get(telegramId);
            EventDto event = eventService.getEvent(eventId, telegramId);
            showTimePicker(telegramId, messageId, event);
            return;
        }
        String hour = data.substring("EDIT_TIME_H_".length());
        Long eventId = editingEventIds.get(telegramId);
        EventDto event = eventService.getEvent(eventId, telegramId);
        showMinutePicker(telegramId, messageId, hour, event);
    }

    private void handleEditTimeSelected(Long telegramId, Integer messageId, String data) {
        Long eventId = editingEventIds.get(telegramId);

        if (data.equals("EDIT_TIME_SKIP")) {
            // Remove time
            EventDto updateDto = EventDto.builder()
                    .id(eventId)
                    .eventTime(null)
                    .build();
            eventService.updateEvent(eventId, telegramId, updateDto);
            eventService.markReminderSentReset(eventId);

            editStates.remove(telegramId);
            sendMessage(telegramId, "✅ Time removed!", null);
            showEditMenu(telegramId, editingMessageIds.getOrDefault(telegramId, messageId), eventId);
            return;
        }

        // EDIT_TIME_HH:MM
        LocalTime time = LocalTime.parse(data.substring("EDIT_TIME_".length()));
        EventDto updateDto = EventDto.builder()
                .id(eventId)
                .eventTime(time)
                .build();
        eventService.updateEvent(eventId, telegramId, updateDto);
        eventService.markReminderSentReset(eventId);

        editStates.remove(telegramId);
        sendMessage(telegramId, "✅ Time updated!", null);
        showEditMenu(telegramId, editingMessageIds.getOrDefault(telegramId, messageId), eventId);
    }

    // --- Color edit ---
    private void enterColorEdit(Long telegramId, Integer messageId, Long eventId) {
        editingEventIds.put(telegramId, eventId);
        editingMessageIds.put(telegramId, messageId);
        editStates.put(telegramId, STATE_EDIT_COLOR);

        EventDto event = eventService.getEvent(eventId, telegramId);
        String timeStr = event.getEventTime() != null ? " 🕐 " + event.getEventTime().format(TIME_FMT) : "";
        String text = String.format("🎨 Edit color\n\n%s %s\n📅 %s%s\n\nChoose new color:",
                getColorEmoji(event.getColor()), event.getTitle(),
                event.getEventDate().format(DATE_FMT), timeStr);

        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.addAll(CalendarBuilder.buildColorPicker("EVT_EDIT_COLOR_").getKeyboard());
        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("❌ Cancel").callbackData("EVT_EDIT_" + eventId).build()
        ));

        editMessage(telegramId, messageId, text, InlineKeyboardMarkup.builder().keyboard(rows).build());
    }

    private void handleColorSelected(Long telegramId, Integer messageId, String data) {
        String color = data.substring("EVT_EDIT_COLOR_".length());
        Long eventId = editingEventIds.get(telegramId);

        EventDto updateDto = EventDto.builder()
                .id(eventId)
                .color(color)
                .build();
        eventService.updateEvent(eventId, telegramId, updateDto);

        editStates.remove(telegramId);
        sendMessage(telegramId, "✅ Color updated!", null);
        showEditMenu(telegramId, editingMessageIds.getOrDefault(telegramId, messageId), eventId);
    }

    // --- Reminder edit ---
    private void enterReminderEdit(Long telegramId, Integer messageId, Long eventId) {
        editingEventIds.put(telegramId, eventId);
        editingMessageIds.put(telegramId, messageId);
        editStates.put(telegramId, STATE_EDIT_REMIND);

        EventDto event = eventService.getEvent(eventId, telegramId);
        String timeStr = event.getEventTime() != null ? " 🕐 " + event.getEventTime().format(TIME_FMT) : "";
        String reminderStr = event.getReminderMinutesBefore() != null && event.getReminderMinutesBefore() > 0
                ? formatReminder(event.getReminderMinutesBefore())
                : "No reminder";

        String text = String.format("🔔 Edit reminder\n\n%s %s\n📅 %s%s\nCurrent: %s\n\nChoose new reminder:",
                getColorEmoji(event.getColor()), event.getTitle(),
                event.getEventDate().format(DATE_FMT), timeStr, reminderStr);

        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.addAll(CalendarBuilder.buildReminderPicker("EVT_EDIT_REMIND_").getKeyboard());
        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("❌ Cancel").callbackData("EVT_EDIT_" + eventId).build()
        ));

        editMessage(telegramId, messageId, text, InlineKeyboardMarkup.builder().keyboard(rows).build());
    }

    private void handleReminderSelected(Long telegramId, Integer messageId, String data) {
        String minutesStr = data.substring("EVT_EDIT_REMIND_".length());
        int minutes = Integer.parseInt(minutesStr);
        Long eventId = editingEventIds.get(telegramId);

        EventDto updateDto = EventDto.builder()
                .id(eventId)
                .reminderMinutesBefore(minutes > 0 ? minutes : null)
                .build();
        eventService.updateEvent(eventId, telegramId, updateDto);
        eventService.markReminderSentReset(eventId);

        editStates.remove(telegramId);
        sendMessage(telegramId, minutes > 0 ? "✅ Reminder updated!" : "✅ Reminder removed!", null);
        showEditMenu(telegramId, editingMessageIds.getOrDefault(telegramId, messageId), eventId);
    }

    // --- Utility ---

    private String getColorEmoji(String color) {
        if (color == null) return "🔵";
        return switch (color.toUpperCase()) {
            case "RED" -> "🔴";
            case "GREEN" -> "🟢";
            case "YELLOW" -> "🟡";
            case "PURPLE" -> "🟣";
            case "ORANGE" -> "🟠";
            default -> "🔵";
        };
    }

    private String getColorName(String color) {
        if (color == null) return "Blue";
        return switch (color.toUpperCase()) {
            case "RED" -> "Red";
            case "GREEN" -> "Green";
            case "YELLOW" -> "Yellow";
            case "PURPLE" -> "Purple";
            case "ORANGE" -> "Orange";
            default -> "Blue";
        };
    }

    private String formatReminder(int minutes) {
        if (minutes >= 1440) return (minutes / 1440) + "d before";
        if (minutes >= 60) return (minutes / 60) + "h before";
        return minutes + "m before";
    }

    private static InlineKeyboardButton btn(String text, String callbackData) {
        return InlineKeyboardButton.builder()
                .text(text)
                .callbackData(callbackData)
                .build();
    }

    private void editMessage(Long chatId, Integer messageId, String text, InlineKeyboardMarkup keyboard) {
        if (messageId == null) {
            sendMessage(chatId, text, keyboard);
            return;
        }
        try {
            EditMessageText msg = EditMessageText.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .text(text)
                    .replyMarkup(keyboard)
                    .build();
            telegramClient.execute(msg);
        } catch (TelegramApiException e) {
            log.error("Failed to edit message: {}", messageId, e);
            sendMessage(chatId, text, keyboard);
        }
    }

    private void sendMessage(Long chatId, String text, InlineKeyboardMarkup keyboard) {
        try {
            SendMessage msg = SendMessage.builder()
                    .chatId(chatId)
                    .text(text)
                    .replyMarkup(keyboard)
                    .build();
            telegramClient.execute(msg);
        } catch (TelegramApiException e) {
            log.error("Failed to send message to chat: {}", chatId, e);
        }
    }
}
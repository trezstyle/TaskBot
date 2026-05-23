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
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class CalendarHandler {

    private final TelegramClient telegramClient;
    private final EventService eventService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final Map<Long, String> calendarDates = new ConcurrentHashMap<>();
    private final Map<Long, String> selectedDates = new ConcurrentHashMap<>();

    public void showCalendar(Long telegramId, LocalDate date) {
        Map<LocalDate, Integer> eventCounts = CalendarBuilder.countEventsForUser(
                date, telegramId, eventService);

        String monthName = date.getMonth().getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH);
        String text = String.format("📅 %s %d\n\nTap a date to view events.\n【】 - today\n🟢 - free  🟡 - events  🔴 - 5+",
                monthName, date.getYear());

        sendMessage(telegramId, text, CalendarBuilder.buildCalendar(date, eventCounts, "CAL"));
    }

    public boolean handleCallback(Long telegramId, Integer messageId, String data) {
        if (data.equals("MENU_MAIN")) {
            calendarDates.remove(telegramId);
            selectedDates.remove(telegramId);
            showCalendar(telegramId, LocalDate.now());
            return true;
        }

        if (data.startsWith("LIST_DELETE_YES_")) {
            handleListDeleteConfirm(telegramId, messageId, data);
            return true;
        }

        if (data.startsWith("LIST_DELETE_")) {
            handleListDeletePrompt(telegramId, messageId, data);
            return true;
        }

        if (data.equals("LIST_PAST")) {
            showPastEvents(telegramId);
            return true;
        }

        if (data.startsWith("LIST_PAST_PAGE_")) {
            int page = Integer.parseInt(data.substring("LIST_PAST_PAGE_".length()));
            showPastEvents(telegramId, page);
            return true;
        }

        if (data.startsWith("LIST_PAST_DELETE_YES_")) {
            handlePastDeleteConfirm(telegramId, messageId, data);
            return true;
        }

        if (data.startsWith("LIST_PAST_DELETE_")) {
            handlePastDeletePrompt(telegramId, messageId, data);
            return true;
        }

        if (data.equals("LIST_PAST_CLEAR")) {
            handleClearPastPrompt(telegramId, messageId);
            return true;
        }

        if (data.equals("LIST_PAST_CLEAR_YES")) {
            handleClearPastConfirm(telegramId, messageId);
            return true;
        }

        if (data.equals("LIST_UPCOMING")) {
            showUpcomingEvents(telegramId);
            return true;
        }

        if (data.startsWith("LIST_PAGE_")) {
            int page = Integer.parseInt(data.substring("LIST_PAGE_".length()));
            showUpcomingEvents(telegramId, page);
            return true;
        }

        if (data.equals("CAL_PREV") || data.equals("CAL_NEXT")) {
            navigateMonth(telegramId, messageId, data.equals("CAL_NEXT"));
            return true;
        }

        if (data.startsWith("CAL_DAY_")) {
            handleDayClick(telegramId, messageId, data);
            return true;
        }

        if (data.startsWith("EVT_VIEW_")) {
            handleEventView(telegramId, messageId, data);
            return true;
        }

        if (data.startsWith("EVT_DELETE_")) {
            handleEventDelete(telegramId, messageId, data);
            return true;
        }

        if (data.startsWith("EVT_BACK_")) {
            handleBackToDay(telegramId, messageId, data);
            return true;
        }

        return false;
    }

    private void navigateMonth(Long telegramId, Integer messageId, boolean next) {
        LocalDate current = calendarDates.containsKey(telegramId)
                ? LocalDate.parse(calendarDates.get(telegramId))
                : LocalDate.now();
        current = next ? current.plusMonths(1) : current.minusMonths(1);
        calendarDates.put(telegramId, current.toString());

        Map<LocalDate, Integer> eventCounts = CalendarBuilder.countEventsForUser(
                current, telegramId, eventService);

        String monthName = current.getMonth().getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH);
        String text = String.format("📅 %s %d\n\nTap a date to view events.\n【】 - today\n🟢 - free  🟡 - events  🔴 - 5+",
                monthName, current.getYear());

        editMessage(telegramId, messageId, text,
                CalendarBuilder.buildCalendar(current, eventCounts, "CAL"));
    }

    private void handleDayClick(Long telegramId, Integer messageId, String data) {
        LocalDate date = LocalDate.parse(data.substring("CAL_DAY_".length()));
        selectedDates.put(telegramId, date.toString());

        List<EventDto> events = eventService.getEventsByDate(telegramId, date);

        StringBuilder sb = new StringBuilder();
        sb.append("📅 ").append(date.format(DATE_FMT)).append("\n\n");

        List<InlineKeyboardRow> rows = new ArrayList<>();

        if (events.isEmpty()) {
            sb.append("📭 No events this day.");
        } else {
            for (EventDto e : events) {
                String timeStr = e.getEventTime() != null ? " 🕐 " + e.getEventTime().format(
                        DateTimeFormatter.ofPattern("HH:mm")) : "";
                sb.append(getColorEmoji(e.getColor())).append(" ").append(e.getTitle()).append(timeStr).append("\n");
                rows.add(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text("👁 " + e.getTitle())
                                .callbackData("EVT_VIEW_" + e.getId())
                                .build()
                ));
            }
        }

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text("➕ Create")
                        .callbackData("CAL_ADD")
                        .build(),
                InlineKeyboardButton.builder()
                        .text("📋 List")
                        .callbackData("LIST_UPCOMING")
                        .build(),
                InlineKeyboardButton.builder()
                        .text("🏠 Start")
                        .callbackData("MENU_MAIN")
                        .build()
        ));

        editMessage(telegramId, messageId, sb.toString(),
                InlineKeyboardMarkup.builder().keyboard(rows).build());
    }

    private void handleEventView(Long telegramId, Integer messageId, String data) {
        Long eventId = Long.parseLong(data.substring("EVT_VIEW_".length()));
        EventDto event = eventService.getEvent(eventId, telegramId);

        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");
        String timeStr = event.getEventTime() != null ? " 🕐 " + event.getEventTime().format(timeFmt) : "";
        String reminderStr = event.getReminderMinutesBefore() != null && event.getReminderMinutesBefore() > 0
                ? formatReminder(event.getReminderMinutesBefore())
                : "No reminder";

        String text = String.format("%s %s\n\n📅 %s%s\n🎨 %s\n🔔 %s\n\n📝 %s",
                getColorEmoji(event.getColor()),
                event.getTitle(),
                event.getEventDate().format(DATE_FMT),
                timeStr,
                getColorName(event.getColor()),
                reminderStr,
                event.getDescription() != null ? event.getDescription() : "No description");

        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("✏️ Edit").callbackData("EVT_EDIT_" + eventId).build(),
                InlineKeyboardButton.builder().text("🗑 Delete").callbackData("EVT_DELETE_" + eventId).build()
        ));

        String selDate = selectedDates.getOrDefault(telegramId, event.getEventDate().toString());
        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("⬅️ Back").callbackData("EVT_BACK_" + selDate).build(),
                InlineKeyboardButton.builder().text("📋 List").callbackData("LIST_UPCOMING").build(),
                InlineKeyboardButton.builder().text("🏠 Start").callbackData("MENU_MAIN").build()
        ));

        editMessage(telegramId, messageId, text,
                InlineKeyboardMarkup.builder().keyboard(rows).build());
    }

    private void showUpcomingEvents(Long telegramId) {
        showUpcomingEvents(telegramId, 0);
    }

    private void showUpcomingEvents(Long telegramId, int page) {
        List<EventDto> events = eventService.getUpcomingEvents(telegramId);
        int pageSize = 5;
        int totalPages = Math.max(1, (int) Math.ceil((double) events.size() / pageSize));

        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        int from = page * pageSize;
        int to = Math.min(from + pageSize, events.size());
        List<EventDto> pageEvents = events.subList(from, to);

        StringBuilder sb = new StringBuilder();
        sb.append("📋 Upcoming events");

        if (events.isEmpty()) {
            sb.append("\n\n📭 No upcoming events.");
        } else {
            sb.append(" (").append(from + 1).append("-").append(to).append(" of ").append(events.size()).append(")\n\n");
            for (EventDto e : pageEvents) {
                String timeStr = e.getEventTime() != null ? " 🕐 " + e.getEventTime().format(
                        DateTimeFormatter.ofPattern("HH:mm")) : "";
                sb.append(getColorEmoji(e.getColor())).append(" ")
                        .append(e.getEventDate().format(DATE_FMT)).append(timeStr)
                        .append("\n   ").append(e.getTitle()).append("\n");
            }
        }

        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (EventDto e : pageEvents) {
            rows.add(new InlineKeyboardRow(
                    InlineKeyboardButton.builder()
                            .text("👁 " + e.getTitle())
                            .callbackData("EVT_VIEW_" + e.getId())
                            .build(),
                    InlineKeyboardButton.builder()
                            .text("🗑")
                            .callbackData("LIST_DELETE_" + e.getId())
                            .build()
            ));
        }

        List<InlineKeyboardButton> navRow = new ArrayList<>();
        if (page > 0) {
            navRow.add(InlineKeyboardButton.builder().text("⬅️ Back").callbackData("LIST_PAGE_" + (page - 1)).build());
        }
        navRow.add(InlineKeyboardButton.builder().text("🏠 Start").callbackData("MENU_MAIN").build());
        if (page < totalPages - 1) {
            navRow.add(InlineKeyboardButton.builder().text("➡️ Next").callbackData("LIST_PAGE_" + (page + 1)).build());
        }
        rows.add(new InlineKeyboardRow(navRow));

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("📂 Past events").callbackData("LIST_PAST").build()
        ));

        sendMessage(telegramId, sb.toString(), InlineKeyboardMarkup.builder().keyboard(rows).build());
    }

    private void showPastEvents(Long telegramId) {
        showPastEvents(telegramId, 0);
    }

    private void showPastEvents(Long telegramId, int page) {
        List<EventDto> events = eventService.getPastEvents(telegramId);
        int pageSize = 5;

        StringBuilder sb = new StringBuilder();
        sb.append("📂 Past events");

        if (events.isEmpty()) {
            sb.append("\n\n📭 No past events.");
            List<InlineKeyboardRow> rows = new ArrayList<>();
            rows.add(new InlineKeyboardRow(
                    InlineKeyboardButton.builder().text("📋 Upcoming").callbackData("LIST_UPCOMING").build(),
                    InlineKeyboardButton.builder().text("🏠 Start").callbackData("MENU_MAIN").build()
            ));
            sendMessage(telegramId, sb.toString(), InlineKeyboardMarkup.builder().keyboard(rows).build());
            return;
        }

        int totalPages = Math.max(1, (int) Math.ceil((double) events.size() / pageSize));
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        int from = page * pageSize;
        int to = Math.min(from + pageSize, events.size());
        List<EventDto> pageEvents = events.subList(from, to);

        sb.append(" (").append(from + 1).append("-").append(to).append(" of ").append(events.size()).append(")\n\n");
        for (EventDto e : pageEvents) {
            String timeStr = e.getEventTime() != null ? " 🕐 " + e.getEventTime().format(
                    DateTimeFormatter.ofPattern("HH:mm")) : "";
            sb.append(getColorEmoji(e.getColor())).append(" ")
                    .append(e.getEventDate().format(DATE_FMT)).append(timeStr)
                    .append("\n   ").append(e.getTitle()).append("\n");
        }

        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (EventDto e : pageEvents) {
            rows.add(new InlineKeyboardRow(
                    InlineKeyboardButton.builder()
                            .text("👁 " + e.getTitle())
                            .callbackData("EVT_VIEW_" + e.getId())
                            .build(),
                    InlineKeyboardButton.builder()
                            .text("🗑")
                            .callbackData("LIST_PAST_DELETE_" + e.getId())
                            .build()
            ));
        }

        List<InlineKeyboardButton> navRow = new ArrayList<>();
        if (page > 0) {
            navRow.add(InlineKeyboardButton.builder().text("⬅️ Back").callbackData("LIST_PAST_PAGE_" + (page - 1)).build());
        }
        navRow.add(InlineKeyboardButton.builder().text("📋 Upcoming").callbackData("LIST_UPCOMING").build());
        if (page < totalPages - 1) {
            navRow.add(InlineKeyboardButton.builder().text("➡️ Next").callbackData("LIST_PAST_PAGE_" + (page + 1)).build());
        }
        rows.add(new InlineKeyboardRow(navRow));

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("🗑 Clear all past").callbackData("LIST_PAST_CLEAR").build(),
                InlineKeyboardButton.builder().text("🏠 Start").callbackData("MENU_MAIN").build()
        ));

        sendMessage(telegramId, sb.toString(), InlineKeyboardMarkup.builder().keyboard(rows).build());
    }

    private void handlePastDeletePrompt(Long telegramId, Integer messageId, String data) {
        Long eventId = Long.parseLong(data.substring("LIST_PAST_DELETE_".length()));
        EventDto event = eventService.getEvent(eventId, telegramId);

        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");
        String timeStr = event.getEventTime() != null ? " 🕐 " + event.getEventTime().format(timeFmt) : "";
        String text = String.format("🗑 Delete event?\n\n%s %s\n📅 %s%s",
                getColorEmoji(event.getColor()), event.getTitle(),
                event.getEventDate().format(DATE_FMT), timeStr);

        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("✅ Yes, delete").callbackData("LIST_PAST_DELETE_YES_" + eventId).build(),
                InlineKeyboardButton.builder().text("❌ Cancel").callbackData("LIST_PAST").build()
        ));

        editMessage(telegramId, messageId, text, InlineKeyboardMarkup.builder().keyboard(rows).build());
    }

    private void handlePastDeleteConfirm(Long telegramId, Integer messageId, String data) {
        Long eventId = Long.parseLong(data.substring("LIST_PAST_DELETE_YES_".length()));
        EventDto event = eventService.getEvent(eventId, telegramId);
        eventService.deleteEvent(eventId, telegramId);
        sendMessage(telegramId, "🗑 Deleted: " + event.getTitle(), null);
        showPastEvents(telegramId);
    }

    private void handleClearPastPrompt(Long telegramId, Integer messageId) {
        int count = eventService.getPastEvents(telegramId).size();
        String text = String.format("⚠️ Clear ALL past events?\n\n%d past event(s) will be permanently deleted.\n\nThis cannot be undone!", count);

        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("✅ Yes, clear all").callbackData("LIST_PAST_CLEAR_YES").build(),
                InlineKeyboardButton.builder().text("❌ Cancel").callbackData("LIST_PAST").build()
        ));

        editMessage(telegramId, messageId, text, InlineKeyboardMarkup.builder().keyboard(rows).build());
    }

    private void handleClearPastConfirm(Long telegramId, Integer messageId) {
        int count = eventService.deleteAllPastEvents(telegramId);
        sendMessage(telegramId, String.format("🗑 Cleared %d past event(s).", count), null);
        showPastEvents(telegramId);
    }

    private void handleEventDelete(Long telegramId, Integer messageId, String data) {
        Long eventId = Long.parseLong(data.substring("EVT_DELETE_".length()));
        EventDto event = eventService.getEvent(eventId, telegramId);
        eventService.deleteEvent(eventId, telegramId);
        sendMessage(telegramId, "🗑 Deleted: " + event.getTitle(), null);
        calendarDates.remove(telegramId);
        showCalendar(telegramId, LocalDate.now());
    }

    private void handleListDeletePrompt(Long telegramId, Integer messageId, String data) {
        Long eventId = Long.parseLong(data.substring("LIST_DELETE_".length()));
        EventDto event = eventService.getEvent(eventId, telegramId);

        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");
        String timeStr = event.getEventTime() != null ? " 🕐 " + event.getEventTime().format(timeFmt) : "";
        String text = String.format("🗑 Delete event?\n\n%s %s\n📅 %s%s",
                getColorEmoji(event.getColor()), event.getTitle(),
                event.getEventDate().format(DATE_FMT), timeStr);

        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("✅ Yes, delete").callbackData("LIST_DELETE_YES_" + eventId).build(),
                InlineKeyboardButton.builder().text("❌ Cancel").callbackData("LIST_UPCOMING").build()
        ));

        editMessage(telegramId, messageId, text, InlineKeyboardMarkup.builder().keyboard(rows).build());
    }

    private void handleListDeleteConfirm(Long telegramId, Integer messageId, String data) {
        Long eventId = Long.parseLong(data.substring("LIST_DELETE_YES_".length()));
        EventDto event = eventService.getEvent(eventId, telegramId);
        eventService.deleteEvent(eventId, telegramId);
        sendMessage(telegramId, "🗑 Deleted: " + event.getTitle(), null);
        showUpcomingEvents(telegramId);
    }

    private void handleBackToDay(Long telegramId, Integer messageId, String data) {
        String dateStr = data.substring("EVT_BACK_".length());
        LocalDate date = LocalDate.parse(dateStr);
        handleDayClick(telegramId, messageId, "CAL_DAY_" + dateStr);
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
}

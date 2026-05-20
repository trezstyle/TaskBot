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

        String monthName = date.getMonth().getDisplayName(java.time.format.TextStyle.FULL, new Locale("ru"));
        String text = String.format("📅 %s %d\n\nНажмите на дату для просмотра событий.\n【】 - сегодня\n• - есть события",
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

        String monthName = current.getMonth().getDisplayName(java.time.format.TextStyle.FULL, new Locale("ru"));
        String text = String.format("📅 %s %d\n\nНажмите на дату для просмотра событий.\n【】 - сегодня\n• - есть события",
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
            sb.append("📭 Нет событий на этот день.");
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
                        .text("➕ Создать событие")
                        .callbackData("CAL_ADD")
                        .build()
        ));

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text("📅 Календарь")
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

        String selDate = selectedDates.getOrDefault(telegramId, event.getEventDate().toString());
        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("⬅️ Назад").callbackData("EVT_BACK_" + selDate).build()
        ));

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("📅 Календарь").callbackData("MENU_MAIN").build()
        ));

        editMessage(telegramId, messageId, text,
                InlineKeyboardMarkup.builder().keyboard(rows).build());
    }

    private void handleEventDelete(Long telegramId, Integer messageId, String data) {
        Long eventId = Long.parseLong(data.substring("EVT_DELETE_".length()));
        eventService.deleteEvent(eventId, telegramId);
        calendarDates.remove(telegramId);
        showCalendar(telegramId, LocalDate.now());
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

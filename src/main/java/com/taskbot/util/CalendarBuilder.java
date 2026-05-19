package com.taskbot.util;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CalendarBuilder {

    private CalendarBuilder() {}

    public static InlineKeyboardMarkup buildCalendar(LocalDate currentDate, String actionPrefix) {
        YearMonth yearMonth = YearMonth.from(currentDate);
        LocalDate today = LocalDate.now();

        List<InlineKeyboardRow> rows = new ArrayList<>();

        // Header: month/year with navigation
        String monthName = yearMonth.getMonth().getDisplayName(TextStyle.FULL, new Locale("ru"));
        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text("◀️")
                        .callbackData(actionPrefix + "_PREV_MONTH")
                        .build(),
                InlineKeyboardButton.builder()
                        .text(monthName + " " + yearMonth.getYear())
                        .callbackData("noop")
                        .build(),
                InlineKeyboardButton.builder()
                        .text("▶️")
                        .callbackData(actionPrefix + "_NEXT_MONTH")
                        .build()
        ));

        // Weekday headers
        String[] weekdays = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};
        InlineKeyboardRow headerRow = new InlineKeyboardRow();
        for (String day : weekdays) {
            headerRow.add(InlineKeyboardButton.builder()
                    .text(day)
                    .callbackData("noop")
                    .build());
        }
        rows.add(headerRow);

        // Days grid
        LocalDate firstDay = yearMonth.atDay(1);
        int dayOfWeek = firstDay.getDayOfWeek().getValue(); // 1=Monday
        int daysInMonth = yearMonth.lengthOfMonth();

        InlineKeyboardRow weekRow = new InlineKeyboardRow();

        // Empty cells before first day
        for (int i = 1; i < dayOfWeek; i++) {
            weekRow.add(InlineKeyboardButton.builder()
                    .text(" ")
                    .callbackData("noop")
                    .build());
        }

        // Days
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = yearMonth.atDay(day);
            boolean isToday = date.equals(today);
            boolean isPast = date.isBefore(today);

            String text = String.valueOf(day);
            if (isToday) {
                text = "🔵" + day;
            } else if (isPast) {
                text = "❌" + day;
            }

            String callbackData = isPast ? "noop" : actionPrefix + "_SELECT_" + date.toString();

            weekRow.add(InlineKeyboardButton.builder()
                    .text(text)
                    .callbackData(callbackData)
                    .build());

            if (weekRow.size() == 7) {
                rows.add(weekRow);
                weekRow = new InlineKeyboardRow();
            }
        }

        // Last row
        if (!weekRow.isEmpty()) {
            rows.add(weekRow);
        }

        // Back button
        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text("⬅️ Назад")
                        .callbackData("MENU_MAIN")
                        .build()
        ));

        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    public static InlineKeyboardMarkup buildTimePicker(String actionPrefix) {
        List<InlineKeyboardRow> rows = new ArrayList<>();

        // Hours
        InlineKeyboardRow hourRow = new InlineKeyboardRow();
        for (int h = 8; h <= 20; h++) {
            hourRow.add(InlineKeyboardButton.builder()
                    .text(String.format("%02d", h))
                    .callbackData(actionPrefix + "_HOUR_" + h)
                    .build());
        }
        rows.add(hourRow);

        // Minutes
        InlineKeyboardRow minuteRow = new InlineKeyboardRow();
        for (int m = 0; m < 60; m += 15) {
            minuteRow.add(InlineKeyboardButton.builder()
                    .text(String.format(":%02d", m))
                    .callbackData(actionPrefix + "_MINUTE_" + m)
                    .build());
        }
        rows.add(minuteRow);

        // Cancel
        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text("❌ Отмена")
                        .callbackData("MENU_MAIN")
                        .build()
        ));

        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }
}

package com.taskbot.util;

import com.taskbot.dto.EventDto;
import com.taskbot.service.EventService;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.*;

public class CalendarBuilder {

    private static final String[] DAY_HEADERS = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};

    public static Map<LocalDate, Integer> countEventsForUser(LocalDate monthDate, Long telegramId, EventService eventService) {
        LocalDate start = monthDate.withDayOfMonth(1);
        LocalDate end = monthDate.withDayOfMonth(monthDate.lengthOfMonth());
        List<EventDto> events = eventService.getEventsInRange(telegramId, start, end);
        Map<LocalDate, Integer> counts = new HashMap<>();
        for (EventDto e : events) {
            counts.merge(e.getEventDate(), 1, Integer::sum);
        }
        return counts;
    }

    public static InlineKeyboardMarkup buildCalendar(LocalDate currentDate,
                                                      Map<LocalDate, Integer> eventCounts,
                                                      String callbackPrefix) {
        List<InlineKeyboardRow> rows = new ArrayList<>();

        YearMonth month = YearMonth.from(currentDate);
        String header = String.format("📅 %s %d",
                month.getMonth().getDisplayName(TextStyle.FULL, new Locale("ru")),
                month.getYear());

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("◀️").callbackData(callbackPrefix + "_PREV").build(),
                InlineKeyboardButton.builder().text(header).callbackData("NONE").build(),
                InlineKeyboardButton.builder().text("▶️").callbackData(callbackPrefix + "_NEXT").build()
        ));

        List<InlineKeyboardRow> dayHeaderRows = new ArrayList<>();
        InlineKeyboardRow dayHeader = new InlineKeyboardRow();
        for (String day : DAY_HEADERS) {
            dayHeader.add(InlineKeyboardButton.builder().text(day).callbackData("NONE").build());
        }
        rows.add(dayHeader);

        LocalDate firstDay = month.atDay(1);
        int dayOfWeek = firstDay.getDayOfWeek().getValue();
        int daysInMonth = month.lengthOfMonth();

        InlineKeyboardRow weekRow = new InlineKeyboardRow();
        for (int i = 1; i < dayOfWeek; i++) {
            weekRow.add(InlineKeyboardButton.builder().text(" ").callbackData("NONE").build());
        }

        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = firstDay.withDayOfMonth(day);
            int count = eventCounts.getOrDefault(date, 0);
            String label = count > 0 ? String.valueOf(day) + "•" : String.valueOf(day);

            if (date.equals(LocalDate.now())) {
                label = "【" + label + "】";
            }

            weekRow.add(InlineKeyboardButton.builder()
                    .text(label)
                    .callbackData(callbackPrefix + "_DAY_" + date.toString())
                    .build());

            if (dayOfWeek == 7 || day == daysInMonth) {
                rows.add(weekRow);
                weekRow = new InlineKeyboardRow();
                dayOfWeek = 0;
            }
            dayOfWeek++;
        }

        if (!weekRow.isEmpty()) {
            rows.add(weekRow);
        }

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("➕ Добавить событие").callbackData(callbackPrefix + "_ADD").build()
        ));

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("🏠 Главное меню").callbackData("MENU_MAIN").build()
        ));

        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    public static InlineKeyboardMarkup buildCalendar(LocalDate currentDate, String callbackPrefix) {
        return buildCalendar(currentDate, Collections.emptyMap(), callbackPrefix);
    }

    public static InlineKeyboardMarkup buildTimePicker(String callbackPrefix) {
        List<InlineKeyboardRow> rows = new ArrayList<>();

        InlineKeyboardRow hourHeader = new InlineKeyboardRow();
        hourHeader.add(InlineKeyboardButton.builder().text("🕐 Час:").callbackData("NONE").build());
        rows.add(hourHeader);

        InlineKeyboardRow hourRow1 = new InlineKeyboardRow();
        InlineKeyboardRow hourRow2 = new InlineKeyboardRow();
        for (int h = 0; h < 24; h++) {
            String label = String.format("%02d", h);
            InlineKeyboardButton btn = InlineKeyboardButton.builder()
                    .text(label)
                    .callbackData(callbackPrefix + "_HOUR_" + label)
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
                    .callbackData(callbackPrefix + "_MINUTE_" + String.format("%02d", m))
                    .build());
        }
        rows.add(minuteRow);

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("⬅️ Назад").callbackData("MENU_MAIN").build()
        ));

        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    public static InlineKeyboardMarkup buildColorPicker(String callbackPrefix) {
        List<InlineKeyboardRow> rows = new ArrayList<>();

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("🔵 Синий").callbackData(callbackPrefix + "_BLUE").build(),
                InlineKeyboardButton.builder().text("🔴 Красный").callbackData(callbackPrefix + "_RED").build(),
                InlineKeyboardButton.builder().text("🟢 Зелёный").callbackData(callbackPrefix + "_GREEN").build()
        ));

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("🟡 Жёлтый").callbackData(callbackPrefix + "_YELLOW").build(),
                InlineKeyboardButton.builder().text("🟣 Фиолет").callbackData(callbackPrefix + "_PURPLE").build(),
                InlineKeyboardButton.builder().text("🟠 Оранж").callbackData(callbackPrefix + "_ORANGE").build()
        ));

        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    public static InlineKeyboardMarkup buildReminderPicker(String callbackPrefix) {
        List<InlineKeyboardRow> rows = new ArrayList<>();

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("Без напоминания").callbackData(callbackPrefix + "_0").build()
        ));
        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("5 мин").callbackData(callbackPrefix + "_5").build(),
                InlineKeyboardButton.builder().text("15 мин").callbackData(callbackPrefix + "_15").build(),
                InlineKeyboardButton.builder().text("30 мин").callbackData(callbackPrefix + "_30").build()
        ));
        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("1 час").callbackData(callbackPrefix + "_60").build(),
                InlineKeyboardButton.builder().text("2 часа").callbackData(callbackPrefix + "_120").build(),
                InlineKeyboardButton.builder().text("1 день").callbackData(callbackPrefix + "_1440").build()
        ));

        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }
}

package com.taskbot.util;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.List;

public class TelegramMenuBuilder {

    private TelegramMenuBuilder() {}

    public static InlineKeyboardMarkup buildMainMenu() {
        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(
                        new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text("📋 Мои задачи")
                                        .callbackData("MENU_TASKS")
                                        .build(),
                                InlineKeyboardButton.builder()
                                        .text("📅 Встречи")
                                        .callbackData("MENU_MEETINGS")
                                        .build()
                        ),
                        new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text("👨‍⚕️ Врачи")
                                        .callbackData("MENU_DOCTORS")
                                        .build(),
                                InlineKeyboardButton.builder()
                                        .text("📝 Заметки")
                                        .callbackData("MENU_NOTES")
                                        .build()
                        ),
                        new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text("🔔 Напоминания")
                                        .callbackData("MENU_REMINDERS")
                                        .build(),
                                InlineKeyboardButton.builder()
                                        .text("⚙️ Настройки")
                                        .callbackData("MENU_SETTINGS")
                                        .build()
                        )
                ))
                .build();
    }

    public static InlineKeyboardMarkup buildTasksMenu() {
        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(
                        new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text("📋 Все задачи")
                                        .callbackData("TASKS_LIST")
                                        .build(),
                                InlineKeyboardButton.builder()
                                        .text("➕ Новая задача")
                                        .callbackData("TASKS_CREATE")
                                        .build()
                        ),
                        new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text("🟡 В процессе")
                                        .callbackData("TASKS_FILTER_IN_PROGRESS")
                                        .build(),
                                InlineKeyboardButton.builder()
                                        .text("✅ Выполненные")
                                        .callbackData("TASKS_FILTER_DONE")
                                        .build()
                        ),
                        new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text("⏰ Просроченные")
                                        .callbackData("TASKS_OVERDUE")
                                        .build()
                        ),
                        new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text("⬅️ Назад")
                                        .callbackData("MENU_MAIN")
                                        .build()
                        )
                ))
                .build();
    }

    public static InlineKeyboardMarkup buildTaskStatusKeyboard(Long taskId) {
        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(
                        new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text("🟡 В процессе")
                                        .callbackData("TASK_STATUS_" + taskId + "_IN_PROGRESS")
                                        .build(),
                                InlineKeyboardButton.builder()
                                        .text("✅ Выполнено")
                                        .callbackData("TASK_STATUS_" + taskId + "_DONE")
                                        .build()
                        ),
                        new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text("🗑 Удалить")
                                        .callbackData("TASK_DELETE_" + taskId)
                                        .build(),
                                InlineKeyboardButton.builder()
                                        .text("⬅️ Назад")
                                        .callbackData("TASKS_LIST")
                                        .build()
                        )
                ))
                .build();
    }

    public static InlineKeyboardMarkup buildMeetingsMenu() {
        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(
                        new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text("📅 Мои встречи")
                                        .callbackData("MEETINGS_LIST")
                                        .build(),
                                InlineKeyboardButton.builder()
                                        .text("➕ Новая встреча")
                                        .callbackData("MEETINGS_CREATE")
                                        .build()
                        ),
                        new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text("⬅️ Назад")
                                        .callbackData("MENU_MAIN")
                                        .build()
                        )
                ))
                .build();
    }

    public static InlineKeyboardMarkup buildNotesMenu() {
        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(
                        new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text("📝 Мои заметки")
                                        .callbackData("NOTES_LIST")
                                        .build(),
                                InlineKeyboardButton.builder()
                                        .text("➕ Новая заметка")
                                        .callbackData("NOTES_CREATE")
                                        .build()
                        ),
                        new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text("🔍 Поиск")
                                        .callbackData("NOTES_SEARCH")
                                        .build(),
                                InlineKeyboardButton.builder()
                                        .text("📂 Категории")
                                        .callbackData("NOTES_CATEGORIES")
                                        .build()
                        ),
                        new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text("⬅️ Назад")
                                        .callbackData("MENU_MAIN")
                                        .build()
                        )
                ))
                .build();
    }

    public static InlineKeyboardMarkup buildDoctorsMenu() {
        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(
                        new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text("👨‍⚕️ Мои записи")
                                        .callbackData("DOCTORS_LIST")
                                        .build(),
                                InlineKeyboardButton.builder()
                                        .text("➕ Новая запись")
                                        .callbackData("DOCTORS_CREATE")
                                        .build()
                        ),
                        new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text("⬅️ Назад")
                                        .callbackData("MENU_MAIN")
                                        .build()
                        )
                ))
                .build();
    }

    public static InlineKeyboardMarkup buildRemindersMenu() {
        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(
                        new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text("🔔 Мои напоминания")
                                        .callbackData("REMINDERS_LIST")
                                        .build()
                        ),
                        new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text("⬅️ Назад")
                                        .callbackData("MENU_MAIN")
                                        .build()
                        )
                ))
                .build();
    }

    public static InlineKeyboardMarkup buildSettingsMenu(String language, String timezone, boolean notifications) {
        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(
                        new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text("🌐 Язык: " + language.toUpperCase())
                                        .callbackData("SETTINGS_LANGUAGE")
                                        .build()
                        ),
                        new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text("🕐 Часовой пояс: " + timezone)
                                        .callbackData("SETTINGS_TIMEZONE")
                                        .build()
                        ),
                        new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text("🔔 Уведомления: " + (notifications ? "ВКЛ" : "ВЫКЛ"))
                                        .callbackData("SETTINGS_NOTIFICATIONS")
                                        .build()
                        ),
                        new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text("⬅️ Назад")
                                        .callbackData("MENU_MAIN")
                                        .build()
                        )
                ))
                .build();
    }
}

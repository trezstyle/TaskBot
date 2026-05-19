package com.taskbot.util;

import com.taskbot.entity.Task;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.ArrayList;
import java.util.List;

public class TelegramMenuBuilder {

    private TelegramMenuBuilder() {}

    public static InlineKeyboardMarkup buildMainMenu() {
        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(
                        new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text("Tasks")
                                        .callbackData("MENU_TASKS")
                                        .build(),
                                InlineKeyboardButton.builder()
                                        .text("Meetings")
                                        .callbackData("MENU_MEETINGS")
                                        .build()
                        ),
                        new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text("Notes")
                                        .callbackData("MENU_NOTES")
                                        .build(),
                                InlineKeyboardButton.builder()
                                        .text("Doctors")
                                        .callbackData("MENU_DOCTORS")
                                        .build()
                        ),
                        new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text("Reminders")
                                        .callbackData("MENU_REMINDERS")
                                        .build(),
                                InlineKeyboardButton.builder()
                                        .text("Settings")
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
                                        .text("My Tasks")
                                        .callbackData("TASKS_LIST")
                                        .build(),
                                InlineKeyboardButton.builder()
                                        .text("New Task")
                                        .callbackData("TASKS_CREATE")
                                        .build()
                        ),
                        new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text("By Status")
                                        .callbackData("TASKS_BY_STATUS")
                                        .build(),
                                InlineKeyboardButton.builder()
                                        .text("Overdue")
                                        .callbackData("TASKS_OVERDUE")
                                        .build()
                        ),
                        new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text("Back to Menu")
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
                                        .text("TODO")
                                        .callbackData("TASK_STATUS_" + taskId + "_TODO")
                                        .build(),
                                InlineKeyboardButton.builder()
                                        .text("IN PROGRESS")
                                        .callbackData("TASK_STATUS_" + taskId + "_IN_PROGRESS")
                                        .build(),
                                InlineKeyboardButton.builder()
                                        .text("DONE")
                                        .callbackData("TASK_STATUS_" + taskId + "_DONE")
                                        .build()
                        ),
                        new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text("Delete")
                                        .callbackData("TASK_DELETE_" + taskId)
                                        .build(),
                                InlineKeyboardButton.builder()
                                        .text("Back")
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
                                        .text("My Meetings")
                                        .callbackData("MEETINGS_LIST")
                                        .build(),
                                InlineKeyboardButton.builder()
                                        .text("New Meeting")
                                        .callbackData("MEETINGS_CREATE")
                                        .build()
                        ),
                        new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text("Back to Menu")
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
                                        .text("My Notes")
                                        .callbackData("NOTES_LIST")
                                        .build(),
                                InlineKeyboardButton.builder()
                                        .text("New Note")
                                        .callbackData("NOTES_CREATE")
                                        .build()
                        ),
                        new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text("Search")
                                        .callbackData("NOTES_SEARCH")
                                        .build(),
                                InlineKeyboardButton.builder()
                                        .text("Categories")
                                        .callbackData("NOTES_CATEGORIES")
                                        .build()
                        ),
                        new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text("Back to Menu")
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
                                        .text("My Appointments")
                                        .callbackData("DOCTORS_LIST")
                                        .build(),
                                InlineKeyboardButton.builder()
                                        .text("New Appointment")
                                        .callbackData("DOCTORS_CREATE")
                                        .build()
                        ),
                        new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text("Back to Menu")
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
                                        .text("My Reminders")
                                        .callbackData("REMINDERS_LIST")
                                        .build()
                        ),
                        new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text("Back to Menu")
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
                                        .text("Language: " + language)
                                        .callbackData("SETTINGS_LANGUAGE")
                                        .build()
                        ),
                        new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text("Timezone: " + timezone)
                                        .callbackData("SETTINGS_TIMEZONE")
                                        .build()
                        ),
                        new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text("Notifications: " + (notifications ? "ON" : "OFF"))
                                        .callbackData("SETTINGS_NOTIFICATIONS")
                                        .build()
                        ),
                        new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text("Back to Menu")
                                        .callbackData("MENU_MAIN")
                                        .build()
                        )
                ))
                .build();
    }
}

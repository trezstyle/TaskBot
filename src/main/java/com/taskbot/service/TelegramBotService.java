package com.taskbot.service;

import com.taskbot.handler.CalendarHandler;
import com.taskbot.handler.EventCreationHandler;
import com.taskbot.security.RateLimitingService;
import com.taskbot.util.CalendarBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramBotService {

    private final TelegramClient telegramClient;
    private final UserService userService;
    private final RateLimitingService rateLimitingService;
    private final CalendarHandler calendarHandler;
    private final EventCreationHandler creationHandler;

    public void processUpdate(Update update) {
        try {
            Long telegramId;
            if (update.hasCallbackQuery()) {
                telegramId = update.getCallbackQuery().getFrom().getId();
                rateLimitingService.checkLimit(telegramId);
                ensureUserExistsFromCallback(update.getCallbackQuery());
                processCallbackQuery(update.getCallbackQuery());
            } else if (update.hasMessage() && update.getMessage().hasText()) {
                telegramId = update.getMessage().getFrom().getId();
                rateLimitingService.checkLimit(telegramId);
                ensureUserExistsFromMessage(update.getMessage());
                processMessage(update.getMessage());
            }
        } catch (com.taskbot.exception.RateLimitExceededException e) {
            Long tgId = update.hasMessage() ? update.getMessage().getFrom().getId() : update.getCallbackQuery().getFrom().getId();
            sendMessage(tgId, "\u26a0\ufe0f Слишком много запросов. Подождите немного.", null);
        } catch (Exception e) {
            log.error("Error processing update", e);
            Long tgId = update.hasMessage() ? update.getMessage().getFrom().getId() : update.getCallbackQuery().getFrom().getId();
            sendMessage(tgId, "\u274c Произошла ошибка. Попробуйте ещё раз.", null);
        }
    }

    private void processMessage(Message message) {
        Long telegramId = message.getFrom().getId();
        String text = message.getText().trim();

        if (creationHandler.isInCreationFlow(telegramId)) {
            if ("/cancel".equals(text)) {
                creationHandler.cancelCreation(telegramId);
                sendMessage(telegramId, "\u274c Действие отменено.", null);
                calendarHandler.showCalendar(telegramId, LocalDate.now());
                return;
            }
            creationHandler.handleTextInput(telegramId, message.getMessageId(), text);
            return;
        }

        if ("/cancel".equals(text)) {
            creationHandler.cancelCreation(telegramId);
            sendMessage(telegramId, "\u274c Действие отменено.", null);
            return;
        }

        if ("/start".equals(text)) {
            calendarHandler.showCalendar(telegramId, LocalDate.now());
        } else if ("/help".equals(text)) {
            sendMessage(telegramId, """
                    \ud83d\udcd6 Доступные команды:
                    
                    /start - Календарь
                    /cancel - Отменить действие
                    /help - Эта справка
                    
                    \ud83d\udca1 Нажмите на дату чтобы увидеть события.
                    \ud83d\udca1 "+" для создания нового события.
                    """, null);
        } else {
            sendMessage(telegramId, "\ud83e\udd14 Используйте /start для календаря.", null);
        }
    }

    private void processCallbackQuery(CallbackQuery callbackQuery) {
        Long telegramId = callbackQuery.getFrom().getId();
        String data = callbackQuery.getData();
        Integer messageId = callbackQuery.getMessage().getMessageId();

        if (creationHandler.isInCreationFlow(telegramId) && data.startsWith("CREATE_")) {
            creationHandler.handleCallback(telegramId, messageId, data);
            return;
        }

        if (data.equals("MENU_MAIN")) {
            creationHandler.cancelCreation(telegramId);
            calendarHandler.showCalendar(telegramId, LocalDate.now());
            return;
        }

        if (data.equals("CAL_ADD")) {
            creationHandler.startCreateEvent(telegramId, messageId);
            return;
        }

        if (data.startsWith("CAL_") || data.startsWith("EVT_")) {
            calendarHandler.handleCallback(telegramId, messageId, data);
            return;
        }
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

    private void ensureUserExistsFromMessage(Message message) {
        userService.findOrCreateUser(
                message.getFrom().getId(),
                message.getFrom().getUserName(),
                message.getFrom().getFirstName(),
                message.getFrom().getLastName()
        );
    }

    private void ensureUserExistsFromCallback(CallbackQuery callbackQuery) {
        userService.findOrCreateUser(
                callbackQuery.getFrom().getId(),
                callbackQuery.getFrom().getUserName(),
                callbackQuery.getFrom().getFirstName(),
                callbackQuery.getFrom().getLastName()
        );
    }

    private void sendMessage(Long chatId, String text, Object keyboard) {
        try {
            SendMessage msg = SendMessage.builder()
                    .chatId(chatId)
                    .text(text)
                    .replyMarkup(keyboard instanceof org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
                            ? (org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup) keyboard : null)
                    .build();
            telegramClient.execute(msg);
        } catch (TelegramApiException e) {
            log.error("Failed to send message to chat: {}", chatId, e);
        }
    }
}

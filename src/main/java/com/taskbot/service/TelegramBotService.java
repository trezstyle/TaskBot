package com.taskbot.service;

import com.taskbot.handler.CalendarHandler;
import com.taskbot.handler.EventCreationHandler;
import com.taskbot.handler.EventEditHandler;
import com.taskbot.security.RateLimitingService;
import com.taskbot.service.SpeechToTextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.Voice;
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
    private final EventEditHandler editHandler;
    private final SpeechToTextService speechToTextService;

    public void processUpdate(Update update) {
        try {
            Long telegramId;
            if (update.hasCallbackQuery()) {
                telegramId = update.getCallbackQuery().getFrom().getId();
                rateLimitingService.checkLimit(telegramId);
                ensureUserExistsFromCallback(update.getCallbackQuery());
                processCallbackQuery(update.getCallbackQuery());
            } else if (update.hasMessage()) {
                telegramId = update.getMessage().getFrom().getId();
                rateLimitingService.checkLimit(telegramId);
                ensureUserExistsFromMessage(update.getMessage());

                if (update.getMessage().hasVoice()) {
                    processVoiceMessage(update.getMessage());
                } else if (update.getMessage().hasText()) {
                    processMessage(update.getMessage());
                }
            }
        } catch (com.taskbot.exception.RateLimitExceededException e) {
            Long tgId = extractTelegramId(update);
            if (tgId != null) {
                sendMessage(tgId, "\u26a0\ufe0f Too many requests. Please wait.", null);
            }
        } catch (Exception e) {
            log.error("Error processing update", e);
            Long tgId = extractTelegramId(update);
            if (tgId != null) {
                sendMessage(tgId, "\u274c An error occurred. Please try again.", null);
            }
        }
    }

    private void processMessage(Message message) {
        Long telegramId = message.getFrom().getId();
        String text = message.getText() != null ? message.getText().trim() : "";
        log.info("Message from {}: '{}'", telegramId, text.length() > 50 ? text.substring(0, 50) : text);

        // Commands always work, even in creation/edit flow
        if ("/start".equals(text) || "/cancel".equals(text)) {
            if (creationHandler.isInCreationFlow(telegramId)) {
                creationHandler.cancelCreation(telegramId);
            }
            if (editHandler.isInEditFlow(telegramId)) {
                editHandler.cancelEdit(telegramId);
            }
            if ("/cancel".equals(text)) {
                sendMessage(telegramId, "❌ Cancelled.", null);
            }
            calendarHandler.showCalendar(telegramId, LocalDate.now());
            return;
        }

        if (editHandler.isInEditFlow(telegramId)) {
            editHandler.handleTextInput(telegramId, message.getMessageId(), text);
            return;
        }

        if (creationHandler.isInCreationFlow(telegramId)) {
            creationHandler.handleTextInput(telegramId, message.getMessageId(), text);
            return;
        }

        if ("/create".equals(text)) {
            creationHandler.startCreateEvent(telegramId, null);
        } else if ("/list".equals(text)) {
            calendarHandler.handleCallback(telegramId, null, "LIST_UPCOMING");
        } else if ("/help".equals(text)) {
            sendMessage(telegramId, """
                    📖 Available commands:
                    
                    /start - Calendar
                    /create - Create event
                    /list - Event list
                    /cancel - Cancel action
                    /help - This help
                    
                    💡 Tap a date to view events.
                    💡 Use "+" to create a new event.
                    """, null);
        } else {
            sendMessage(telegramId, "🤔 Use /start to open calendar.", null);
        }
    }

    private void processVoiceMessage(Message message) {
        Long telegramId = message.getFrom().getId();
        Voice voice = message.getVoice();

        if (creationHandler.isInCreationFlow(telegramId)) {
            // User is in event creation flow - transcribe voice as title
            sendMessage(telegramId, "🎙 Transcribing...", null);
            String text = speechToTextService.transcribeVoice(voice.getFileId(), telegramClient);

            if (text != null && !text.isBlank()) {
                sendMessage(telegramId, "📝 Recognized: " + text, null);
                creationHandler.handleTextInput(telegramId, message.getMessageId(), text);
            } else {
                sendMessage(telegramId, "🎙 Could not recognize. Please type the title:", null);
            }
        } else if (editHandler.isInEditFlow(telegramId)) {
            // User is in edit flow - transcribe voice as new title
            sendMessage(telegramId, "🎙 Transcribing...", null);
            String text = speechToTextService.transcribeVoice(voice.getFileId(), telegramClient);

            if (text != null && !text.isBlank()) {
                sendMessage(telegramId, "📝 Recognized: " + text, null);
                editHandler.handleTextInput(telegramId, message.getMessageId(), text);
            } else {
                sendMessage(telegramId, "🎙 Could not recognize. Please type the new title:", null);
            }
        } else {
            // Not in creation flow - start quick event from voice
            sendMessage(telegramId, "🎙 Transcribing...", null);
            String text = speechToTextService.transcribeVoice(voice.getFileId(), telegramClient);

            if (text != null && !text.isBlank()) {
                creationHandler.startQuickEventFromVoice(telegramId, text);
                sendMessage(telegramId, "📝 Recognized: " + text + "\n\nSelect date and time:", null);
                calendarHandler.showCalendar(telegramId, LocalDate.now());
            } else {
                sendMessage(telegramId, "🎙 Could not recognize. Try again or use /start", null);
            }
        }
    }

    private void processCallbackQuery(CallbackQuery callbackQuery) {
        Long telegramId = callbackQuery.getFrom().getId();
        String data = callbackQuery.getData();
        Integer messageId = callbackQuery.getMessage().getMessageId();

        if (data.equals("NONE")) {
            // Dummy button — just answer the callback to remove the loading spinner
            try {
                telegramClient.execute(new org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery(
                        callbackQuery.getId()));
            } catch (TelegramApiException e) {
                log.debug("Failed to answer NONE callback", e);
            }
            return;
        }

        if (creationHandler.isInCreationFlow(telegramId) && data.startsWith("CREATE_")) {
            creationHandler.handleCallback(telegramId, messageId, data);
            return;
        }

        if (editHandler.isInEditFlow(telegramId)) {
            if (editHandler.handleCallback(telegramId, messageId, data)) {
                return;
            }
            // If not handled by edit handler, fall through to normal routing
        }

        if (data.startsWith("EVT_EDIT_TITLE_") || data.startsWith("EVT_EDIT_DATE_")
                || data.startsWith("EVT_EDIT_TIME_") || data.startsWith("EVT_EDIT_COLOR_")
                || data.startsWith("EVT_EDIT_REMIND_") || data.startsWith("EVT_EDIT_CLR_")
                || data.startsWith("EVT_EDIT_REM_") || data.startsWith("EVT_EDIT_")) {
            editHandler.handleCallback(telegramId, messageId, data);
            return;
        }

        // Edit sub-flow callbacks (calendar navigation, time picker, etc.)
        if (data.startsWith("EDIT_")) {
            editHandler.handleCallback(telegramId, messageId, data);
            return;
        }

        if (data.equals("MENU_MAIN")) {
            creationHandler.cancelCreation(telegramId);
            editHandler.cancelEdit(telegramId);
            calendarHandler.showCalendar(telegramId, LocalDate.now());
            return;
        }

        if (data.equals("LIST_UPCOMING") || data.startsWith("LIST_PAGE_")
                || data.startsWith("LIST_DELETE_") || data.startsWith("LIST_DELETE_YES_")
                || data.equals("LIST_PAST") || data.startsWith("LIST_PAST_PAGE_")
                || data.startsWith("LIST_PAST_DELETE_") || data.startsWith("LIST_PAST_DELETE_YES_")
                || data.equals("LIST_PAST_CLEAR") || data.equals("LIST_PAST_CLEAR_YES")) {
            calendarHandler.handleCallback(telegramId, messageId, data);
            return;
        }

        if (data.equals("CAL_ADD")) {
            editHandler.cancelEdit(telegramId);
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

    private Long extractTelegramId(Update update) {
        if (update.hasMessage()) return update.getMessage().getFrom().getId();
        if (update.hasCallbackQuery()) return update.getCallbackQuery().getFrom().getId();
        return null;
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

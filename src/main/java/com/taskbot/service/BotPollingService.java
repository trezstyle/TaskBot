package com.taskbot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "bot.enabled", havingValue = "true", matchIfMissing = true)
public class BotPollingService implements SpringLongPollingBot {

    @Value("${bot.token}")
    private String botToken;

    private final TelegramBotService telegramBotService;

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return updates -> updates.forEach(telegramBotService::processUpdate);
    }
}

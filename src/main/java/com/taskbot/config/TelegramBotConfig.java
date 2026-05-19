package com.taskbot.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Slf4j
@Configuration
public class TelegramBotConfig {

    @Value("${bot.token}")
    private String botToken;

    @Value("${bot.username}")
    private String botUsername;

    @Bean
    public TelegramClient telegramClient() {
        return new OkHttpTelegramClient(botToken);
    }

    @PostConstruct
    public void init() {
        log.info("Telegram Bot configured: username={}", botUsername);
        String masked = botToken.length() > 8 ? botToken.substring(0, 8) + "..." : "***";
        log.info("Bot token loaded: {}", masked);
    }
}

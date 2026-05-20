package com.taskbot.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BotCommandRegistrar {

    private final TelegramClient telegramClient;

    @EventListener(ApplicationReadyEvent.class)
    public void registerCommands() {
        try {
            SetMyCommands setCommands = SetMyCommands.builder()
                    .commands(List.of(
                            new BotCommand("start", "📅 Calendar"),
                            new BotCommand("create", "➕ Create event"),
                            new BotCommand("list", "📋 Event list"),
                            new BotCommand("cancel", "❌ Cancel action"),
                            new BotCommand("help", "📖 Help")
                    ))
                    .build();
            telegramClient.execute(setCommands);
            log.info("Bot commands registered in Telegram menu");
        } catch (Exception e) {
            log.error("Failed to register bot commands", e);
        }
    }
}
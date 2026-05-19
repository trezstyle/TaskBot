package com.taskbot.controller;

import com.taskbot.service.TelegramBotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.telegram.telegrambots.meta.api.objects.Update;

@Slf4j
@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
@Tag(name = "Telegram Webhook", description = "Endpoint for Telegram bot updates")
public class TelegramWebhookController {

    private final TelegramBotService telegramBotService;

    @PostMapping
    @Operation(summary = "Receive Telegram update")
    public ResponseEntity<Void> receiveUpdate(@RequestBody Update update) {
        log.debug("Received update: {}", update.getUpdateId());
        telegramBotService.processUpdate(update);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    @Operation(summary = "Health check for webhook")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Webhook endpoint is active");
    }
}

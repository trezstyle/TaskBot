package com.taskbot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SpeechToTextService {

    @Value("${bot.token}")
    private String botToken;

    @Value("${stt.enabled:true}")
    private boolean enabled;

    @Value("${stt.max-duration-seconds:120}")
    private int maxDurationSeconds;

    private static final String VOSK_SCRIPT = "/app/vosk_transcribe.py";
    private static final String PYTHON_BIN = "/opt/vosk-venv/bin/python3";
    private static final String TELEGRAM_FILE_URL = "https://api.telegram.org/file/bot";

    /**
     * Transcribe a Telegram voice message to text.
     *
     * @param fileId         Telegram file_id of the voice message
     * @param telegramClient Telegram client for API calls
     * @return transcribed text, or null if transcription failed
     */
    public String transcribeVoice(String fileId, TelegramClient telegramClient) {
        if (!enabled) {
            log.warn("STT is disabled");
            return null;
        }

        Path oggFile = null;
        Path wavFile = null;

        try {
            // 1. Get file path from Telegram
            GetFile getFile = GetFile.builder().fileId(fileId).build();
            File telegramFile = telegramClient.execute(getFile);
            String filePath = telegramFile.getFilePath();

            // 2. Download .ogg file
            String downloadUrl = TELEGRAM_FILE_URL + botToken + "/" + filePath;
            oggFile = Files.createTempFile("voice_", ".ogg");

            try (java.io.InputStream in = URI.create(downloadUrl).toURL().openStream()) {
                Files.copy(in, oggFile, StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("Downloaded voice file: {} ({} bytes)", oggFile, Files.size(oggFile));

            // 3. Convert to .wav (16kHz mono) with ffmpeg
            wavFile = Files.createTempFile("voice_", ".wav");
            ProcessBuilder ffmpegPb = new ProcessBuilder(
                    "ffmpeg", "-y", "-i", oggFile.toString(),
                    "-ar", "16000", "-ac", "1", "-f", "wav",
                    wavFile.toString()
            );
            ffmpegPb.redirectErrorStream(true);
            Process ffmpegProc = ffmpegPb.start();
            String ffmpegOutput = new String(ffmpegProc.getInputStream().readAllBytes());
            boolean ffmpegOk = ffmpegProc.waitFor(30, TimeUnit.SECONDS);

            if (!ffmpegOk || ffmpegProc.exitValue() != 0) {
                log.error("ffmpeg failed: exit={}, output={}", ffmpegProc.exitValue(), ffmpegOutput);
                return null;
            }

            log.info("Converted to WAV: {} ({} bytes)", wavFile, Files.size(wavFile));

            // 4. Run Vosk transcription
            ProcessBuilder voskPb = new ProcessBuilder(PYTHON_BIN, VOSK_SCRIPT, wavFile.toString());
            Process voskProc = voskPb.start();
            // Read stdout (transcription) first, then stderr separately to avoid mixing
            String result = new String(voskProc.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
            boolean voskOk = voskProc.waitFor(60, TimeUnit.SECONDS);
            String errors = new String(voskProc.getErrorStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();

            if (!voskOk || voskProc.exitValue() != 0) {
                log.error("Vosk failed: exit={}, result={}, errors={}", voskProc.exitValue(), result, errors);
                return null;
            }

            if (result.startsWith("ERROR")) {
                log.error("Vosk error: {}", result);
                return null;
            }

            log.info("Transcribed: '{}' ({} chars)", result, result.length());
            return result.isBlank() ? null : result;

        } catch (Exception e) {
            log.error("Voice transcription failed", e);
            return null;
        } finally {
            // Clean up temp files
            try {
                if (oggFile != null) Files.deleteIfExists(oggFile);
                if (wavFile != null) Files.deleteIfExists(wavFile);
            } catch (IOException e) {
                log.warn("Failed to delete temp files", e);
            }
        }
    }
}
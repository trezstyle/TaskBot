package com.taskbot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.util.HexFormat;

@Configuration
public class EncryptionConfig {

    @Value("${encryption.secret-key}")
    private String secretKeyHex;

    @Bean
    public Key encryptionKey() {
        byte[] keyBytes = HexFormat.of().parseHex(secretKeyHex);
        return new SecretKeySpec(keyBytes, "AES");
    }
}

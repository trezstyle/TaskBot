package com.taskbot.security;

import com.taskbot.exception.RateLimitExceededException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RateLimitingService {

    private final int capacity;
    private final int refillPerMinute;
    private final Cache<Long, Bucket> buckets;

    public RateLimitingService(
            @Value("${rate-limiting.capacity:10}") int capacity,
            @Value("${rate-limiting.refill-per-minute:5}") int refillPerMinute) {
        this.capacity = capacity;
        this.refillPerMinute = refillPerMinute;
        this.buckets = Caffeine.newBuilder()
                .expireAfterAccess(1, TimeUnit.HOURS)
                .maximumSize(10_000)
                .build();
    }

    @PostConstruct
    public void init() {
        log.info("RateLimitingService initialized: capacity={}, refillPerMinute={}", capacity, refillPerMinute);
    }

    public void checkLimit(Long telegramId) {
        Bucket bucket = buckets.get(telegramId, k -> new Bucket(capacity));
        if (!bucket.tryConsume()) {
            log.warn("Rate limit exceeded for user: {}", telegramId);
            throw new RateLimitExceededException("Too many requests. Please wait before sending another command.");
        }
    }

    private class Bucket {
        private final int capacity;
        private int tokens;
        private volatile long lastRefill;

        Bucket(int capacity) {
            this.capacity = capacity;
            this.tokens = capacity;
            this.lastRefill = System.currentTimeMillis();
        }

        synchronized boolean tryConsume() {
            refill();
            if (tokens > 0) {
                tokens--;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefill;
            if (elapsed < 60_000) {
                return;
            }
            int tokensToAdd = (int) (elapsed / 60_000) * refillPerMinute;
            if (tokensToAdd > 0) {
                tokens = Math.min(capacity, tokens + tokensToAdd);
                lastRefill = now;
            }
        }
    }
}
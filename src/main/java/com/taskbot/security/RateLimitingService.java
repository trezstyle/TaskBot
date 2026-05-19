package com.taskbot.security;

import com.taskbot.exception.RateLimitExceededException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class RateLimitingService {

    private final int capacity;
    private final int refillPerMinute;
    private final Map<Long, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitingService(
            @Value("${rate-limiting.capacity:10}") int capacity,
            @Value("${rate-limiting.refill-per-minute:5}") int refillPerMinute) {
        this.capacity = capacity;
        this.refillPerMinute = refillPerMinute;
    }

    @PostConstruct
    public void init() {
        log.info("RateLimitingService initialized: capacity={}, refillPerMinute={}", capacity, refillPerMinute);
    }

    public void checkLimit(Long telegramId) {
        Bucket bucket = buckets.computeIfAbsent(telegramId, k -> new Bucket(capacity));
        if (!bucket.tryConsume()) {
            log.warn("Rate limit exceeded for user: {}", telegramId);
            throw new RateLimitExceededException("Too many requests. Please wait before sending another command.");
        }
    }

    private class Bucket {
        private final int capacity;
        private final AtomicInteger tokens;
        private volatile long lastRefill;

        Bucket(int capacity) {
            this.capacity = capacity;
            this.tokens = new AtomicInteger(capacity);
            this.lastRefill = System.currentTimeMillis();
        }

        synchronized boolean tryConsume() {
            refill();
            if (tokens.get() > 0) {
                tokens.decrementAndGet();
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
                int newTokens = Math.min(capacity, tokens.get() + tokensToAdd);
                tokens.set(newTokens);
                lastRefill = now;
            }
        }
    }
}

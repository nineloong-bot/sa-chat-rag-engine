package com.sa.assistant.common.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlidingWindowRateLimiter {

    private final StringRedisTemplate stringRedisTemplate;

    private static final String SLIDING_WINDOW_SCRIPT = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window_ms = tonumber(ARGV[2])
            local max_requests = tonumber(ARGV[3])
            
            redis.call('zremrangebyscore', key, 0, now - window_ms)
            
            local current = redis.call('zcard', key)
            
            if current >= max_requests then
                return current
            end
            
            redis.call('zadd', key, now, now .. ':' .. math.random(1000000))
            redis.call('pexpire', key, window_ms)
            
            return current
            """;

    private final DefaultRedisScript<Long> slidingWindowScript = new DefaultRedisScript<>(SLIDING_WINDOW_SCRIPT, Long.class);

    public boolean isAllowed(String key, long windowMs, int maxRequests) {
        long now = System.currentTimeMillis();
        Long current = stringRedisTemplate.execute(
                slidingWindowScript,
                Collections.singletonList(key),
                String.valueOf(now),
                String.valueOf(windowMs),
                String.valueOf(maxRequests)
        );

        boolean allowed = current != null && current < maxRequests;

        if (!allowed) {
            log.warn("Rate limit exceeded | key={}, current={}, max={}, window={}ms", key, current, maxRequests, windowMs);
        }

        return allowed;
    }

    public void checkOrThrow(String key, long windowMs, int maxRequests) {
        if (!isAllowed(key, windowMs, maxRequests)) {
            throw new RateLimitExceededException(
                    String.format("请求过于频繁，每分钟最多允许%d次请求，请稍后再试", maxRequests)
            );
        }
    }
}

package com.sa.assistant.common.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SlidingWindowRateLimiter 单元测试")
class SlidingWindowRateLimiterTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    private SlidingWindowRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new SlidingWindowRateLimiter(stringRedisTemplate);
    }

    @Test
    @DisplayName("当前请求数小于阈值时应允许通过")
    void shouldAllowWhenUnderLimit() {
        when(stringRedisTemplate.execute(
                any(DefaultRedisScript.class),
                eq(Collections.singletonList("ratelimit:test")),
                anyString(), anyString(), anyString()
        )).thenReturn(3L); // 3 < max(10)

        boolean allowed = rateLimiter.isAllowed("ratelimit:test", 60_000, 10);

        assertThat(allowed).isTrue();
    }

    @Test
    @DisplayName("当前请求数等于阈值时应拒绝")
    void shouldRejectWhenAtLimit() {
        when(stringRedisTemplate.execute(
                any(DefaultRedisScript.class),
                eq(Collections.singletonList("ratelimit:test")),
                anyString(), anyString(), anyString()
        )).thenReturn(10L); // 10 == max(10)

        boolean allowed = rateLimiter.isAllowed("ratelimit:test", 60_000, 10);

        assertThat(allowed).isFalse();
    }

    @Test
    @DisplayName("当前请求数超过阈值时应拒绝")
    void shouldRejectWhenOverLimit() {
        when(stringRedisTemplate.execute(
                any(DefaultRedisScript.class),
                eq(Collections.singletonList("ratelimit:test")),
                anyString(), anyString(), anyString()
        )).thenReturn(15L); // 15 > max(10)

        boolean allowed = rateLimiter.isAllowed("ratelimit:test", 60_000, 10);

        assertThat(allowed).isFalse();
    }

    @Test
    @DisplayName("checkOrThrow 在超限时应抛 RateLimitExceededException")
    void shouldThrowWhenRateLimited() {
        when(stringRedisTemplate.execute(
                any(DefaultRedisScript.class),
                eq(Collections.singletonList("ratelimit:test")),
                anyString(), anyString(), anyString()
        )).thenReturn(20L);

        assertThatThrownBy(() -> rateLimiter.checkOrThrow("ratelimit:test", 60_000, 10))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("请求过于频繁");
    }

    @Test
    @DisplayName("checkOrThrow 在未超限时不应抛异常")
    void shouldNotThrowWhenNotRateLimited() {
        when(stringRedisTemplate.execute(
                any(DefaultRedisScript.class),
                eq(Collections.singletonList("ratelimit:test")),
                anyString(), anyString(), anyString()
        )).thenReturn(0L);

        // 不应抛异常
        rateLimiter.checkOrThrow("ratelimit:test", 60_000, 10);
    }
}

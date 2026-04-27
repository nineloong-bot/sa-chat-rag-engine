package com.sa.assistant.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MultiLevelCache 单元测试")
class MultiLevelCacheTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private MultiLevelCache cache;
    private com.github.benmanes.caffeine.cache.Cache<Object, Object> caffeine;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        caffeine = Caffeine.newBuilder()
                .maximumSize(100)
                .build();

        cache = new MultiLevelCache("testCache", caffeine, redisTemplate, "sa:cache:", 60000);
    }

    @Test
    @DisplayName("L1 Caffeine 命中时应直接返回，不查 Redis")
    void shouldHitL1CaffeineWithoutQueryingRedis() {
        caffeine.put("key1", "caffeine-value");

        Object result = cache.get("key1", () -> "db-value");

        assertThat(result).isEqualTo("caffeine-value");
        verify(valueOperations, never()).get(anyString());
    }

    @Test
    @DisplayName("L1 未命中但 L2 Redis 命中时，应回填 L1 并返回")
    void shouldHitL2RedisAndBackfillL1() {
        when(valueOperations.get(eq("sa:cache:testCache:key2")))
                .thenReturn("redis-value");

        Object result = cache.get("key2", () -> "db-value");

        assertThat(result).isEqualTo("redis-value");
        assertThat(caffeine.getIfPresent("key2")).isEqualTo("redis-value");
    }

    @Test
    @DisplayName("L1 和 L2 都未命中时，触发 Callable 回源查 DB")
    void shouldLoadFromDbWhenL1AndL2Miss() {
        when(valueOperations.get(eq("sa:cache:testCache:key3")))
                .thenReturn(null);

        Object result = cache.get("key3", () -> "db-loaded-value");

        assertThat(result).isEqualTo("db-loaded-value");
        assertThat(caffeine.getIfPresent("key3")).isEqualTo("db-loaded-value");
        verify(valueOperations).set(
                eq("sa:cache:testCache:key3"),
                eq("db-loaded-value"),
                anyLong(),
                any(TimeUnit.class)
        );
    }

    @Test
    @DisplayName("evict 应先删 L2(Redis)，再删 L1(Caffeine)")
    void shouldEvictBothL2AndL1() {
        caffeine.put("key4", "some-value");

        cache.evict("key4");

        assertThat(caffeine.getIfPresent("key4")).isNull();
        verify(redisTemplate).delete(eq("sa:cache:testCache:key4"));
    }

    @Test
    @DisplayName("put 应同时写入 L1 和 L2")
    void shouldPutToBothL1AndL2() {
        cache.put("key5", "new-value");

        assertThat(caffeine.getIfPresent("key5")).isEqualTo("new-value");
        verify(valueOperations).set(
                eq("sa:cache:testCache:key5"),
                eq("new-value"),
                anyLong(),
                any(TimeUnit.class)
        );
    }
}

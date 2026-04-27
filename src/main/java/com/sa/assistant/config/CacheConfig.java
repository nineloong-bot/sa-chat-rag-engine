package com.sa.assistant.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.sa.assistant.cache.MultiLevelCacheManager;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@EnableCaching
@Configuration
@ConfigurationProperties(prefix = "app.cache")
public class CacheConfig {

    private CaffeineProps caffeine = new CaffeineProps();
    private RedisProps redis = new RedisProps();
    private ChatHistoryProps chatHistory = new ChatHistoryProps();

    public CaffeineProps getCaffeine() { return caffeine; }
    public void setCaffeine(CaffeineProps caffeine) { this.caffeine = caffeine; }
    public RedisProps getRedis() { return redis; }
    public void setRedis(RedisProps redis) { this.redis = redis; }
    public ChatHistoryProps getChatHistory() { return chatHistory; }
    public void setChatHistory(ChatHistoryProps chatHistory) { this.chatHistory = chatHistory; }

    public static class CaffeineProps {
        private int maxSize = 500;
        private String expireAfterWrite = "5m";
        public int getMaxSize() { return maxSize; }
        public void setMaxSize(int maxSize) { this.maxSize = maxSize; }
        public String getExpireAfterWrite() { return expireAfterWrite; }
        public void setExpireAfterWrite(String expireAfterWrite) { this.expireAfterWrite = expireAfterWrite; }
    }

    public static class RedisProps {
        private String ttl = "30m";
        public String getTtl() { return ttl; }
        public void setTtl(String ttl) { this.ttl = ttl; }
    }

    public static class ChatHistoryProps {
        private String caffeineTtl = "3m";
        private String redisTtl = "20m";
        public String getCaffeineTtl() { return caffeineTtl; }
        public void setCaffeineTtl(String caffeineTtl) { this.caffeineTtl = caffeineTtl; }
        public String getRedisTtl() { return redisTtl; }
        public void setRedisTtl(String redisTtl) { this.redisTtl = redisTtl; }
    }

    @Bean
    public Caffeine<Object, Object> caffeineConfig() {
        return Caffeine.newBuilder()
                .initialCapacity(50)
                .maximumSize(caffeine.getMaxSize())
                .expireAfterWrite(parseDuration(caffeine.getExpireAfterWrite()), TimeUnit.MILLISECONDS)
                .recordStats();
    }

    @Bean
    public MultiLevelCacheManager multiLevelCacheManager(
            Caffeine<Object, Object> caffeineSpec,
            RedisTemplate<String, Object> redisTemplate) {
        long defaultRedisTtlMillis = parseDuration(redis.getTtl());

        // Per-cache Redis TTL overrides
        Map<String, Long> redisTtlOverrides = new HashMap<>();
        redisTtlOverrides.put("chatHistory", parseDuration(chatHistory.getRedisTtl()));

        // Per-cache Caffeine spec overrides
        Map<String, Caffeine<Object, Object>> caffeineOverrides = new HashMap<>();
        caffeineOverrides.put("chatHistory", Caffeine.newBuilder()
                .initialCapacity(20)
                .maximumSize(caffeine.getMaxSize())
                .expireAfterWrite(parseDuration(chatHistory.getCaffeineTtl()), TimeUnit.MILLISECONDS)
                .recordStats());

        return new MultiLevelCacheManager(
                caffeineSpec,
                redisTemplate,
                "sa:cache:",
                defaultRedisTtlMillis,
                redisTtlOverrides,
                caffeineOverrides
        );
    }

    private long parseDuration(String duration) {
        char unit = duration.charAt(duration.length() - 1);
        long value = Long.parseLong(duration.substring(0, duration.length() - 1));
        return switch (unit) {
            case 's' -> value * 1000;
            case 'm' -> value * 60 * 1000;
            case 'h' -> value * 3600 * 1000;
            default -> value;
        };
    }
}

package com.sa.assistant.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
public class MultiLevelCacheManager implements CacheManager {

    private final Map<String, MultiLevelCache> cacheMap = new ConcurrentHashMap<>();
    private final Caffeine<Object, Object> defaultCaffeineSpec;
    private final RedisTemplate<String, Object> redisTemplate;
    private final String redisKeyPrefix;
    private final long defaultRedisTtlMillis;

    private final Map<String, Long> cacheSpecificRedisTtls;
    private final Map<String, Caffeine<Object, Object>> cacheSpecificCaffeineSpecs;

    public MultiLevelCacheManager(Caffeine<Object, Object> defaultCaffeineSpec,
                                  RedisTemplate<String, Object> redisTemplate,
                                  String redisKeyPrefix,
                                  long defaultRedisTtlMillis,
                                  Map<String, Long> cacheSpecificRedisTtls,
                                  Map<String, Caffeine<Object, Object>> cacheSpecificCaffeineSpecs) {
        this.defaultCaffeineSpec = defaultCaffeineSpec;
        this.redisTemplate = redisTemplate;
        this.redisKeyPrefix = redisKeyPrefix;
        this.defaultRedisTtlMillis = defaultRedisTtlMillis;
        this.cacheSpecificRedisTtls = cacheSpecificRedisTtls != null ? cacheSpecificRedisTtls : Map.of();
        this.cacheSpecificCaffeineSpecs = cacheSpecificCaffeineSpecs != null ? cacheSpecificCaffeineSpecs : Map.of();
    }

    @Override
    public org.springframework.cache.Cache getCache(String name) {
        return cacheMap.computeIfAbsent(name, this::createCache);
    }

    @Override
    public Collection<String> getCacheNames() {
        return Collections.unmodifiableSet(cacheMap.keySet());
    }

    private MultiLevelCache createCache(String name) {
        Caffeine<Object, Object> caffeineSpec = cacheSpecificCaffeineSpecs.getOrDefault(name, defaultCaffeineSpec);
        Cache<Object, Object> caffeine = caffeineSpec.build();
        long redisTtl = cacheSpecificRedisTtls.getOrDefault(name, defaultRedisTtlMillis);

        log.info("MultiLevelCache created | name={}, redisTTL={}ms", name, redisTtl);
        return new MultiLevelCache(name, caffeine, redisTemplate, redisKeyPrefix, redisTtl);
    }
}

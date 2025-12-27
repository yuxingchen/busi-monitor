package com.monitor.backend.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Redis缓存策略
 * <p>
 * 适用于中小数据量、高频访问的场景。
 * 支持分布式部署、自动TTL过期。
 * </p>
 */
@Component
public class RedisCacheStrategy implements CacheStrategy {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheStrategy.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired(required = false)
    private RedisTemplate<String, String> redisTemplate;

    @Value("${monitor.cache.redis.key-prefix:busi-monitor:cache:}")
    private String keyPrefix;

    @Value("${monitor.cache.redis.ttl-seconds:3600}")
    private long ttlSeconds;

    @Override
    public Type getType() {
        return Type.REDIS;
    }

    @Override
    public void write(String cacheKey, List<Map<String, Object>> data) {
        if (redisTemplate == null) {
            log.warn("Redis未配置，无法使用Redis缓存策略");
            return;
        }
        try {
            String key = keyPrefix + cacheKey;
            String json = objectMapper.writeValueAsString(data);
            redisTemplate.opsForValue().set(key, json, ttlSeconds, TimeUnit.SECONDS);
            log.info("Redis缓存写入成功: key={}, count={}", cacheKey, data.size());
        } catch (Exception e) {
            log.error("Redis缓存写入失败: key={}", cacheKey, e);
            throw new RuntimeException("Redis缓存写入失败", e);
        }
    }

    @Override
    public List<Map<String, Object>> read(String cacheKey) {
        if (redisTemplate == null) {
            return Collections.emptyList();
        }
        try {
            String key = keyPrefix + cacheKey;
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isEmpty()) {
                return Collections.emptyList();
            }
            return objectMapper.readValue(json,
                    new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.error("Redis缓存读取失败: key={}", cacheKey, e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<Map<String, Object>> readBatch(String cacheKey, int offset, int limit) {
        List<Map<String, Object>> all = read(cacheKey);
        if (all.isEmpty() || offset >= all.size()) {
            return Collections.emptyList();
        }
        int end = Math.min(offset + limit, all.size());
        return all.subList(offset, end);
    }

    @Override
    public long count(String cacheKey) {
        return read(cacheKey).size();
    }

    @Override
    public void clear(String cacheKey) {
        if (redisTemplate == null) {
            return;
        }
        try {
            String key = keyPrefix + cacheKey;
            redisTemplate.delete(key);
            log.info("Redis缓存清理成功: key={}", cacheKey);
        } catch (Exception e) {
            log.error("Redis缓存清理失败: key={}", cacheKey, e);
        }
    }

    @Override
    public boolean exists(String cacheKey) {
        if (redisTemplate == null) {
            return false;
        }
        String key = keyPrefix + cacheKey;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}

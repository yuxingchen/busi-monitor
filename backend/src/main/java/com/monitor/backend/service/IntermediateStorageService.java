package com.monitor.backend.service;

import com.monitor.backend.cache.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 中间结果缓存服务
 * <p>
 * 策略模式实现，支持运行时切换缓存后端。
 * 根据配置或数据量自动选择最优缓存策略。
 * </p>
 */
@Service
public class IntermediateStorageService {

    private static final Logger log = LoggerFactory.getLogger(IntermediateStorageService.class);

    @Value("${monitor.cache.strategy:FILE}")
    private String strategyType;

    @Autowired
    private FileCacheStrategy fileCacheStrategy;

    @Autowired
    private RedisCacheStrategy redisCacheStrategy;

    @Autowired(required = false)
    private ElasticsearchCacheStrategy elasticsearchCacheStrategy;

    @Autowired
    private TempTableCacheStrategy tempTableCacheStrategy;

    private CacheStrategy currentStrategy;

    @PostConstruct
    public void init() {
        // 根据配置初始化默认策略
        this.currentStrategy = getStrategy(CacheStrategy.Type.valueOf(strategyType));
        log.info("中间缓存服务初始化完成，当前策略: {}", strategyType);
    }

    /**
     * 获取指定类型的缓存策略
     */
    public CacheStrategy getStrategy(CacheStrategy.Type type) {
        switch (type) {
            case REDIS:
                return redisCacheStrategy;
            case ES:
                if (elasticsearchCacheStrategy != null) {
                    return elasticsearchCacheStrategy;
                }
                log.warn("ES缓存策略未配置，降级使用FILE策略");
                return fileCacheStrategy;
            case TEMP_TABLE:
                return tempTableCacheStrategy;
            case FILE:
            default:
                return fileCacheStrategy;
        }
    }

    /**
     * 根据数据量自动选择最优缓存策略
     * 
     * @param estimatedCount 预估数据条数
     * @return 推荐的缓存策略
     */
    public CacheStrategy autoSelectStrategy(long estimatedCount) {
        CacheStrategy.Type recommended;
        if (estimatedCount < 100_000) {
            // 小于10万条，使用Redis（最快）
            recommended = CacheStrategy.Type.REDIS;
        } else if (estimatedCount < 10_000_000) {
            // 10万-1000万条，使用本地文件（平衡）
            recommended = CacheStrategy.Type.FILE;
        } else {
            // 大于1000万条，使用临时表（大容量）
            recommended = CacheStrategy.Type.TEMP_TABLE;
        }
        log.info("根据数据量 {} 自动选择缓存策略: {}", estimatedCount, recommended);
        return getStrategy(recommended);
    }

    /**
     * 使用当前策略写入缓存
     */
    public void write(String cacheKey, List<Map<String, Object>> data) {
        currentStrategy.write(cacheKey, data);
    }

    /**
     * 使用指定策略写入缓存
     */
    public void write(CacheStrategy strategy, String cacheKey, List<Map<String, Object>> data) {
        strategy.write(cacheKey, data);
    }

    /**
     * 使用当前策略读取缓存
     */
    public List<Map<String, Object>> read(String cacheKey) {
        return currentStrategy.read(cacheKey);
    }

    /**
     * 使用指定策略读取缓存
     */
    public List<Map<String, Object>> read(CacheStrategy strategy, String cacheKey) {
        return strategy.read(cacheKey);
    }

    /**
     * 分批读取缓存数据
     */
    public List<Map<String, Object>> readBatch(String cacheKey, int offset, int limit) {
        return currentStrategy.readBatch(cacheKey, offset, limit);
    }

    /**
     * 获取缓存数据条数
     */
    public long count(String cacheKey) {
        return currentStrategy.count(cacheKey);
    }

    /**
     * 清理缓存
     */
    public void clear(String cacheKey) {
        currentStrategy.clear(cacheKey);
    }

    /**
     * 检查缓存是否存在
     */
    public boolean exists(String cacheKey) {
        return currentStrategy.exists(cacheKey);
    }

    /**
     * 切换当前缓存策略
     */
    public void switchStrategy(CacheStrategy.Type type) {
        this.currentStrategy = getStrategy(type);
        log.info("缓存策略切换为: {}", type);
    }

    /**
     * 获取当前策略类型
     */
    public CacheStrategy.Type getCurrentStrategyType() {
        return currentStrategy.getType();
    }
}

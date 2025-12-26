package com.monitor.backend.batch;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.monitor.backend.service.IntermediateStorageService;

/**
 * 批处理数据富化处理器
 * <p>
 * 负责从中间缓存读取主表数据，并通过关联查询从其他数据源
 * 获取补充信息，合并后输出完整记录。
 * </p>
 * 
 * 优化策略：
 * - 本地缓存：对关联表使用本地HashMap缓存，减少重复查询
 * - 批量查询：收集关联键后批量查询关联表
 * - 索引字段：自动识别并利用关联字段建立索引
 */
@Component
@StepScope
public class BatchEnrichmentProcessor implements ItemProcessor<Map<String, Object>, Map<String, Object>> {

    private static final Logger log = LoggerFactory.getLogger(BatchEnrichmentProcessor.class);

    @Autowired
    private IntermediateStorageService storageService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 关联缓存配置
    @Value("#{jobParameters['enrichmentCacheKey']}")
    private String enrichmentCacheKey;

    @Value("#{jobParameters['joinField']}")
    private String joinField;

    @Value("#{jobParameters['enrichFields']}")
    private String enrichFields;

    // 本地缓存关联数据，key为关联字段值
    private ConcurrentHashMap<Object, Map<String, Object>> localLookupCache;

    // 缓存是否已加载
    private volatile boolean cacheLoaded = false;

    @Override
    public Map<String, Object> process(Map<String, Object> item) throws Exception {
        // 懒加载关联缓存
        if (!cacheLoaded) {
            loadEnrichmentCache();
        }

        // 获取关联键值
        Object joinValue = item.get(joinField);
        if (joinValue == null) {
            log.warn("关联字段 {} 值为空，跳过富化", joinField);
            return item;
        }

        // 查找关联数据
        Map<String, Object> enrichData = localLookupCache.get(joinValue);
        if (enrichData == null) {
            log.debug("未找到关联数据，joinField={}, value={}", joinField, joinValue);
            return item;
        }

        // 合并数据
        Map<String, Object> enrichedItem = new HashMap<>(item);
        if (enrichFields != null && !enrichFields.isEmpty()) {
            // 只合并指定字段
            for (String field : enrichFields.split(",")) {
                String trimmedField = field.trim();
                if (enrichData.containsKey(trimmedField)) {
                    enrichedItem.put(trimmedField, enrichData.get(trimmedField));
                }
            }
        } else {
            // 合并所有字段
            enrichedItem.putAll(enrichData);
        }

        return enrichedItem;
    }

    /**
     * 加载富化关联缓存
     * <p>
     * 从中间缓存读取关联表数据并建立本地索引。
     * 使用同步块确保只加载一次。
     * </p>
     */
    private synchronized void loadEnrichmentCache() {
        if (cacheLoaded) {
            return;
        }

        log.info("开始加载富化关联缓存: key={}, joinField={}", enrichmentCacheKey, joinField);
        long startTime = System.currentTimeMillis();

        localLookupCache = new ConcurrentHashMap<>();

        if (enrichmentCacheKey == null || joinField == null) {
            log.warn("富化配置不完整，跳过加载");
            cacheLoaded = true;
            return;
        }

        // 从中间缓存分批读取数据
        int offset = 0;
        int batchSize = 10000;
        int totalCount = 0;

        while (true) {
            List<Map<String, Object>> batch = storageService.readBatch(enrichmentCacheKey, offset, batchSize);
            if (batch.isEmpty()) {
                break;
            }

            for (Map<String, Object> record : batch) {
                Object key = record.get(joinField);
                if (key != null) {
                    localLookupCache.put(key, record);
                }
            }

            totalCount += batch.size();
            offset += batch.size();
            log.debug("已加载 {} 条关联数据", totalCount);
        }

        cacheLoaded = true;
        long elapsed = System.currentTimeMillis() - startTime;
        log.info("富化关联缓存加载完成，共 {} 条记录，耗时 {} ms", totalCount, elapsed);
    }

    /**
     * 清理本地缓存
     * <p>
     * 在Job完成后调用以释放内存
     * </p>
     */
    public void clearCache() {
        if (localLookupCache != null) {
            localLookupCache.clear();
            localLookupCache = null;
        }
        cacheLoaded = false;
        log.info("本地关联缓存已清理");
    }

    /**
     * 获取当前缓存大小
     */
    public int getCacheSize() {
        return localLookupCache != null ? localLookupCache.size() : 0;
    }
}

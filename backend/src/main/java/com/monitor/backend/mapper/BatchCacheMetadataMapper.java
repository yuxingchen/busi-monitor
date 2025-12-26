package com.monitor.backend.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.monitor.backend.entity.BatchCacheMetadata;

/**
 * 批处理缓存元数据 Mapper
 */
@Mapper
public interface BatchCacheMetadataMapper {

    /**
     * 根据缓存键查询
     */
    BatchCacheMetadata findByCacheKey(@Param("cacheKey") String cacheKey);

    /**
     * 根据执行ID查询
     */
    List<BatchCacheMetadata> findByExecutionId(@Param("executionId") Long executionId);

    /**
     * 插入记录
     */
    int insert(BatchCacheMetadata metadata);

    /**
     * 更新记录
     */
    int update(BatchCacheMetadata metadata);

    /**
     * 根据缓存键更新或插入（Upsert）
     */
    int upsertByCacheKey(BatchCacheMetadata metadata);

    /**
     * 标记为过期
     */
    int markExpired(@Param("cacheKey") String cacheKey);

    /**
     * 删除过期记录
     */
    int deleteExpired();
}

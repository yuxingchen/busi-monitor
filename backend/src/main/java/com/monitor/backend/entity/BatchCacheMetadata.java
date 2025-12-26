package com.monitor.backend.entity;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 批处理缓存元数据实体
 * <p>
 * 记录中间结果缓存的详细信息，用于追踪和管理批处理过程中的缓存数据。
 * </p>
 */
@Data
public class BatchCacheMetadata {

    private Long id;

    /** 缓存键 */
    private String cacheKey;

    /** 缓存类型: FILE/REDIS/ES/TEMP_TABLE */
    private String cacheType;

    /** 记录数 */
    private Long recordCount;

    /** 数据大小(字节) */
    private Long sizeBytes;

    /** 数据结构JSON */
    private String schemaInfo;

    /** 数据来源信息JSON */
    private String sourceInfo;

    /** 创建者(Job名称) */
    private String createdBy;

    /** 关联执行ID */
    private Long executionId;

    /** 过期时间 */
    private LocalDateTime expireTime;

    /** 是否已过期 */
    private Integer isExpired;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

package com.monitor.backend.entity;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 批处理缓存元数据实体
 */
@Data
@Schema(description = "批处理缓存元数据")
public class BatchCacheMetadata {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "缓存键")
    private String cacheKey;

    @Schema(description = "缓存类型: FILE/REDIS/ES/TEMP_TABLE")
    private String cacheType;

    @Schema(description = "记录数")
    private Long recordCount;

    @Schema(description = "数据大小(字节)")
    private Long sizeBytes;

    @Schema(description = "数据结构JSON")
    private String schemaInfo;

    @Schema(description = "数据来源信息JSON")
    private String sourceInfo;

    @Schema(description = "创建者(Job名称)")
    private String createdBy;

    @Schema(description = "关联执行ID")
    private Long executionId;

    @Schema(description = "过期时间")
    private LocalDateTime expireTime;

    @Schema(description = "是否已过期")
    private Integer isExpired;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}

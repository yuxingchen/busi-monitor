package com.monitor.backend.entity;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 批处理性能日志实体
 */
@Data
@Schema(description = "批处理性能日志")
public class BatchPerformanceLog {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "Job名称")
    private String jobName;

    @Schema(description = "Job执行ID")
    private Long executionId;

    @Schema(description = "步骤名称")
    private String stepName;

    @Schema(description = "分区ID")
    private String partitionId;

    @Schema(description = "状态: STARTED/COMPLETED/FAILED/STOPPED")
    private String status;

    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @Schema(description = "结束时间")
    private LocalDateTime endTime;

    @Schema(description = "执行时长(毫秒)")
    private Long durationMs;

    @Schema(description = "读取记录数")
    private Long readCount;

    @Schema(description = "写入记录数")
    private Long writeCount;

    @Schema(description = "跳过记录数")
    private Long skipCount;

    @Schema(description = "处理速率(条/秒)")
    private Double recordsPerSecond;

    @Schema(description = "缓存策略")
    private String cacheStrategy;

    @Schema(description = "缓存键")
    private String cacheKey;

    @Schema(description = "涉及的数据源ID列表")
    private String dataSourceIds;

    @Schema(description = "错误信息")
    private String errorMessage;

    @Schema(description = "额外信息JSON")
    private String extraInfo;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}

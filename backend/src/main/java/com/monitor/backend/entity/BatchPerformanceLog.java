package com.monitor.backend.entity;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 批处理性能日志实体
 * <p>
 * 记录批处理执行的性能信息，包括读取/写入数量、执行时长、处理速率等。
 * </p>
 */
@Data
public class BatchPerformanceLog {

    private Long id;

    /** Job名称 */
    private String jobName;

    /** Job执行ID */
    private Long executionId;

    /** 步骤名称 */
    private String stepName;

    /** 分区ID */
    private String partitionId;

    /** 状态: STARTED/COMPLETED/FAILED/STOPPED */
    private String status;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 执行时长(毫秒) */
    private Long durationMs;

    /** 读取记录数 */
    private Long readCount;

    /** 写入记录数 */
    private Long writeCount;

    /** 跳过记录数 */
    private Long skipCount;

    /** 处理速率(条/秒) */
    private Double recordsPerSecond;

    /** 缓存策略 */
    private String cacheStrategy;

    /** 缓存键 */
    private String cacheKey;

    /** 涉及的数据源ID列表 */
    private String dataSourceIds;

    /** 错误信息 */
    private String errorMessage;

    /** 额外信息JSON */
    private String extraInfo;

    private LocalDateTime createTime;
}

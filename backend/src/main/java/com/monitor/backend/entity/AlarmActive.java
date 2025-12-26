package com.monitor.backend.entity;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 活跃告警实体类
 * <p>
 * 记录当前未恢复的告警状态信息
 * </p>
 */
@Data
public class AlarmActive {

    /** 主键ID */
    private Long id;

    /** 关联的监控任务ID */
    private Long taskId;

    /** 任务类型: MONITOR_TASK/SERVER_TASK/WORKFLOW */
    private String taskType;

    /** 触发类型: THRESHOLD/YOY/MOM */
    private String triggerType;

    /** 触发时的值 */
    private Double triggerValue;

    /** 阈值 */
    private Double thresholdValue;

    /** 首次触发时间 */
    private LocalDateTime firstTriggerTime;

    /** 最后触发时间 */
    private LocalDateTime lastTriggerTime;

    /** 触发次数 */
    private Integer triggerCount;

    /** 状态: FIRING/ACKNOWLEDGED/SUPPRESSED/RESOLVED */
    private String status;

    /** 级别: INFO/WARNING/CRITICAL */
    private String level;

    /** 确认人 */
    private String acknowledgeBy;

    /** 确认时间 */
    private LocalDateTime acknowledgeTime;

    /** 恢复时间 */
    private LocalDateTime resolveTime;

    /** 抑制截止时间 */
    private LocalDateTime suppressedUntil;

    /** 告警消息 */
    private String message;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}


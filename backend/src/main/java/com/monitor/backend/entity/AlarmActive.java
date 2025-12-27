package com.monitor.backend.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活跃告警实体类
 */
@Data
@Schema(description = "活跃告警")
public class AlarmActive {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "关联的监控任务ID")
    private Long taskId;

    @Schema(description = "任务类型: MONITOR_TASK/SERVER_TASK/WORKFLOW")
    private String taskType;

    @Schema(description = "触发类型: THRESHOLD/YOY/MOM")
    private String triggerType;

    @Schema(description = "触发时的值")
    private Double triggerValue;

    @Schema(description = "阈值")
    private Double thresholdValue;

    @Schema(description = "首次触发时间")
    private LocalDateTime firstTriggerTime;

    @Schema(description = "最后触发时间")
    private LocalDateTime lastTriggerTime;

    @Schema(description = "触发次数")
    private Integer triggerCount;

    @Schema(description = "状态: FIRING/ACKNOWLEDGED/SUPPRESSED/RESOLVED")
    private String status;

    @Schema(description = "级别: INFO/WARNING/CRITICAL")
    private String level;

    @Schema(description = "确认人")
    private String acknowledgeBy;

    @Schema(description = "确认时间")
    private LocalDateTime acknowledgeTime;

    @Schema(description = "恢复时间")
    private LocalDateTime resolveTime;

    @Schema(description = "抑制截止时间")
    private LocalDateTime suppressedUntil;

    @Schema(description = "告警消息")
    private String message;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}

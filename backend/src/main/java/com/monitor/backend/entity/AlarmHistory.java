package com.monitor.backend.entity;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 告警历史记录实体类
 */
@Data
@Schema(description = "告警历史")
public class AlarmHistory {
    
    @Schema(description = "主键ID")
    private Long id;
    
    @Schema(description = "关联的监控任务ID")
    private Long taskId;
    
    @Schema(description = "告警渠道ID")
    private Long channelId;

    @Schema(description = "触发时间")
    private LocalDateTime triggerTime;

    @Schema(description = "触发类型（THRESHOLD/CHANGE/ABSENCE）")
    private String triggerType;
    
    @Schema(description = "触发时的实际值")
    private Double triggerValue;
    
    @Schema(description = "告警阈值")
    private Double thresholdValue;
    
    @Schema(description = "告警消息内容")
    private String message;
    
    @Schema(description = "发送是否成功（1=成功，0=失败）")
    private Integer isSuccess;
    
    @Schema(description = "发送失败时的错误信息")
    private String errorMessage;
}

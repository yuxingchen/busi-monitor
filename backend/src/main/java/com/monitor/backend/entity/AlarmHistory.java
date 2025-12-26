package com.monitor.backend.entity;


import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警历史记录实体类
 * <p>
 * 记录每次告警触发的详细信息，包括触发条件、阈值、发送结果等。
 * </p>
 *
 * @author monitor-system
 */
@Data
public class AlarmHistory {
    
    /** 主键ID */
    private Long id;
    
    /** 关联的监控任务ID */
    private Long taskId;
    
    /** 告警渠道ID */
    private Long channelId;

    /** 触发时间 */
    private LocalDateTime triggerTime;

    /** 触发类型（THRESHOLD/CHANGE/ABSENCE） */
    private String triggerType;
    
    /** 触发时的实际值 */
    private Double triggerValue;
    
    /** 告警阈值 */
    private Double thresholdValue;
    
    /** 告警消息内容 */
    private String message;
    
    /** 发送是否成功（1=成功，0=失败） */
    private Integer isSuccess;
    
    /** 发送失败时的错误信息 */
    private String errorMessage;

}

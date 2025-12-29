package com.monitor.backend.alarm;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 告警上下文
 * <p>
 * 用于传递告警触发的相关信息
 * </p>
 */
@Data
public class AlarmContext {

    /** 任务ID */
    private Long taskId;

    /** 任务类型: MONITOR_TASK, SERVER_TASK, WORKFLOW */
    private String taskType;

    /** 任务名称 */
    private String taskName;

    /** 触发类型: THRESHOLD, YOY, MOM */
    private String triggerType;

    /** 当前值 */
    private Double currentValue;

    /** 阈值 */
    private Double thresholdValue;

    /** 比较运算符 */
    private String operator;

    /** 告警级别: INFO, WARNING, CRITICAL */
    private String level;

    /** 告警渠道ID列表 */
    private List<Long> channelIds;

    /** 告警模板ID（优先使用指定模板生成消息） */
    private Long alarmTemplateId;

    /** 额外参数 */
    private Map<String, Object> extraParams;

}

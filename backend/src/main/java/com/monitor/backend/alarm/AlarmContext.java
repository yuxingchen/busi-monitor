package com.monitor.backend.alarm;

import com.monitor.backend.dto.GroupCompareItem;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    
    /** 触发时间 */
    private LocalDateTime triggerTime;
    
    /** 服务器IP地址（服务器任务时使用） */
    private String serverIp;

    /** 服务器名称（服务器任务时使用） */
    private String serverName;

    // ===== 同比环比相关字段 =====
    
    /** 历史值（同比环比时使用） */
    private Double previousValue;
    
    /** 变化值 = currentValue - previousValue */
    private Double changeValue;
    
    /** 变化率(%) = (changeValue / previousValue) * 100 */
    private Double changeRate;
    
    /** 对比周期标签（如："环比昨日"、"同比去年"） */
    private String periodLabel;
    
    /** 触发告警的分组列表（分组对比时使用） */
    private List<GroupCompareItem> alertGroups;
    
    /**
     * 获取触发分组明细的格式化字符串
     */
    public String getAlertGroupsStr() {
        if (alertGroups == null || alertGroups.isEmpty()) {
            return "";
        }
        return alertGroups.stream()
            .filter(GroupCompareItem::isAlert)
            .map(GroupCompareItem::format)
            .collect(Collectors.joining("; "));
    }
    
    /**
     * 获取触发告警的分组数量字符串
     */
    public String getAlertCountStr() {
        if (alertGroups == null || alertGroups.isEmpty()) {
            return "0";
        }
        long count = alertGroups.stream().filter(GroupCompareItem::isAlert).count();
        return String.valueOf(count);
    }
}

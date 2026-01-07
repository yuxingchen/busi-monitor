package com.monitor.backend.dto;

import lombok.Data;
import java.util.List;

/**
 * DATASET 类型任务告警配置
 * <p>
 * 存储在 chartConfig.alarm 中
 * </p>
 */
@Data
public class DatasetAlarmConfig {
    
    /** 是否启用告警 */
    private Boolean enabled;
    
    /** 告警模板ID */
    private Long alarmTemplateId;
    
    /** 告警渠道ID列表 */
    private List<Long> channelIds;
    
    /** 
     * 触发条件类型
     * - FIELD_VALUE: 字段值（检查每行的指定字段）
     * - FIELD_AGG: 字段聚合（对指定字段进行聚合计算）
     * - ROW_COUNT: 记录条数
     */
    private String triggerType;
    
    /** 触发条件字段（FIELD_VALUE/FIELD_AGG 时使用） */
    private String triggerField;
    
    /** 聚合方式: SUM, COUNT, AVG, MAX, MIN（FIELD_AGG 时使用） */
    private String aggregateMethod;
    
    /** 比较操作符: >, >=, <, <=, = */
    private String operator;
    
    /** 阈值 */
    private Double threshold;
    
    /** 字符串阈值（FIELD_VALUE 时使用，支持字符串比较） */
    private String thresholdStr;
    
    /** 用于模板参数的字段列表 */
    private List<String> templateFields;
    
    /** 告警字段解析的最大条数限制 */
    private Integer maxRows;
    
    /** 字段分隔符（默认逗号） */
    private String fieldSeparator;
    
    /**
     * 获取字段分隔符，默认返回逗号
     */
    public String getFieldSeparator() {
        return fieldSeparator != null ? fieldSeparator : ",";
    }
    
    /**
     * 获取最大行数，默认返回10
     */
    public Integer getMaxRows() {
        return maxRows != null && maxRows > 0 ? maxRows : 10;
    }
}

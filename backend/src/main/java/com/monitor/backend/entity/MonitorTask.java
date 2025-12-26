package com.monitor.backend.entity;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 监控任务实体类
 * <p>
 * 定义SQL类型的监控任务，包含数据源配置、定时表达式、告警规则等。
 * </p>
 *
 * @author monitor-system
 */
@Data
public class MonitorTask {
    
    /** 主键ID */
    private Long id;
    
    /** 任务名称 */
    private String name;
    
    /** 数据源ID */
    private Long datasourceId;
    
    /** SQL脚本 */
    private String sqlScript;
    
    /** Cron定时表达式 */
    private String cronExpression;
    
    /** 是否启用（1=启用，0=禁用） */
    private Integer isActive;
    
    /** 结果类型（SCALAR=单值，DATASET=数据集） */
    private String resultType;
    
    /** 告警阈值规则（JSON格式） */
    private String alarmThresholdRule;
    
    /** 图表配置（JSON格式） */
    private String chartConfig;
    
    /** 是否存储历史数据（1=存储，0=不存储） */
    private Integer isStoreData;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

}

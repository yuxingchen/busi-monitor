package com.monitor.backend.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 监控任务实体类
 */
@Data
@Schema(description = "监控任务")
public class MonitorTask {
    
    @Schema(description = "主键ID")
    private Long id;
    
    @Schema(description = "任务名称")
    private String name;
    
    @Schema(description = "数据源ID")
    private Long datasourceId;
    
    @Schema(description = "SQL脚本")
    private String sqlScript;
    
    @Schema(description = "Cron定时表达式")
    private String cronExpression;
    
    @Schema(description = "是否启用（1=启用，0=禁用）")
    private Integer isActive;
    
    @Schema(description = "结果类型（SCALAR=单值，DATASET=数据集）")
    private String resultType;
    
    @Schema(description = "告警配置（JSON格式）")
    private String alarmConfig;
    
    @Schema(description = "图表配置（JSON格式）")
    private String chartConfig;
    
    @Schema(description = "是否存储历史数据（1=存储，0=不存储）")
    private Integer isStoreData;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}

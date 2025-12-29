package com.monitor.backend.dto.task;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 监控任务请求DTO
 */
@Data
@Schema(description = "监控任务请求")
public class MonitorTaskRequest {

    @Schema(description = "主键ID，更新时必填")
    private Long id;

    @Schema(description = "任务名称")
    private String name;

    @Schema(description = "数据源ID")
    private Long datasourceId;

    @Schema(description = "SQL查询语句")
    private String sqlQuery;

    @Schema(description = "Cron定时表达式")
    private String cronExpression;

    @Schema(description = "告警阈值规则（JSON格式）")
    private String thresholdRule;

    @Schema(description = "告警渠道ID列表（逗号分隔）")
    private String alarmChannels;

    @Schema(description = "告警模板ID")
    private Long alarmTemplateId;

    @Schema(description = "是否启用（1=启用，0=禁用）")
    private Integer isActive;
}

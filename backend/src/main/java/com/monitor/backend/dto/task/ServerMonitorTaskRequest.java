package com.monitor.backend.dto.task;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 服务器监控任务请求DTO
 */
@Data
@Schema(description = "服务器监控任务请求")
public class ServerMonitorTaskRequest {

    @Schema(description = "主键ID，更新时必填")
    private Long id;

    @Schema(description = "任务名称")
    private String name;

    @Schema(description = "关联的服务器ID")
    private Long serverId;

    @Schema(description = "关联的模板ID")
    private Long templateId;

    @Schema(description = "采集脚本（Shell命令）")
    private String collectScript;

    @Schema(description = "脚本参数（JSON格式）")
    private String params;

    @Schema(description = "告警阈值规则（JSON格式）")
    private String thresholdRule;

    @Schema(description = "Cron定时表达式")
    private String cronExpression;

    @Schema(description = "告警渠道ID列表（逗号分隔）")
    private String alarmChannels;

    @Schema(description = "告警模板ID")
    private Long alarmTemplateId;

    @Schema(description = "是否启用（1=启用，0=禁用）")
    private Integer isActive;
}

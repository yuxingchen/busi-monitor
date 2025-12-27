package com.monitor.backend.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 服务器监控任务实体类
 */
@Data
@Schema(description = "服务器监控任务")
public class ServerMonitorTask {

    @Schema(description = "主键ID")
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

    @Schema(description = "最后执行时间")
    private LocalDateTime lastRunTime;

    @Schema(description = "最后执行状态（SUCCESS/FAILED）")
    private String lastRunStatus;

    @Schema(description = "最后执行结果值")
    private String lastRunValue;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "服务器名称", hidden = true)
    private transient String serverName;

    @Schema(description = "服务器IP", hidden = true)
    private transient String serverIp;

    @Schema(description = "模板名称", hidden = true)
    private transient String templateName;
}

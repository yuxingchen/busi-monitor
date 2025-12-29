package com.monitor.backend.dto.template;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 监控模板请求DTO
 */
@Data
@Schema(description = "监控模板请求")
public class MonitorTemplateRequest {

    @Schema(description = "主键ID，更新时必填")
    private Long id;

    @Schema(description = "模板名称")
    private String name;

    @Schema(description = "分类")
    private String category;

    @Schema(description = "采集脚本")
    private String collectScript;

    @Schema(description = "默认阈值规则（JSON格式）")
    private String defaultThreshold;

    @Schema(description = "默认Cron表达式")
    private String defaultCron;

    @Schema(description = "告警模板ID")
    private Long alarmTemplateId;

    @Schema(description = "是否启用（1=启用，0=禁用）")
    private Integer isActive;
}

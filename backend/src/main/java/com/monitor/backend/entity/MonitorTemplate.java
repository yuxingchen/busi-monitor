package com.monitor.backend.entity;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 监控模板实体类
 */
@Data
@Schema(description = "监控模板")
public class MonitorTemplate {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "模板名称")
    private String name;

    @Schema(description = "分类（BASIC/COMPONENT/APPLICATION）")
    private String category;

    @Schema(description = "子分类（CPU/MySQL/Redis等）")
    private String subCategory;

    @Schema(description = "采集类型（SSH_SCRIPT/API/SQL）")
    private String collectType;

    @Schema(description = "采集脚本")
    private String collectScript;

    @Schema(description = "参数定义（JSON格式）")
    private String paramSchema;

    @Schema(description = "默认告警阈值（JSON格式）")
    private String defaultThreshold;

    @Schema(description = "默认Cron表达式")
    private String defaultCron;

    @Schema(description = "关联的告警模板ID")
    private Long alarmTemplateId;

    @Schema(description = "告警模板名称", hidden = true)
    private transient String alarmTemplateName;

    @Schema(description = "模板描述")
    private String description;

    @Schema(description = "是否为系统内置模板（1=是，0=否）")
    private Integer isSystem;

    @Schema(description = "是否启用（1=启用，0=禁用）")
    private Integer isActive;

    @Schema(description = "排序顺序")
    private Integer sortOrder;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}

package com.monitor.backend.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工作流实体类
 */
@Data
@Schema(description = "工作流")
public class Workflow {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "工作流名称")
    private String name;

    @Schema(description = "工作流描述")
    private String description;

    @Schema(description = "Cron定时表达式")
    private String cronExpression;

    @Schema(description = "是否启用（1=启用，0=禁用）")
    private Integer isActive;

    @Schema(description = "执行超时时间（秒）")
    private Integer timeoutSeconds;

    @Schema(description = "结果输出表名")
    private String outputTable;

    @Schema(description = "索引字段配置")
    private String indexFields;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "步骤列表")
    private List<WorkflowStep> steps;
}

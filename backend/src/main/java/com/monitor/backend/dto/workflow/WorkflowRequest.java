package com.monitor.backend.dto.workflow;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 工作流请求DTO
 */
@Data
@Schema(description = "工作流请求")
public class WorkflowRequest {

    @Schema(description = "主键ID，更新时必填")
    private Long id;

    @Schema(description = "工作流名称")
    private String name;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "Cron定时表达式")
    private String cronExpression;

    @Schema(description = "超时时间（秒）")
    private Integer timeoutSeconds;

    @Schema(description = "索引字段（逗号分隔）")
    private String indexFields;

    @Schema(description = "是否启用（1=启用，0=禁用）")
    private Integer isActive;

    @Schema(description = "输出表名")
    private String outputTable;

    @Schema(description = "工作流步骤列表")
    private List<WorkflowStepRequest> steps;
}

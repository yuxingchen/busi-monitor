package com.monitor.backend.dto.workflow;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 工作流步骤请求DTO
 */
@Data
@Schema(description = "工作流步骤请求")
public class WorkflowStepRequest {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "步骤名称")
    private String name;

    @Schema(description = "步骤类型（SQL/CONSTANT/LOOP/SHELL）")
    private String stepType;

    @Schema(description = "步骤顺序")
    private Integer stepOrder;

    @Schema(description = "数据源ID")
    private Long datasourceId;

    @Schema(description = "SQL脚本")
    private String sqlScript;

    @Schema(description = "结果变量名")
    private String resultVariable;

    @Schema(description = "常量值（JSON格式）")
    private String constantValue;

    @Schema(description = "循环变量名")
    private String loopVariable;

    @Schema(description = "是否启用批处理")
    private Integer batchEnabled;

    @Schema(description = "批处理大小")
    private Integer batchSize;
}

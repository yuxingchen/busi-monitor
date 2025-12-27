package com.monitor.backend.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 工作流步骤实体类
 */
@Data
@Schema(description = "工作流步骤")
public class WorkflowStep {

    @Schema(description = "主键ID")
    private Long id;
    
    @Schema(description = "所属工作流ID")
    private Long workflowId;
    
    @Schema(description = "步骤执行顺序")
    private Integer stepOrder;
    
    @Schema(description = "步骤名称")
    private String name;
    
    @Schema(description = "步骤类型（SQL/TASK_REF）")
    private String stepType;
    
    @Schema(description = "数据源ID")
    private Long datasourceId;
    
    @Schema(description = "SQL脚本")
    private String sqlScript;
    
    @Schema(description = "结果变量名")
    private String resultVariable;
    
    @Schema(description = "其他配置（JSON格式）")
    private String config;
    
    @Schema(description = "编辑器X坐标")
    private Integer positionX;
    
    @Schema(description = "编辑器Y坐标")
    private Integer positionY;

    @Schema(description = "是否启用批处理（0=否, 1=是）")
    private Integer batchEnabled;
    
    @Schema(description = "分区主键列名")
    private String idColumn;
    
    @Schema(description = "分区数量")
    private Integer partitionCount;
    
    @Schema(description = "Chunk大小")
    private Integer chunkSize;
    
    @Schema(description = "缓存策略")
    private String cacheStrategy;
}

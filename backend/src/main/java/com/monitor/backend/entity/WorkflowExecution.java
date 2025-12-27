package com.monitor.backend.entity;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 工作流执行记录实体类
 */
@Data
@Schema(description = "工作流执行记录")
public class WorkflowExecution {

    @Schema(description = "主键ID")
    private Long id;
    
    @Schema(description = "关联的工作流ID")
    private Long workflowId;
    
    @Schema(description = "执行状态（PENDING/RUNNING/SUCCESS/FAILED/TIMEOUT）")
    private String status;
    
    @Schema(description = "开始时间")
    private LocalDateTime startTime;
    
    @Schema(description = "结束时间")
    private LocalDateTime endTime;
    
    @Schema(description = "各步骤执行结果（JSON格式）")
    private String stepResults;
    
    @Schema(description = "错误信息")
    private String errorMessage;

    @Schema(description = "工作流名称")
    private String workflowName;

    @Schema(description = "执行耗时（秒）")
    public Long getDurationSeconds() {
        if (startTime != null && endTime != null) {
            return java.time.Duration.between(startTime, endTime).getSeconds();
        }
        return null;
    }
}

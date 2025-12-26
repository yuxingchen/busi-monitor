package com.monitor.backend.entity;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 工作流执行记录实体类
 * <p>
 * 记录每次工作流执行的详细信息，包括开始/结束时间、状态、各步骤执行结果等。
 * </p>
 *
 * @author monitor-system
 */
@Data
public class WorkflowExecution {

    /** 主键ID */
    private Long id;
    
    /** 关联的工作流ID */
    private Long workflowId;
    
    /** 执行状态（PENDING/RUNNING/SUCCESS/FAILED/TIMEOUT） */
    private String status;
    
    /** 开始时间 */
    private LocalDateTime startTime;
    
    /** 结束时间 */
    private LocalDateTime endTime;
    
    /** 各步骤执行结果（JSON格式） */
    private String stepResults;
    
    /** 错误信息 */
    private String errorMessage;

    /** 工作流名称（非持久化字段，用于前端显示） */
    private String workflowName;

    /**
     * 计算执行耗时（秒）
     *
     * @return 执行耗时秒数，如果未完成则返回null
     */
    public Long getDurationSeconds() {
        if (startTime != null && endTime != null) {
            return java.time.Duration.between(startTime, endTime).getSeconds();
        }
        return null;
    }
}

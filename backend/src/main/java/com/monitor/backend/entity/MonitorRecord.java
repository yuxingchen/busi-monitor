package com.monitor.backend.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 监控执行记录实体类
 */
@Data
@Schema(description = "监控执行记录")
public class MonitorRecord {
    
    @Schema(description = "主键ID")
    private Long id;
    
    @Schema(description = "关联的监控任务ID")
    private Long taskId;

    @Schema(description = "执行时间")
    private LocalDateTime executionTime;

    @Schema(description = "执行是否成功（1=成功，0=失败）")
    private Integer isSuccess;
    
    @Schema(description = "执行失败时的错误信息")
    private String errorMessage;
    
    @Schema(description = "执行结果数值（用于单值类型任务）")
    private Double resultNumber;
    
    @Schema(description = "执行结果JSON（用于数据集类型任务）")
    private String resultJson;
}

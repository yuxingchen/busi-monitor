package com.monitor.backend.entity;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 监控执行记录实体类
 * <p>
 * 记录每次监控任务执行的结果，包括执行时间、成功状态、结果数据等。
 * </p>
 *
 * @author monitor-system
 */
@Data
public class MonitorRecord {
    
    /** 主键ID */
    private Long id;
    
    /** 关联的监控任务ID */
    private Long taskId;

    /** 执行时间 */
    private LocalDateTime executionTime;

    /** 执行是否成功（1=成功，0=失败） */
    private Integer isSuccess;
    
    /** 执行失败时的错误信息 */
    private String errorMessage;
    
    /** 执行结果数值（用于单值类型任务） */
    private Double resultNumber;
    
    /** 执行结果JSON（用于数据集类型任务） */
    private String resultJson;

}

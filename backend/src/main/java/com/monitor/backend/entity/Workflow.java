package com.monitor.backend.entity;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

/**
 * 工作流实体类
 * <p>
 * 定义数据处理工作流，支持多步骤SQL编排执行。
 * 可配置定时调度、超时时间、结果表和索引字段。
 * </p>
 *
 * @author monitor-system
 */
@Data
public class Workflow {

    /** 主键ID */
    private Long id;

    /** 工作流名称 */
    private String name;

    /** 工作流描述 */
    private String description;

    /** Cron定时表达式 */
    private String cronExpression;

    /** 是否启用（1=启用，0=禁用） */
    private Integer isActive;

    /** 执行超时时间（秒） */
    private Integer timeoutSeconds;

    /** 结果输出表名 */
    private String outputTable;

    /** 索引字段配置（JSON数组格式，如["order", "productName"]） */
    private String indexFields;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 关联的步骤列表（非持久化字段） */
    private List<WorkflowStep> steps;

}

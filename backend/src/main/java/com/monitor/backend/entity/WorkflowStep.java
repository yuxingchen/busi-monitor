package com.monitor.backend.entity;

import lombok.Data;

/**
 * 工作流步骤实体类
 * <p>
 * 定义工作流中的单个执行步骤，包含SQL脚本、数据源配置、位置信息等。
 * 步骤按stepOrder顺序执行。
 * </p>
 *
 * @author monitor-system
 */
@Data
public class WorkflowStep {

    /** 主键ID */
    private Long id;
    
    /** 所属工作流ID */
    private Long workflowId;
    
    /** 步骤执行顺序 */
    private Integer stepOrder;
    
    /** 步骤名称 */
    private String name;
    
    /** 步骤类型（SQL/TASK_REF） */
    private String stepType;
    
    /** 数据源ID */
    private Long datasourceId;
    
    /** SQL脚本 */
    private String sqlScript;
    
    /** 结果变量名（用于步骤间数据传递） */
    private String resultVariable;
    
    /** 其他配置（JSON格式） */
    private String config;
    
    /** 可视化编辑器X坐标 */
    private Integer positionX;
    
    /** 可视化编辑器Y坐标 */
    private Integer positionY;

    /** 是否启用批处理（0=否, 1=是） */
    private Integer batchEnabled;
    
    /** 分区主键列名（留空自动识别） */
    private String idColumn;
    
    /** 分区数量 */
    private Integer partitionCount;
    
    /** Chunk大小 */
    private Integer chunkSize;
    
    /** 缓存策略（FILE/REDIS/ES/TEMP_TABLE） */
    private String cacheStrategy;

}

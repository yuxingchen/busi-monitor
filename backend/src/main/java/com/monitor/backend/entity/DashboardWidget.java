package com.monitor.backend.entity;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 仪表盘组件实体类
 * <p>
 * 定义仪表盘中的可视化组件，如图表、时钟、文本等。
 * 每个组件关联到一个仪表盘，并具有位置和尺寸信息。
 * </p>
 *
 * @author monitor-system
 */
@Data
public class DashboardWidget {
    
    /** 主键ID */
    private Long id;
    
    /** 所属仪表盘ID */
    private Long dashboardId;
    
    /** 组件类型（TASK/WORKFLOW/CLOCK/TEXT/IMAGE） */
    private String widgetType;
    
    /** 数据源ID（任务ID或工作流ID） */
    private Long sourceId;
    
    /** 组件标题 */
    private String title;
    
    /** 网格X坐标 */
    private Integer gridX;
    
    /** 网格Y坐标 */
    private Integer gridY;
    
    /** 网格宽度（占用列数） */
    private Integer gridW;
    
    /** 网格高度（占用行数） */
    private Integer gridH;
    
    /** 显示配置（JSON格式，包含图表类型、颜色等） */
    private String displayConfig;
    
    /** Z轴层级（用于组件叠加排序） */
    private Integer zIndex;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 数据源名称（非持久化字段，用于前端显示） */
    private transient String sourceName;
    
    /** 数据源结果类型（非持久化字段，SCALAR/DATASET） */
    private transient String sourceResultType;

}

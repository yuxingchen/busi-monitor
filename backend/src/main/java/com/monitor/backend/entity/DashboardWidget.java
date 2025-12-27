package com.monitor.backend.entity;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 仪表盘组件实体类
 */
@Data
@Schema(description = "仪表盘组件")
public class DashboardWidget {
    
    @Schema(description = "主键ID")
    private Long id;
    
    @Schema(description = "所属仪表盘ID")
    private Long dashboardId;
    
    @Schema(description = "组件类型（TASK/WORKFLOW/CLOCK/TEXT/IMAGE）")
    private String widgetType;
    
    @Schema(description = "数据源ID（任务ID或工作流ID）")
    private Long sourceId;
    
    @Schema(description = "组件标题")
    private String title;
    
    @Schema(description = "网格X坐标")
    private Integer gridX;
    
    @Schema(description = "网格Y坐标")
    private Integer gridY;
    
    @Schema(description = "网格宽度（占用列数）")
    private Integer gridW;
    
    @Schema(description = "网格高度（占用行数）")
    private Integer gridH;
    
    @Schema(description = "显示配置（JSON格式）")
    private String displayConfig;
    
    @Schema(description = "Z轴层级")
    private Integer zIndex;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "数据源名称", hidden = true)
    private transient String sourceName;
    
    @Schema(description = "数据源结果类型", hidden = true)
    private transient String sourceResultType;
}

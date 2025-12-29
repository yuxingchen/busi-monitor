package com.monitor.backend.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 仪表盘组件请求DTO
 */
@Data
@Schema(description = "仪表盘组件请求")
public class DashboardWidgetRequest {

    @Schema(description = "主键ID，更新时必填")
    private Long id;

    @Schema(description = "仪表盘ID")
    private Long dashboardId;

    @Schema(description = "组件类型（TASK/WORKFLOW/CHART）")
    private String widgetType;

    @Schema(description = "关联的任务ID")
    private Long taskId;

    @Schema(description = "关联的工作流ID")
    private Long workflowId;

    @Schema(description = "图表配置（JSON格式）")
    private String chartConfig;

    @Schema(description = "栅格X坐标")
    private Integer gridX;

    @Schema(description = "栅格Y坐标")
    private Integer gridY;

    @Schema(description = "栅格宽度")
    private Integer gridW;

    @Schema(description = "栅格高度")
    private Integer gridH;

    @Schema(description = "层级")
    private Integer zIndex;
}

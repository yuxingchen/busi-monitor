package com.monitor.backend.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 仪表盘请求DTO
 */
@Data
@Schema(description = "仪表盘请求")
public class DashboardRequest {

    @Schema(description = "主键ID，更新时必填")
    private Long id;

    @Schema(description = "仪表盘名称")
    private String name;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "栅格列数")
    private Integer gridCols;

    @Schema(description = "栅格行数")
    private Integer gridRows;

    @Schema(description = "单元格高度(px)")
    private Integer cellHeight;

    @Schema(description = "是否为默认仪表盘")
    private Integer isDefault;

    @Schema(description = "是否启用")
    private Integer isActive;
}

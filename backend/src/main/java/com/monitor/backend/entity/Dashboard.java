package com.monitor.backend.entity;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 仪表盘实体类
 */
@Data
@Schema(description = "仪表盘")
public class Dashboard {
    
    @Schema(description = "主键ID")
    private Long id;
    
    @Schema(description = "仪表盘名称")
    private String name;
    
    @Schema(description = "网格列数")
    private Integer gridCols;
    
    @Schema(description = "网格行数")
    private Integer gridRows;
    
    @Schema(description = "单元格高度（像素）")
    private Integer cellHeight;
    
    @Schema(description = "组件间距（像素）")
    private Integer widgetMargin;
    
    @Schema(description = "背景配置（JSON格式）")
    private String backgroundConfig;
    
    @Schema(description = "是否为默认仪表盘（1=默认，0=非默认）")
    private Integer isDefault;
    
    @Schema(description = "是否启用（1=启用，0=禁用）")
    private Integer isActive;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}

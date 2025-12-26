package com.monitor.backend.entity;

import java.time.LocalDateTime;


import lombok.Data;

/**
 * 仪表盘实体类
 * <p>
 * 定义可视化仪表盘的布局配置，包括网格尺寸、背景样式等。
 * 一个仪表盘可包含多个组件（Widget）。
 * </p>
 *
 * @author monitor-system
 */
@Data
public class Dashboard {
    
    /** 主键ID */
    private Long id;
    
    /** 仪表盘名称 */
    private String name;
    
    /** 网格列数 */
    private Integer gridCols;
    
    /** 网格行数 */
    private Integer gridRows;
    
    /** 单元格高度（像素） */
    private Integer cellHeight;
    
    /** 组件间距（像素，默认为10） */
    private Integer widgetMargin;
    
    /** 背景配置（JSON格式，包含颜色、图片等） */
    private String backgroundConfig;
    
    /** 是否为默认仪表盘（1=默认，0=非默认） */
    private Integer isDefault;
    
    /** 是否启用（1=启用，0=禁用） */
    private Integer isActive;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

}

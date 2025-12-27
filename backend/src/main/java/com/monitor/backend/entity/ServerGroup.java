package com.monitor.backend.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 服务器分组实体类
 */
@Data
@Schema(description = "服务器分组")
public class ServerGroup {
    
    @Schema(description = "主键ID")
    private Long id;
    
    @Schema(description = "分组名称")
    private String name;
    
    @Schema(description = "分组描述")
    private String description;
    
    @Schema(description = "父分组ID（顶级分组为null）")
    private Long parentId;
    
    @Schema(description = "是否启用（1=启用，0=禁用）")
    private Integer isActive;
    
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}

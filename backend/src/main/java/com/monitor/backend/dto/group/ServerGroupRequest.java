package com.monitor.backend.dto.group;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 服务器分组请求DTO
 */
@Data
@Schema(description = "服务器分组请求")
public class ServerGroupRequest {

    @Schema(description = "主键ID，更新时必填")
    private Long id;

    @Schema(description = "分组名称")
    private String name;

    @Schema(description = "父分组ID（顶级分组为null）")
    private Long parentId;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "是否启用（1=启用，0=禁用）")
    private Integer isActive;
}

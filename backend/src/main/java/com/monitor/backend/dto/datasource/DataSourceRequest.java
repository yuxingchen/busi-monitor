package com.monitor.backend.dto.datasource;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 数据源请求DTO
 */
@Data
@Schema(description = "数据源请求")
public class DataSourceRequest {

    @Schema(description = "主键ID，更新时必填")
    private Long id;

    @Schema(description = "数据源名称")
    private String name;

    @Schema(description = "数据源类型（MySQL/PostgreSQL/Oracle等）")
    private String type;

    @Schema(description = "JDBC URL")
    private String url;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "密码（前端加密传输）")
    private String password;

    @Schema(description = "JDBC驱动类名")
    private String driverClassName;

    @Schema(description = "是否启用（1=启用，0=禁用）")
    private Integer isActive;
}

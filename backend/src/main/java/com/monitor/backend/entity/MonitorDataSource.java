package com.monitor.backend.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 监控数据源实体类
 */
@Data
@Schema(description = "监控数据源")
public class MonitorDataSource {
    
    @Schema(description = "主键ID")
    private Long id;
    
    @Schema(description = "数据源名称")
    private String name;
    
    @Schema(description = "JDBC连接URL")
    private String url;
    
    @Schema(description = "数据库用户名")
    private String username;
    
    @Schema(description = "加密后的数据库密码", hidden = true)
    private String passwordEncrypted;
    
    @Schema(description = "JDBC驱动类名")
    private String driverClassName;
    
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
    
    @Schema(description = "明文密码", hidden = true)
    private transient String password;
}

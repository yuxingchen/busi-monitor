package com.monitor.backend.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户实体类
 */
@Data
@Schema(description = "系统用户")
public class User {
    
    @Schema(description = "主键ID")
    private Long id;
    
    @Schema(description = "用户名")
    private String username;
    
    @Schema(description = "密码哈希值", hidden = true)
    private String passwordHash;
    
    @Schema(description = "角色（ADMIN/USER）")
    private String role;
    
    @Schema(description = "是否启用")
    private Integer enabled;
    
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}

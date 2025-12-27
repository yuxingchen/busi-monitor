package com.monitor.backend.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 服务器资产实体类
 */
@Data
@Schema(description = "服务器资产")
public class ServerAsset {
    
    @Schema(description = "主键ID")
    private Long id;
    
    @Schema(description = "服务器名称")
    private String name;
    
    @Schema(description = "服务器IP地址")
    private String ip;
    
    @Schema(description = "SSH端口号")
    private Integer port;
    
    @Schema(description = "SSH用户名")
    private String username;
    
    @Schema(description = "认证类型（PASSWORD/KEY）")
    private String authType;
    
    @Schema(description = "加密后的密码", hidden = true)
    private String passwordEncrypted;
    
    @Schema(description = "加密后的私钥", hidden = true)
    private String privateKeyEncrypted;
    
    @Schema(description = "所属分组ID")
    private Long groupId;
    
    @Schema(description = "标签（逗号分隔）")
    private String tags;
    
    @Schema(description = "是否启用（1=启用，0=禁用）")
    private Integer isActive;
    
    @Schema(description = "最后健康检查时间")
    private LocalDateTime lastCheckTime;
    
    @Schema(description = "最后健康检查状态（ONLINE/OFFLINE）")
    private String lastCheckStatus;
    
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
    
    @Schema(description = "明文密码", hidden = true)
    private transient String password;
    
    @Schema(description = "明文私钥", hidden = true)
    private transient String privateKey;
}

package com.monitor.backend.dto.server;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 服务器资产响应DTO
 *
 * @author monitor-system
 */
@Data
@Schema(description = "服务器资产响应")
public class ServerAssetResponse {
    
    @Schema(description = "服务器ID")
    private Long id;
    
    @Schema(description = "服务器名称")
    private String name;
    
    @Schema(description = "服务器IP地址")
    private String ip;
    
    @Schema(description = "SSH端口号")
    private Integer port;
    
    @Schema(description = "SSH用户名")
    private String username;
    
    @Schema(description = "认证类型")
    private String authType;
    
    @Schema(description = "所属分组ID")
    private Long groupId;
    
    @Schema(description = "标签")
    private String tags;
    
    @Schema(description = "是否启用")
    private Integer isActive;
    
    @Schema(description = "最后健康检查时间")
    private LocalDateTime lastCheckTime;
    
    @Schema(description = "最后健康检查状态")
    private String lastCheckStatus;
    
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}

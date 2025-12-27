package com.monitor.backend.dto.server;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * 服务器资产请求DTO
 *
 * @author monitor-system
 */
@Data
@Schema(description = "服务器资产请求参数")
public class ServerAssetRequest {
    
    @Schema(description = "服务器ID（更新时必填）")
    private Long id;
    
    @NotBlank(message = "服务器名称不能为空")
    @Size(max = 100, message = "服务器名称长度不能超过100个字符")
    @Schema(description = "服务器名称", example = "web-server-01", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;
    
    @NotBlank(message = "IP地址不能为空")
    @Pattern(regexp = "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$", 
             message = "IP地址格式不正确")
    @Schema(description = "服务器IP地址", example = "192.168.1.100", requiredMode = Schema.RequiredMode.REQUIRED)
    private String ip;
    
    @Min(value = 1, message = "端口号最小为1")
    @Max(value = 65535, message = "端口号最大为65535")
    @Schema(description = "SSH端口号", example = "22", defaultValue = "22")
    private Integer port = 22;
    
    @NotBlank(message = "用户名不能为空")
    @Size(max = 50, message = "用户名长度不能超过50个字符")
    @Schema(description = "SSH用户名", example = "root", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;
    
    @Schema(description = "认证类型", example = "PASSWORD", allowableValues = {"PASSWORD", "KEY"})
    private String authType = "PASSWORD";
    
    @Schema(description = "密码（经前端加密传输）")
    private String password;
    
    @Schema(description = "私钥（经前端加密传输）")
    private String privateKey;
    
    @Schema(description = "所属分组ID")
    private Long groupId;
    
    @Size(max = 500, message = "标签长度不能超过500个字符")
    @Schema(description = "标签（逗号分隔）", example = "production,web")
    private String tags;
}

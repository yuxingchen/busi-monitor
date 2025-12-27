package com.monitor.backend.dto.server;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 批量添加服务器请求DTO
 *
 * @author monitor-system
 */
@Data
@Schema(description = "批量添加服务器请求参数")
public class BatchAddServerRequest {
    
    @NotBlank(message = "IP输入不能为空")
    @Schema(description = "IP地址输入（支持单个IP、逗号分隔、IP段、CIDR）", 
            example = "192.168.1.1-10 或 192.168.1.0/24", 
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String ipInput;
    
    @Schema(description = "服务器名称前缀")
    private String name;
    
    @NotBlank(message = "用户名不能为空")
    @Schema(description = "SSH用户名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;
    
    @Schema(description = "密码（经前端加密传输）")
    private String password;
    
    @Min(value = 1, message = "端口号最小为1")
    @Max(value = 65535, message = "端口号最大为65535")
    @Schema(description = "SSH端口号", defaultValue = "22")
    private Integer port = 22;
    
    @Schema(description = "所属分组ID")
    private Long groupId;
}

package com.monitor.backend.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求DTO
 *
 * @author monitor-system
 */
@Data
@Schema(description = "登录请求参数")
public class LoginRequest {
    
    @NotBlank(message = "用户名不能为空")
    @Schema(description = "用户名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;
    
    @NotBlank(message = "密码不能为空")
    @Schema(description = "密码（经前端加密传输）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;
}

package com.monitor.backend.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 登录响应DTO
 *
 * @author monitor-system
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "登录响应")
public class LoginResponse {
    
    @Schema(description = "JWT Token")
    private String token;
    
    @Schema(description = "用户名")
    private String username;
    
    @Schema(description = "用户角色")
    private String role;
}

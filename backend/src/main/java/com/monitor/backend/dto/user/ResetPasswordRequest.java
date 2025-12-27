package com.monitor.backend.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 重置密码请求DTO
 *
 * @author monitor-system
 */
@Data
@Schema(description = "重置密码请求参数")
public class ResetPasswordRequest {
    
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, message = "密码不能少于6位")
    @Schema(description = "新密码（经前端加密传输）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;
}

package com.monitor.backend.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户请求DTO
 *
 * @author monitor-system
 */
@Data
@Schema(description = "用户请求参数")
public class UserRequest {
    
    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 50, message = "用户名长度应在2-50个字符之间")
    @Schema(description = "用户名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;
    
    @Schema(description = "密码（经前端加密传输）")
    private String password;
    
    @Schema(description = "角色", allowableValues = {"ADMIN", "USER"}, defaultValue = "USER")
    private String role = "USER";
}

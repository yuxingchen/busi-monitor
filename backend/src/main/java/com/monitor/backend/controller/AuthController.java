package com.monitor.backend.controller;

import com.monitor.backend.common.ApiResponse;
import com.monitor.backend.dto.auth.LoginRequest;
import com.monitor.backend.dto.auth.LoginResponse;
import com.monitor.backend.entity.User;
import com.monitor.backend.exception.BusinessException;
import com.monitor.backend.exception.ErrorCode;
import com.monitor.backend.mapper.UserMapper;
import com.monitor.backend.service.JwtTokenService;
import com.monitor.backend.service.TransmitEncryptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证控制器
 * <p>
 * 处理登录、注销等认证相关请求。
 * </p>
 *
 * @author monitor-system
 */
@Tag(name = "认证管理", description = "用户登录、获取个人信息、修改密码")
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    
    private final UserMapper userMapper;
    private final JwtTokenService jwtTokenService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final TransmitEncryptionService transmitEncryptionService;
    
    public AuthController(UserMapper userMapper, JwtTokenService jwtTokenService,
                          TransmitEncryptionService transmitEncryptionService) {
        this.userMapper = userMapper;
        this.jwtTokenService = jwtTokenService;
        this.passwordEncoder = new BCryptPasswordEncoder();
        this.transmitEncryptionService = transmitEncryptionService;
    }
    
    @Operation(summary = "用户登录", description = "验证用户名密码，返回JWT Token")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("用户登录: username={}", request.getUsername());
        
        // 解密前端传输的加密密码
        String password = transmitEncryptionService.decrypt(request.getPassword());
        
        User user = userMapper.findByUsername(request.getUsername());
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        
        if (user.getEnabled() == null || user.getEnabled() != 1) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }
        
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_INCORRECT);
        }
        
        // 生成Token
        String token = jwtTokenService.generateToken(user.getUsername(), user.getRole());
        
        LoginResponse response = new LoginResponse(token, user.getUsername(), user.getRole());
        return ApiResponse.ok(response);
    }
    
    @Operation(summary = "获取当前用户信息", description = "根据Token获取当前登录用户信息")
    @GetMapping("/profile")
    public ApiResponse<LoginResponse> getProfile(
            @Parameter(description = "Authorization Header") 
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        
        String token = authHeader.substring(7);
        String username = jwtTokenService.validateTokenAndGetUsername(token);
        
        if (username == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Token无效或已过期");
        }
        
        User user = userMapper.findByUsername(username);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        
        LoginResponse response = new LoginResponse(null, user.getUsername(), user.getRole());
        return ApiResponse.ok(response);
    }
    
    @Operation(summary = "修改密码", description = "修改当前用户的密码")
    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(
            @Parameter(description = "Authorization Header")
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, String> request) {
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        
        String token = authHeader.substring(7);
        String username = jwtTokenService.validateTokenAndGetUsername(token);
        
        if (username == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Token无效或已过期");
        }
        
        User user = userMapper.findByUsername(username);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        
        String oldPassword = request.get("oldPassword");
        String newPassword = request.get("newPassword");
        
        if (oldPassword == null || newPassword == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "原密码和新密码不能为空");
        }
        
        // 解密前端传输的加密密码
        oldPassword = transmitEncryptionService.decrypt(oldPassword);
        newPassword = transmitEncryptionService.decrypt(newPassword);
        
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_INCORRECT, "原密码错误");
        }
        
        String newPasswordHash = passwordEncoder.encode(newPassword);
        userMapper.updatePassword(user.getId(), newPasswordHash);
        
        log.info("用户修改密码: username={}", username);
        
        return ApiResponse.ok("密码修改成功", null);
    }
}

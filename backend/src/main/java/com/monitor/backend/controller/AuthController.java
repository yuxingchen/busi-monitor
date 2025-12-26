package com.monitor.backend.controller;

import com.monitor.backend.entity.User;
import com.monitor.backend.mapper.UserMapper;
import com.monitor.backend.service.JwtTokenService;
import com.monitor.backend.service.TransmitEncryptionService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证控制器
 * 处理登录、注销等认证相关请求
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
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
    
    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest request) {
        Map<String, Object> result = new HashMap<>();
        
        if (request.getUsername() == null || request.getPassword() == null) {
            result.put("success", false);
            result.put("message", "用户名和密码不能为空");
            return result;
        }
        
        // 解密前端传输的加密密码
        String password = transmitEncryptionService.decrypt(request.getPassword());
        
        User user = userMapper.findByUsername(request.getUsername());
        if (user == null) {
            result.put("success", false);
            result.put("message", "用户不存在");
            return result;
        }
        
        if (user.getEnabled() == null || user.getEnabled() != 1) {
            result.put("success", false);
            result.put("message", "用户已被禁用");
            return result;
        }
        
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            result.put("success", false);
            result.put("message", "密码错误");
            return result;
        }
        
        // 生成Token
        String token = jwtTokenService.generateToken(user.getUsername(), user.getRole());
        
        result.put("success", true);
        result.put("token", token);
        result.put("username", user.getUsername());
        result.put("role", user.getRole());
        return result;
    }
    
    /**
     * 获取当前用户信息
     */
    @GetMapping("/profile")
    public Map<String, Object> getProfile(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        Map<String, Object> result = new HashMap<>();
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            result.put("success", false);
            result.put("message", "未登录");
            return result;
        }
        
        String token = authHeader.substring(7);
        String username = jwtTokenService.validateTokenAndGetUsername(token);
        
        if (username == null) {
            result.put("success", false);
            result.put("message", "Token无效或已过期");
            return result;
        }
        
        User user = userMapper.findByUsername(username);
        if (user == null) {
            result.put("success", false);
            result.put("message", "用户不存在");
            return result;
        }
        
        result.put("success", true);
        result.put("username", user.getUsername());
        result.put("role", user.getRole());
        return result;
    }
    
    /**
     * 修改密码
     */
    @PostMapping("/change-password")
    public Map<String, Object> changePassword(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody ChangePasswordRequest request) {
        Map<String, Object> result = new HashMap<>();
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            result.put("success", false);
            result.put("message", "未登录");
            return result;
        }
        
        String token = authHeader.substring(7);
        String username = jwtTokenService.validateTokenAndGetUsername(token);
        
        if (username == null) {
            result.put("success", false);
            result.put("message", "Token无效或已过期");
            return result;
        }
        
        User user = userMapper.findByUsername(username);
        if (user == null) {
            result.put("success", false);
            result.put("message", "用户不存在");
            return result;
        }
        
        // 解密前端传输的加密密码
        String oldPassword = transmitEncryptionService.decrypt(request.getOldPassword());
        String newPassword = transmitEncryptionService.decrypt(request.getNewPassword());
        
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            result.put("success", false);
            result.put("message", "原密码错误");
            return result;
        }
        
        String newPasswordHash = passwordEncoder.encode(newPassword);
        userMapper.updatePassword(user.getId(), newPasswordHash);
        
        result.put("success", true);
        result.put("message", "密码修改成功");
        return result;
    }
    
    // --- 内部请求类 ---
    
    public static class LoginRequest {
        private String username;
        private String password;
        
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
    
    public static class ChangePasswordRequest {
        private String oldPassword;
        private String newPassword;
        
        public String getOldPassword() { return oldPassword; }
        public void setOldPassword(String oldPassword) { this.oldPassword = oldPassword; }
        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    }
}

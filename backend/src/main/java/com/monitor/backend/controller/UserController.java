package com.monitor.backend.controller;

import com.monitor.backend.entity.User;
import com.monitor.backend.mapper.UserMapper;
import com.monitor.backend.service.TransmitEncryptionService;
import lombok.Data;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户管理控制器
 */
@RestController
@RequestMapping("/api/user")
public class UserController {
    
    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final TransmitEncryptionService transmitEncryptionService;
    
    public UserController(UserMapper userMapper, TransmitEncryptionService transmitEncryptionService) {
        this.userMapper = userMapper;
        this.passwordEncoder = new BCryptPasswordEncoder();
        this.transmitEncryptionService = transmitEncryptionService;
    }
    
    /**
     * 分页查询用户列表
     */
    @GetMapping("/list")
    public Map<String, Object> list(
            @RequestParam(defaultValue = "") String username,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Map<String, Object> result = new HashMap<>();
        
        int offset = (page - 1) * size;
        List<User> users = userMapper.findByPage(username, offset, size);
        int total = userMapper.countByUsername(username);
        
        // 隐藏密码哈希
        users.forEach(u -> u.setPasswordHash(null));
        
        result.put("success", true);
        result.put("data", users);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }
    
    /**
     * 获取单个用户
     */
    @GetMapping("/{id}")
    public Map<String, Object> getById(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        User user = userMapper.findById(id);
        if (user == null) {
            result.put("success", false);
            result.put("message", "用户不存在");
            return result;
        }
        user.setPasswordHash(null);
        result.put("success", true);
        result.put("data", user);
        return result;
    }
    
    /**
     * 新增用户
     */
    @PostMapping
    public Map<String, Object> add(@RequestBody UserRequest request) {
        Map<String, Object> result = new HashMap<>();
        
        // 验证用户名
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "用户名不能为空");
            return result;
        }
        
        // 检查用户名是否已存在
        User existing = userMapper.findByUsername(request.getUsername());
        if (existing != null) {
            result.put("success", false);
            result.put("message", "用户名已存在");
            return result;
        }
        
        // 验证密码
        if (request.getPassword() == null || request.getPassword().length() < 6) {
            result.put("success", false);
            result.put("message", "密码不能少于6位");
            return result;
        }
        
        // 解密前端传输的加密密码
        String password = transmitEncryptionService.decrypt(request.getPassword());
        
        User user = new User();
        user.setUsername(request.getUsername().trim());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(request.getRole() != null ? request.getRole() : "USER");
        user.setEnabled(1);
        
        userMapper.insert(user);
        
        result.put("success", true);
        result.put("message", "用户创建成功");
        return result;
    }
    
    /**
     * 修改用户
     */
    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody UserRequest request) {
        Map<String, Object> result = new HashMap<>();
        
        User user = userMapper.findById(id);
        if (user == null) {
            result.put("success", false);
            result.put("message", "用户不存在");
            return result;
        }
        
        // 如果修改了用户名，检查是否重复
        if (request.getUsername() != null && !request.getUsername().equals(user.getUsername())) {
            User existingUser = userMapper.findByUsername(request.getUsername());
            if (existingUser != null) {
                result.put("success", false);
                result.put("message", "用户名已被使用");
                return result;
            }
            user.setUsername(request.getUsername().trim());
        }
        
        // 如果提供了新密码则更新
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            // 解密前端传输的加密密码
            String password = transmitEncryptionService.decrypt(request.getPassword());
            if (password.length() < 6) {
                result.put("success", false);
                result.put("message", "密码不能少于6位");
                return result;
            }
            user.setPasswordHash(passwordEncoder.encode(password));
        }
        
        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }
        
        userMapper.update(user);
        
        result.put("success", true);
        result.put("message", "用户更新成功");
        return result;
    }
    
    /**
     * 删除用户
     */
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        
        User user = userMapper.findById(id);
        if (user == null) {
            result.put("success", false);
            result.put("message", "用户不存在");
            return result;
        }
        
        // 防止删除admin用户
        if ("admin".equals(user.getUsername())) {
            result.put("success", false);
            result.put("message", "不能删除admin用户");
            return result;
        }
        
        userMapper.deleteById(id);
        
        result.put("success", true);
        result.put("message", "用户删除成功");
        return result;
    }
    
    /**
     * 启用/禁用用户
     */
    @PostMapping("/{id}/toggle-status")
    public Map<String, Object> toggleStatus(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        
        User user = userMapper.findById(id);
        if (user == null) {
            result.put("success", false);
            result.put("message", "用户不存在");
            return result;
        }
        
        // 防止禁用admin用户
        if ("admin".equals(user.getUsername()) && user.getEnabled() == 1) {
            result.put("success", false);
            result.put("message", "不能禁用admin用户");
            return result;
        }
        
        int newStatus = user.getEnabled() == 1 ? 0 : 1;
        userMapper.updateEnabled(id, newStatus);
        
        result.put("success", true);
        result.put("message", newStatus == 1 ? "用户已启用" : "用户已禁用");
        return result;
    }
    
    /**
     * 重置用户密码
     */
    @PostMapping("/{id}/reset-password")
    public Map<String, Object> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Map<String, Object> result = new HashMap<>();
        
        User user = userMapper.findById(id);
        if (user == null) {
            result.put("success", false);
            result.put("message", "用户不存在");
            return result;
        }
        
        String newPassword = body.get("password");
        // 解密前端传输的加密密码
        newPassword = transmitEncryptionService.decrypt(newPassword);
        if (newPassword == null || newPassword.length() < 6) {
            result.put("success", false);
            result.put("message", "密码不能少于6位");
            return result;
        }
        
        userMapper.updatePassword(id, passwordEncoder.encode(newPassword));
        
        result.put("success", true);
        result.put("message", "密码重置成功");
        return result;
    }
    
    // --- 内部请求类 ---
    @Data
    public static class UserRequest {
        private String username;
        private String password;
        private String role;
    }
}

package com.monitor.backend.controller;

import com.monitor.backend.common.ApiResponse;
import com.monitor.backend.common.PageResult;
import com.monitor.backend.dto.user.ResetPasswordRequest;
import com.monitor.backend.dto.user.UserRequest;
import com.monitor.backend.entity.User;
import com.monitor.backend.exception.BusinessException;
import com.monitor.backend.exception.ErrorCode;
import com.monitor.backend.mapper.UserMapper;
import com.monitor.backend.service.TransmitEncryptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户管理控制器
 * <p>
 * 提供用户的增删改查、状态切换、密码重置等功能。
 * </p>
 *
 * @author monitor-system
 */
@Tag(name = "用户管理", description = "系统用户的增删改查、状态切换、密码重置")
@RestController
@RequestMapping("/api/user")
public class UserController {
    
    private static final Logger log = LoggerFactory.getLogger(UserController.class);
    
    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final TransmitEncryptionService transmitEncryptionService;
    
    public UserController(UserMapper userMapper, TransmitEncryptionService transmitEncryptionService) {
        this.userMapper = userMapper;
        this.passwordEncoder = new BCryptPasswordEncoder();
        this.transmitEncryptionService = transmitEncryptionService;
    }
    
    @Operation(summary = "分页查询用户", description = "根据用户名模糊搜索，分页返回用户列表")
    @GetMapping("/list")
    public ApiResponse<PageResult<User>> list(
            @Parameter(description = "用户名关键字") @RequestParam(defaultValue = "") String username,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int size) {
        
        int offset = (page - 1) * size;
        List<User> users = userMapper.findByPage(username, offset, size);
        int total = userMapper.countByUsername(username);
        
        // 隐藏密码哈希
        users.forEach(u -> u.setPasswordHash(null));
        
        return ApiResponse.ok(PageResult.of(users, total, page, size));
    }
    
    @Operation(summary = "获取用户详情", description = "根据ID查询用户详情")
    @GetMapping("/{id}")
    public ApiResponse<User> getById(
            @Parameter(description = "用户ID") @PathVariable Long id) {
        User user = userMapper.findById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        user.setPasswordHash(null);
        return ApiResponse.ok(user);
    }
    
    @Operation(summary = "新增用户", description = "创建新用户")
    @PostMapping
    public ApiResponse<Void> add(@Valid @RequestBody UserRequest request) {
        log.info("创建用户: username={}", request.getUsername());
        
        // 检查用户名是否已存在
        User existing = userMapper.findByUsername(request.getUsername());
        if (existing != null) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
        }
        
        // 验证密码
        if (request.getPassword() == null || request.getPassword().length() < 6) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "密码不能少于6位");
        }
        
        // 解密前端传输的加密密码
        String password = transmitEncryptionService.decrypt(request.getPassword());
        
        User user = new User();
        user.setUsername(request.getUsername().trim());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(request.getRole() != null ? request.getRole() : "USER");
        user.setEnabled(1);
        
        userMapper.insert(user);
        
        return ApiResponse.ok("用户创建成功", null);
    }
    
    @Operation(summary = "修改用户", description = "更新用户信息")
    @PutMapping("/{id}")
    public ApiResponse<Void> update(
            @Parameter(description = "用户ID") @PathVariable Long id, 
            @Valid @RequestBody UserRequest request) {
        
        log.info("更新用户: id={}", id);
        
        User user = userMapper.findById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        
        // 如果修改了用户名，检查是否重复
        if (request.getUsername() != null && !request.getUsername().equals(user.getUsername())) {
            User existingUser = userMapper.findByUsername(request.getUsername());
            if (existingUser != null) {
                throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, "用户名已被使用");
            }
            user.setUsername(request.getUsername().trim());
        }
        
        // 如果提供了新密码则更新
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            String password = transmitEncryptionService.decrypt(request.getPassword());
            if (password.length() < 6) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "密码不能少于6位");
            }
            user.setPasswordHash(passwordEncoder.encode(password));
        }
        
        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }
        
        userMapper.update(user);
        
        return ApiResponse.ok("用户更新成功", null);
    }
    
    @Operation(summary = "删除用户", description = "根据ID删除用户")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @Parameter(description = "用户ID") @PathVariable Long id) {
        
        User user = userMapper.findById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        
        // 防止删除admin用户
        if ("admin".equals(user.getUsername())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能删除admin用户");
        }
        
        log.info("删除用户: id={}, username={}", id, user.getUsername());
        userMapper.deleteById(id);
        
        return ApiResponse.ok("用户删除成功", null);
    }
    
    @Operation(summary = "切换用户状态", description = "启用或禁用用户")
    @PostMapping("/{id}/toggle-status")
    public ApiResponse<Void> toggleStatus(
            @Parameter(description = "用户ID") @PathVariable Long id) {
        
        User user = userMapper.findById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        
        // 防止禁用admin用户
        if ("admin".equals(user.getUsername()) && user.getEnabled() == 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能禁用admin用户");
        }
        
        int newStatus = user.getEnabled() == 1 ? 0 : 1;
        userMapper.updateEnabled(id, newStatus);
        
        log.info("切换用户状态: id={}, newStatus={}", id, newStatus);
        
        return ApiResponse.ok(newStatus == 1 ? "用户已启用" : "用户已禁用", null);
    }
    
    @Operation(summary = "重置密码", description = "重置用户密码")
    @PostMapping("/{id}/reset-password")
    public ApiResponse<Void> resetPassword(
            @Parameter(description = "用户ID") @PathVariable Long id, 
            @Valid @RequestBody ResetPasswordRequest request) {
        
        User user = userMapper.findById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        
        // 解密前端传输的加密密码
        String newPassword = transmitEncryptionService.decrypt(request.getPassword());
        if (newPassword == null || newPassword.length() < 6) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "密码不能少于6位");
        }
        
        userMapper.updatePassword(id, passwordEncoder.encode(newPassword));
        
        log.info("重置用户密码: id={}, username={}", id, user.getUsername());
        
        return ApiResponse.ok("密码重置成功", null);
    }
}

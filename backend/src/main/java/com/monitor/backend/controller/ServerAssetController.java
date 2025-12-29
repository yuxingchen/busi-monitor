package com.monitor.backend.controller;

import com.monitor.backend.common.ApiResponse;
import com.monitor.backend.component.IpRangeParser;
import com.monitor.backend.dto.server.BatchAddServerRequest;
import com.monitor.backend.dto.server.ServerAssetRequest;
import com.monitor.backend.entity.ServerAsset;
import com.monitor.backend.exception.BusinessException;
import com.monitor.backend.exception.ErrorCode;
import com.monitor.backend.mapper.ServerAssetMapper;
import com.monitor.backend.service.EncryptionService;
import com.monitor.backend.service.SshExecutorService;
import com.monitor.backend.service.TransmitEncryptionService;
import com.monitor.backend.util.DateTimeUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务器资产管理控制器
 * <p>
 * 提供服务器资产的CRUD操作、批量导入、SSH连接测试等功能。
 * </p>
 *
 * @author monitor-system
 */
@Tag(name = "服务器资产管理", description = "服务器资产的增删改查、批量导入、SSH连接测试")
@RestController
@RequestMapping("/api/server-asset")
public class ServerAssetController {
    
    private static final Logger log = LoggerFactory.getLogger(ServerAssetController.class);
    
    private final ServerAssetMapper assetMapper;
    private final EncryptionService encryptionService;
    private final TransmitEncryptionService transmitEncryptionService;
    private final SshExecutorService sshExecutorService;
    private final IpRangeParser ipRangeParser;
    
    public ServerAssetController(ServerAssetMapper assetMapper, 
                                  EncryptionService encryptionService,
                                  TransmitEncryptionService transmitEncryptionService,
                                  SshExecutorService sshExecutorService,
                                  IpRangeParser ipRangeParser) {
        this.assetMapper = assetMapper;
        this.encryptionService = encryptionService;
        this.transmitEncryptionService = transmitEncryptionService;
        this.sshExecutorService = sshExecutorService;
        this.ipRangeParser = ipRangeParser;
    }
    
    @Operation(summary = "获取所有服务器", description = "查询所有服务器资产列表")
    @GetMapping
    public ApiResponse<List<ServerAsset>> listAll() {
        log.debug("查询所有服务器资产");
        return ApiResponse.ok(assetMapper.findAll());
    }
    
    @Operation(summary = "获取服务器详情", description = "根据ID查询服务器资产详情")
    @GetMapping("/{id}")
    public ApiResponse<ServerAsset> getById(
            @Parameter(description = "服务器ID") @PathVariable Long id) {
        ServerAsset asset = assetMapper.findById(id);
        if (asset == null) {
            throw new BusinessException(ErrorCode.SERVER_NOT_FOUND);
        }
        return ApiResponse.ok(asset);
    }
    
    @Operation(summary = "按分组查询服务器", description = "根据分组ID查询服务器列表")
    @GetMapping("/group/{groupId}")
    public ApiResponse<List<ServerAsset>> listByGroup(
            @Parameter(description = "分组ID") @PathVariable Long groupId) {
        return ApiResponse.ok(assetMapper.findByGroupId(groupId));
    }
    
    @Operation(summary = "添加服务器", description = "添加单个服务器资产")
    @PostMapping
    public ApiResponse<Long> add(@Valid @RequestBody ServerAssetRequest request) {
        log.info("添加服务器: name={}, ip={}", request.getName(), request.getIp());
        
        ServerAsset asset = convertToEntity(request);
        processPassword(asset, request.getPassword(), request.getPrivateKey());
        asset.setIsActive(1);
        
        assetMapper.insert(asset);
        
        return ApiResponse.ok("添加成功", asset.getId());
    }
    
    @Operation(summary = "批量添加服务器", description = "支持IP段批量添加服务器")
    @PostMapping("/batch")
    public ApiResponse<Map<String, Object>> batchAdd(@Valid @RequestBody BatchAddServerRequest request) {
        log.info("批量添加服务器: ipInput={}", request.getIpInput());
        
        List<String> ips = ipRangeParser.parse(request.getIpInput());
        
        // 解密前端传输的加密密码后再加密存储
        String encryptedPassword = null;
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            String plainPassword = transmitEncryptionService.decrypt(request.getPassword());
            encryptedPassword = encryptionService.encrypt(plainPassword);
        }
        
        int successCount = 0;
        List<String> failedIps = new ArrayList<>();
        
        for (String ip : ips) {
            try {
                // 检查是否已存在
                if (assetMapper.findByIp(ip) != null) {
                    failedIps.add(ip + "(已存在)");
                    continue;
                }
                
                ServerAsset asset = new ServerAsset();
                asset.setName(request.getName() != null ? request.getName() : ip);
                asset.setIp(ip);
                asset.setPort(request.getPort());
                asset.setUsername(request.getUsername());
                asset.setAuthType("PASSWORD");
                asset.setPasswordEncrypted(encryptedPassword);
                asset.setGroupId(request.getGroupId());
                asset.setIsActive(1);
                
                assetMapper.insert(asset);
                successCount++;
            } catch (Exception e) {
                log.warn("添加服务器失败: ip={}, error={}", ip, e.getMessage());
                failedIps.add(ip);
            }
        }
        
        Map<String, Object> data = new HashMap<>();
        data.put("total", ips.size());
        data.put("successCount", successCount);
        data.put("failedIps", failedIps);
        
        return ApiResponse.ok("批量添加完成", data);
    }
    
    @Operation(summary = "更新服务器", description = "更新服务器资产信息")
    @PutMapping
    public ApiResponse<Void> update(@Valid @RequestBody ServerAssetRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "服务器ID不能为空");
        }
        
        log.info("更新服务器: id={}, name={}", request.getId(), request.getName());
        
        ServerAsset asset = convertToEntity(request);
        processPassword(asset, request.getPassword(), request.getPrivateKey());
        
        assetMapper.update(asset);
        
        return ApiResponse.ok();
    }
    
    @Operation(summary = "删除服务器", description = "根据ID删除服务器资产")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @Parameter(description = "服务器ID") @PathVariable Long id) {
        log.info("删除服务器: id={}", id);
        assetMapper.deleteById(id);
        return ApiResponse.ok();
    }
    
    @Operation(summary = "测试SSH连接", description = "测试服务器的SSH连接状态")
    @PostMapping("/{id}/test")
    public ApiResponse<Map<String, Object>> testConnection(
            @Parameter(description = "服务器ID") @PathVariable Long id) {
        
        ServerAsset asset = assetMapper.findById(id);
        if (asset == null) {
            throw new BusinessException(ErrorCode.SERVER_NOT_FOUND);
        }
        
        // 解密密码
        if (asset.getPasswordEncrypted() != null) {
            asset.setPassword(encryptionService.decrypt(asset.getPasswordEncrypted()));
        }
        
        boolean connected = sshExecutorService.testConnection(asset);
        
        // 更新状态
        asset.setLastCheckTime(DateTimeUtils.now());
        asset.setLastCheckStatus(connected ? "ONLINE" : "OFFLINE");
        assetMapper.updateStatus(asset);
        
        Map<String, Object> data = new HashMap<>();
        data.put("connected", connected);
        data.put("message", connected ? "连接成功" : "连接失败");
        
        if (connected) {
            data.put("systemInfo", sshExecutorService.getSystemInfo(asset));
        }
        
        if (connected) {
            return ApiResponse.ok(data);
        } else {
            return ApiResponse.fail(ErrorCode.SERVER_CONNECTION_FAILED, "连接失败");
        }
    }
    
    @Operation(summary = "执行脚本", description = "在服务器上执行指定脚本")
    @PostMapping("/{id}/execute")
    public ApiResponse<String> executeScript(
            @Parameter(description = "服务器ID") @PathVariable Long id, 
            @RequestBody Map<String, String> params) {
        
        ServerAsset asset = assetMapper.findById(id);
        if (asset == null) {
            throw new BusinessException(ErrorCode.SERVER_NOT_FOUND);
        }
        
        String script = params.get("script");
        if (script == null || script.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "脚本不能为空");
        }
        
        // 解密密码
        if (asset.getPasswordEncrypted() != null) {
            asset.setPassword(encryptionService.decrypt(asset.getPasswordEncrypted()));
        }
        
        String output = sshExecutorService.executeScript(asset, script);
        return ApiResponse.ok(output);
    }
    
    // ==================== 私有辅助方法 ====================
    
    /**
     * 将请求DTO转换为实体
     */
    private ServerAsset convertToEntity(ServerAssetRequest request) {
        ServerAsset asset = new ServerAsset();
        asset.setId(request.getId());
        asset.setName(request.getName());
        asset.setIp(request.getIp());
        asset.setPort(request.getPort());
        asset.setUsername(request.getUsername());
        asset.setAuthType(request.getAuthType());
        asset.setGroupId(request.getGroupId());
        asset.setTags(request.getTags());
        return asset;
    }
    
    /**
     * 处理密码加密
     */
    private void processPassword(ServerAsset asset, String password, String privateKey) {
        if (password != null && !password.isEmpty()) {
            String plainPassword = transmitEncryptionService.decrypt(password);
            asset.setPasswordEncrypted(encryptionService.encrypt(plainPassword));
        }
        if (privateKey != null && !privateKey.isEmpty()) {
            String plainKey = transmitEncryptionService.decrypt(privateKey);
            asset.setPrivateKeyEncrypted(encryptionService.encrypt(plainKey));
        }
    }
}

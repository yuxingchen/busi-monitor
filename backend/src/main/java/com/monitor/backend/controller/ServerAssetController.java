package com.monitor.backend.controller;

import com.monitor.backend.entity.ServerAsset;
import com.monitor.backend.mapper.ServerAssetMapper;
import com.monitor.backend.service.EncryptionService;
import com.monitor.backend.service.TransmitEncryptionService;
import com.monitor.backend.service.SshExecutorService;
import com.monitor.backend.service.IpRangeParser;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/server-asset")
public class ServerAssetController {
    
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
    
    @GetMapping
    public List<ServerAsset> getAll() {
        return assetMapper.findAll();
    }
    
    @GetMapping("/{id}")
    public ServerAsset getById(@PathVariable Long id) {
        return assetMapper.findById(id);
    }
    
    @GetMapping("/group/{groupId}")
    public List<ServerAsset> getByGroup(@PathVariable Long groupId) {
        return assetMapper.findByGroupId(groupId);
    }
    
    @PostMapping
    public Map<String, Object> add(@RequestBody ServerAsset asset) {
        Map<String, Object> result = new HashMap<>();
        
        // 解密前端传输的加密密码后再加密存储
        if (asset.getPassword() != null && !asset.getPassword().isEmpty()) {
            String plainPassword = transmitEncryptionService.decrypt(asset.getPassword());
            asset.setPasswordEncrypted(encryptionService.encrypt(plainPassword));
        }
        if (asset.getPrivateKey() != null && !asset.getPrivateKey().isEmpty()) {
            String plainKey = transmitEncryptionService.decrypt(asset.getPrivateKey());
            asset.setPrivateKeyEncrypted(encryptionService.encrypt(plainKey));
        }
        
        asset.setIsActive(1);
        assetMapper.insert(asset);
        
        result.put("status", "success");
        result.put("id", asset.getId());
        return result;
    }
    
    /**
     * 批量添加（支持IP段）
     */
    @PostMapping("/batch")
    public Map<String, Object> batchAdd(@RequestBody Map<String, Object> params) {
        String ipInput = (String) params.get("ipInput");
        String name = (String) params.get("name");
        String username = (String) params.get("username");
        String password = (String) params.get("password");
        Integer port = params.get("port") != null ? (Integer) params.get("port") : 22;
        Long groupId = params.get("groupId") != null ? Long.valueOf(params.get("groupId").toString()) : null;
        
        List<String> ips = ipRangeParser.parse(ipInput);
        // 解密前端传输的加密密码后再加密存储
        String encryptedPassword = null;
        if (password != null && !password.isEmpty()) {
            String plainPassword = transmitEncryptionService.decrypt(password);
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
                asset.setName(name != null ? name : ip);
                asset.setIp(ip);
                asset.setPort(port);
                asset.setUsername(username);
                asset.setAuthType("PASSWORD");
                asset.setPasswordEncrypted(encryptedPassword);
                asset.setGroupId(groupId);
                asset.setIsActive(1);
                
                assetMapper.insert(asset);
                successCount++;
            } catch (Exception e) {
                failedIps.add(ip);
            }
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("total", ips.size());
        result.put("successCount", successCount);
        result.put("failedIps", failedIps);
        return result;
    }
    
    @PutMapping
    public String update(@RequestBody ServerAsset asset) {
        // 如果密码变更则解密后重新加密存储
        if (asset.getPassword() != null && !asset.getPassword().isEmpty()) {
            String plainPassword = transmitEncryptionService.decrypt(asset.getPassword());
            asset.setPasswordEncrypted(encryptionService.encrypt(plainPassword));
        }
        if (asset.getPrivateKey() != null && !asset.getPrivateKey().isEmpty()) {
            String plainKey = transmitEncryptionService.decrypt(asset.getPrivateKey());
            asset.setPrivateKeyEncrypted(encryptionService.encrypt(plainKey));
        }
        
        assetMapper.update(asset);
        return "success";
    }
    
    @DeleteMapping("/{id}")
    public String delete(@PathVariable Long id) {
        assetMapper.deleteById(id);
        return "success";
    }
    
    /**
     * 测试SSH连接
     */
    @PostMapping("/{id}/test")
    public Map<String, Object> testConnection(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        ServerAsset asset = assetMapper.findById(id);
        if (asset == null) {
            result.put("status", "error");
            result.put("message", "服务器不存在");
            return result;
        }
        
        // 解密密码
        if (asset.getPasswordEncrypted() != null) {
            asset.setPassword(encryptionService.decrypt(asset.getPasswordEncrypted()));
        }
        
        boolean connected = sshExecutorService.testConnection(asset);
        
        // 更新状态
        asset.setLastCheckTime(LocalDateTime.now());
        asset.setLastCheckStatus(connected ? "ONLINE" : "OFFLINE");
        assetMapper.updateStatus(asset);
        
        result.put("status", connected ? "success" : "error");
        result.put("connected", connected);
        result.put("message", connected ? "连接成功" : "连接失败");
        
        if (connected) {
            result.put("systemInfo", sshExecutorService.getSystemInfo(asset));
        }
        
        return result;
    }
    
    /**
     * 执行脚本
     */
    @PostMapping("/{id}/execute")
    public Map<String, Object> executeScript(@PathVariable Long id, @RequestBody Map<String, String> params) {
        Map<String, Object> result = new HashMap<>();
        ServerAsset asset = assetMapper.findById(id);
        if (asset == null) {
            result.put("status", "error");
            result.put("message", "服务器不存在");
            return result;
        }
        
        String script = params.get("script");
        if (script == null || script.isEmpty()) {
            result.put("status", "error");
            result.put("message", "脚本不能为空");
            return result;
        }
        
        try {
            String output = sshExecutorService.executeScript(asset, script);
            result.put("status", "success");
            result.put("output", output);
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        
        return result;
    }
}

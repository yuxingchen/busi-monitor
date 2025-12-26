package com.monitor.backend.controller;

import com.monitor.backend.entity.MonitorDataSource;
import com.monitor.backend.mapper.MonitorDataSourceMapper;
import com.monitor.backend.service.DataSourceManager;
import com.monitor.backend.service.EncryptionService;
import com.monitor.backend.service.TransmitEncryptionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/datasource")
@CrossOrigin // For dev
public class DataSourceController {

    private final MonitorDataSourceMapper dataSourceMapper;
    private final DataSourceManager dataSourceManager;
    private final EncryptionService encryptionService;
    private final TransmitEncryptionService transmitEncryptionService;

    public DataSourceController(MonitorDataSourceMapper dataSourceMapper, 
                                DataSourceManager dataSourceManager,
                                EncryptionService encryptionService,
                                TransmitEncryptionService transmitEncryptionService) {
        this.dataSourceMapper = dataSourceMapper;
        this.dataSourceManager = dataSourceManager;
        this.encryptionService = encryptionService;
        this.transmitEncryptionService = transmitEncryptionService;
    }

    @GetMapping
    public List<MonitorDataSource> list() {
        List<MonitorDataSource> list = dataSourceMapper.findAll();
        // 不返回加密密码
        list.forEach(ds -> ds.setPasswordEncrypted(null));
        return list;
    }

    @PostMapping
    public String add(@RequestBody MonitorDataSource dataSource) {
        // 解密前端传输的加密密码后再加密存储
        if (dataSource.getPassword() != null && !dataSource.getPassword().isEmpty()) {
            String plainPassword = transmitEncryptionService.decrypt(dataSource.getPassword());
            dataSource.setPasswordEncrypted(encryptionService.encrypt(plainPassword));
        }
        dataSourceMapper.insert(dataSource);
        return "success";
    }

    @PutMapping
    public String update(@RequestBody MonitorDataSource dataSource) {
        // 如果密码变更则重新加密
        if (dataSource.getPassword() != null && !dataSource.getPassword().isEmpty()) {
            // 解密前端传输的加密密码后再加密存储
            String plainPassword = transmitEncryptionService.decrypt(dataSource.getPassword());
            dataSource.setPasswordEncrypted(encryptionService.encrypt(plainPassword));
        } else {
            // 保留原密码
            MonitorDataSource existing = dataSourceMapper.findById(dataSource.getId());
            if (existing != null) {
                dataSource.setPasswordEncrypted(existing.getPasswordEncrypted());
            }
        }
        dataSourceMapper.update(dataSource);
        dataSourceManager.removeDataSource(dataSource.getId()); // Invalidate cache
        return "success";
    }

    @DeleteMapping("/{id}")
    public String delete(@PathVariable Long id) {
        dataSourceMapper.deleteById(id);
        dataSourceManager.removeDataSource(id);
        return "success";
    }

    /**
     * 测试数据源连接
     */
    @PostMapping("/{id}/test")
    public java.util.Map<String, Object> testConnection(@PathVariable Long id) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        try {
            // 获取数据源配置
            MonitorDataSource config = dataSourceMapper.findById(id);
            if (config == null) {
                result.put("success", false);
                result.put("message", "数据源不存在");
                return result;
            }

            // 尝试建立连接
            javax.sql.DataSource ds = dataSourceManager.getDataSource(id);
            try (java.sql.Connection conn = ds.getConnection()) {
                // 简单查询测试
                try (java.sql.Statement stmt = conn.createStatement()) {
                    stmt.execute("SELECT 1");
                }
                result.put("success", true);
                result.put("message", "连接成功");
                result.put("dbProductName", conn.getMetaData().getDatabaseProductName());
                result.put("dbVersion", conn.getMetaData().getDatabaseProductVersion());
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "连接失败: " + e.getMessage());
        }
        return result;
    }
}


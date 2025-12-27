package com.monitor.backend.controller;

import com.monitor.backend.common.ApiResponse;
import com.monitor.backend.entity.MonitorDataSource;
import com.monitor.backend.exception.BusinessException;
import com.monitor.backend.exception.ErrorCode;
import com.monitor.backend.mapper.MonitorDataSourceMapper;
import com.monitor.backend.service.DataSourceManager;
import com.monitor.backend.service.EncryptionService;
import com.monitor.backend.service.TransmitEncryptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据源管理控制器
 *
 * @author monitor-system
 */
@Tag(name = "数据源管理", description = "数据源配置的增删改查和连接测试")
@RestController
@RequestMapping("/api/datasource")
public class DataSourceController {

    private static final Logger log = LoggerFactory.getLogger(DataSourceController.class);

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

    @Operation(summary = "获取所有数据源", description = "查询所有数据源配置（密码不返回）")
    @GetMapping
    public ApiResponse<List<MonitorDataSource>> list() {
        List<MonitorDataSource> list = dataSourceMapper.findAll();
        // 不返回加密密码
        list.forEach(ds -> ds.setPasswordEncrypted(null));
        return ApiResponse.ok(list);
    }

    @Operation(summary = "添加数据源")
    @PostMapping
    public ApiResponse<Void> add(@RequestBody MonitorDataSource dataSource) {
        log.info("添加数据源: name={}", dataSource.getName());
        
        // 解密前端传输的加密密码后再加密存储
        if (dataSource.getPassword() != null && !dataSource.getPassword().isEmpty()) {
            String plainPassword = transmitEncryptionService.decrypt(dataSource.getPassword());
            dataSource.setPasswordEncrypted(encryptionService.encrypt(plainPassword));
        }
        dataSourceMapper.insert(dataSource);
        return ApiResponse.ok("添加成功", null);
    }

    @Operation(summary = "更新数据源")
    @PutMapping
    public ApiResponse<Void> update(@RequestBody MonitorDataSource dataSource) {
        log.info("更新数据源: id={}", dataSource.getId());
        
        // 如果密码变更则重新加密
        if (dataSource.getPassword() != null && !dataSource.getPassword().isEmpty()) {
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
        dataSourceManager.removeDataSource(dataSource.getId()); // 清除缓存
        return ApiResponse.ok("更新成功", null);
    }

    @Operation(summary = "删除数据源")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @Parameter(description = "数据源ID") @PathVariable Long id) {
        log.info("删除数据源: id={}", id);
        dataSourceMapper.deleteById(id);
        dataSourceManager.removeDataSource(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "测试数据源连接", description = "测试数据源能否正常连接")
    @PostMapping("/{id}/test")
    public ApiResponse<Map<String, Object>> testConnection(
            @Parameter(description = "数据源ID") @PathVariable Long id) {
        
        MonitorDataSource config = dataSourceMapper.findById(id);
        if (config == null) {
            throw new BusinessException(ErrorCode.DATASOURCE_NOT_FOUND);
        }

        Map<String, Object> data = new HashMap<>();
        try {
            DataSource ds = dataSourceManager.getDataSource(id);
            try (Connection conn = ds.getConnection()) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("SELECT 1");
                }
                data.put("dbProductName", conn.getMetaData().getDatabaseProductName());
                data.put("dbVersion", conn.getMetaData().getDatabaseProductVersion());
            }
            return ApiResponse.ok("连接成功", data);
        } catch (Exception e) {
            log.warn("数据源连接测试失败: id={}, error={}", id, e.getMessage());
            return ApiResponse.fail(ErrorCode.DATASOURCE_CONNECTION_FAILED, "连接失败: " + e.getMessage());
        }
    }
}

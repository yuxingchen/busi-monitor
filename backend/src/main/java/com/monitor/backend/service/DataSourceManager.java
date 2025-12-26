package com.monitor.backend.service;

import com.monitor.backend.entity.MonitorDataSource;
import com.monitor.backend.mapper.MonitorDataSourceMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DataSourceManager {

    private final MonitorDataSourceMapper dataSourceMapper;
    private final EncryptionService encryptionService;
    private final Map<Long, HikariDataSource> dataSourceCache = new ConcurrentHashMap<>();

    public DataSourceManager(MonitorDataSourceMapper dataSourceMapper, EncryptionService encryptionService) {
        this.dataSourceMapper = dataSourceMapper;
        this.encryptionService = encryptionService;
    }

    public DataSource getDataSource(Long id) {
        if (dataSourceCache.containsKey(id)) {
            return dataSourceCache.get(id);
        }
        return createAndCacheDataSource(id);
    }

    private synchronized DataSource createAndCacheDataSource(Long id) {
        if (dataSourceCache.containsKey(id)) {
            return dataSourceCache.get(id);
        }
        MonitorDataSource config = dataSourceMapper.findById(id);
        if (config == null) {
            throw new RuntimeException("DataSource config not found: " + id);
        }

        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(config.getUrl());
        ds.setUsername(config.getUsername());
        
        // 解密密码
        String password = config.getPasswordEncrypted();
        if (password != null && !password.isEmpty()) {
            password = encryptionService.decrypt(password);
        }
        ds.setPassword(password);
        
        ds.setDriverClassName(config.getDriverClassName());
        ds.setMaximumPoolSize(5); // Small pool for monitoring
        
        dataSourceCache.put(id, ds);
        return ds;
    }

    public void removeDataSource(Long id) {
        HikariDataSource ds = dataSourceCache.remove(id);
        if (ds != null) {
            ds.close();
        }
    }
}


package com.monitor.backend.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 临时数据库表缓存策略
 * <p>
 * 适用于复杂关联、SQL聚合的场景。
 * 支持索引、强一致性，任务完成后自动清理。
 * </p>
 */
@Component
public class TempTableCacheStrategy implements CacheStrategy {

    private static final Logger log = LoggerFactory.getLogger(TempTableCacheStrategy.class);
    private static final String TABLE_PREFIX = "batch_cache_";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public Type getType() {
        return Type.TEMP_TABLE;
    }

    @Override
    public void write(String cacheKey, List<Map<String, Object>> data) {
        if (data == null || data.isEmpty()) {
            return;
        }
        try {
            String tableName = getTableName(cacheKey);
            
            // 获取列名
            Set<String> columns = data.get(0).keySet();
            
            // 创建表（如果不存在）
            createTableIfNotExists(tableName, columns);
            
            // 批量插入数据
            batchInsert(tableName, columns, data);
            
            log.info("临时表缓存写入成功: table={}, count={}", tableName, data.size());
        } catch (Exception e) {
            log.error("临时表缓存写入失败: key={}", cacheKey, e);
            throw new RuntimeException("临时表缓存写入失败", e);
        }
    }

    @Override
    public List<Map<String, Object>> read(String cacheKey) {
        try {
            String tableName = getTableName(cacheKey);
            if (!tableExists(tableName)) {
                return Collections.emptyList();
            }
            String sql = "SELECT * FROM " + tableName;
            return jdbcTemplate.queryForList(sql);
        } catch (Exception e) {
            log.error("临时表缓存读取失败: key={}", cacheKey, e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<Map<String, Object>> readBatch(String cacheKey, int offset, int limit) {
        try {
            String tableName = getTableName(cacheKey);
            if (!tableExists(tableName)) {
                return Collections.emptyList();
            }
            String sql = "SELECT * FROM " + tableName + " LIMIT ? OFFSET ?";
            return jdbcTemplate.queryForList(sql, limit, offset);
        } catch (Exception e) {
            log.error("临时表缓存分批读取失败: key={}", cacheKey, e);
            return Collections.emptyList();
        }
    }

    @Override
    public long count(String cacheKey) {
        try {
            String tableName = getTableName(cacheKey);
            if (!tableExists(tableName)) {
                return 0;
            }
            String sql = "SELECT COUNT(*) FROM " + tableName;
            Long count = jdbcTemplate.queryForObject(sql, Long.class);
            return count != null ? count : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public void clear(String cacheKey) {
        try {
            String tableName = getTableName(cacheKey);
            if (tableExists(tableName)) {
                jdbcTemplate.execute("DROP TABLE IF EXISTS " + tableName);
                log.info("临时表缓存清理成功: table={}", tableName);
            }
        } catch (Exception e) {
            log.error("临时表缓存清理失败: key={}", cacheKey, e);
        }
    }

    @Override
    public boolean exists(String cacheKey) {
        return tableExists(getTableName(cacheKey));
    }

    private String getTableName(String cacheKey) {
        return TABLE_PREFIX + cacheKey.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
    }

    private boolean tableExists(String tableName) {
        try {
            String sql = "SELECT 1 FROM " + tableName + " LIMIT 1";
            jdbcTemplate.queryForObject(sql, Integer.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void createTableIfNotExists(String tableName, Set<String> columns) {
        // 先删除旧表
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + tableName);
        
        // 构建建表语句（所有字段使用TEXT类型以兼容任意数据）
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE ").append(tableName).append(" (");
        sql.append("_id BIGINT AUTO_INCREMENT PRIMARY KEY, ");
        sql.append(columns.stream()
                .map(col -> "`" + col + "` TEXT")
                .collect(Collectors.joining(", ")));
        sql.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        
        jdbcTemplate.execute(sql.toString());
    }

    private void batchInsert(String tableName, Set<String> columns, List<Map<String, Object>> data) {
        if (data.isEmpty()) {
            return;
        }
        
        String columnList = columns.stream()
                .map(col -> "`" + col + "`")
                .collect(Collectors.joining(", "));
        String placeholders = columns.stream()
                .map(c -> "?")
                .collect(Collectors.joining(", "));
        
        String sql = "INSERT INTO " + tableName + " (" + columnList + ") VALUES (" + placeholders + ")";
        
        List<Object[]> batchArgs = data.stream()
                .map(row -> columns.stream()
                        .map(col -> row.get(col) != null ? row.get(col).toString() : null)
                        .toArray())
                .collect(Collectors.toList());
        
        jdbcTemplate.batchUpdate(sql, batchArgs);
    }
}

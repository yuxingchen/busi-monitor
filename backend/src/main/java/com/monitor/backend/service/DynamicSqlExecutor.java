package com.monitor.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

@Service
public class DynamicSqlExecutor {

    private static final Logger logger = LoggerFactory.getLogger(DynamicSqlExecutor.class);

    private final DataSourceManager dataSourceManager;
    private final SqlPlaceholderService placeholderService;
    private final JdbcTemplate localJdbcTemplate;  // 本地监控库

    public DynamicSqlExecutor(DataSourceManager dataSourceManager, SqlPlaceholderService placeholderService, 
                              JdbcTemplate jdbcTemplate) {
        this.dataSourceManager = dataSourceManager;
        this.placeholderService = placeholderService;
        this.localJdbcTemplate = jdbcTemplate;
    }

    /**
     * 执行标量查询（无占位符替换）
     */
    public Double executeScalar(Long datasourceId, String sql) {
        return executeScalar(datasourceId, sql, null);
    }

    /**
     * 执行标量查询（带占位符替换）
     */
    public Double executeScalar(Long datasourceId, String sql, Long taskId) {
        JdbcTemplate jdbcTemplate = getJdbcTemplate(datasourceId);
        
        // 替换占位符
        String resolvedSql = placeholderService.resolvePlaceholders(sql, taskId);
        logger.info("Executing scalar query: {}", resolvedSql);
        return jdbcTemplate.queryForObject(resolvedSql, Double.class);
    }

    /**
     * 执行列表查询（无占位符替换）
     */
    public List<Map<String, Object>> executeQuery(Long datasourceId, String sql) {
        return executeQuery(datasourceId, sql, null);
    }

    /**
     * 执行列表查询（带占位符替换）
     */
    public List<Map<String, Object>> executeQuery(Long datasourceId, String sql, Long taskId) {
        JdbcTemplate jdbcTemplate = getJdbcTemplate(datasourceId);

        // 替换占位符
        String resolvedSql = placeholderService.resolvePlaceholders(sql, taskId);
        logger.info("Executing query: {}", resolvedSql);
        return jdbcTemplate.queryForList(resolvedSql);
    }

    /**
     * 执行列表查询（带工作流上下文变量替换）
     */
    public List<Map<String, Object>> executeQueryWithContext(Long datasourceId, String sql, Map<String, Object> context) {
        JdbcTemplate jdbcTemplate = getJdbcTemplate(datasourceId);

        // 替换工作流变量
        String resolvedSql = placeholderService.resolveWorkflowPlaceholders(sql, context);
        logger.info("Executing query with context: {}", resolvedSql);
        return jdbcTemplate.queryForList(resolvedSql);
    }

    /**
     * 获取 JdbcTemplate
     * datasourceId = null 或 0 时使用本地监控库
     */
    private JdbcTemplate getJdbcTemplate(Long datasourceId) {
        if (datasourceId == null || datasourceId == 0) {
            return localJdbcTemplate;
        }
        DataSource ds = dataSourceManager.getDataSource(datasourceId);
        return new JdbcTemplate(ds);
    }
}


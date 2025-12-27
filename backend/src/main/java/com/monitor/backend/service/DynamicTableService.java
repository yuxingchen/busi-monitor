package com.monitor.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitor.backend.constant.SystemFields;
import com.monitor.backend.constant.WorkflowStepType;
import com.monitor.backend.entity.MonitorTask;
import com.monitor.backend.entity.Workflow;
import com.monitor.backend.mapper.MonitorTaskMapper;
import com.monitor.backend.mapper.WorkflowMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DynamicTableService {

    private static final Logger logger = LoggerFactory.getLogger(DynamicTableService.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final JdbcTemplate jdbcTemplate;
    private final MonitorTaskMapper taskMapper;
    private final WorkflowMapper workflowMapper;
    private final ObjectMapper objectMapper;
    private final DataSourceManager dataSourceManager;
    private final SqlPlaceholderService sqlPlaceholderService;

    public DynamicTableService(JdbcTemplate jdbcTemplate,
                               MonitorTaskMapper taskMapper,
                               WorkflowMapper workflowMapper,
                               ObjectMapper objectMapper,
                               DataSourceManager dataSourceManager,
                               SqlPlaceholderService sqlPlaceholderService) {
        this.jdbcTemplate = jdbcTemplate;
        this.taskMapper = taskMapper;
        this.workflowMapper = workflowMapper;
        this.objectMapper = objectMapper;
        this.dataSourceManager = dataSourceManager;
        this.sqlPlaceholderService = sqlPlaceholderService;
    }


    private String inferSqlType(Object value) {
        if (value == null)
            return "VARCHAR(255)"; // Fallback
        if (value instanceof Integer)
            return "INT";
        if (value instanceof Long)
            return "BIGINT";
        if (value instanceof Double || value instanceof Float)
            return "DOUBLE";
        if (value instanceof Boolean)
            return "TINYINT(1)";
        if (value instanceof Date || value instanceof LocalDateTime)
            return "DATETIME";
        if (value instanceof String && ((String) value).length() > 255)
            return "TEXT";
        return "VARCHAR(255)";
    }


    /**
     * 统一透视表数据查询入口
     * 根据chartConfig中的sourceType自动分发到SQL任务或工作流
     *
     * @param taskId       任务ID
     * @param requestLimit 请求的limit（可选，null表示使用配置或不限制）
     * @return 透视表数据
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> queryPivotData(Long taskId, Integer requestLimit) {
        MonitorTask task = taskMapper.findById(taskId);
        if (task == null) {
            logger.warn("任务不存在: taskId={}", taskId);
            return List.of();
        }

        try {
            // 1. 解析chartConfig
            JsonNode cfg = parseChartConfig(task.getChartConfig());
            String sourceType = cfg.path("sourceType").asText(WorkflowStepType.SQL.getCode());
            Long workflowId = cfg.path("workflowId").asLong(0);
            String outputTable = cfg.path("outputTable").asText(null);
            int configLimit = cfg.path("queryLimit").asInt(0);

            // 2. 确定limit（请求 > 配置 > 无限制）
            Integer effectiveLimit = (requestLimit != null) ? requestLimit
                    : (configLimit > 0) ? configLimit : null;

            // 3. 工作流模式：直接查询结果表（工作流始终有输出表，跳过is_store_data检查）
            if ("WORKFLOW".equals(sourceType) && workflowId > 0) {
                String tableName = resolveTableName(sourceType, workflowId, outputTable, taskId);
                if (tableExists(tableName)) {
                    logger.debug("工作流任务{}从结果表{}查询数据", taskId, tableName);
                    return queryPivotFromTable(tableName, effectiveLimit);
                } else {
                    logger.warn("工作流{}的结果表{}不存在，请先执行工作流", workflowId, tableName);
                    return List.of();
                }
            }

            // 4. SQL模式：检查is_store_data决定是否实时执行
            Integer isStoreData = task.getIsStoreData();
            if (isStoreData == null || isStoreData == 0) {
                // 非存储模式：直接执行任务SQL（可得到别名字段如concat结果）
                logger.debug("任务{}为非存储模式，实时执行SQL", taskId);
                return executeTaskSqlRealtime(task, effectiveLimit);
            }

            // 5. SQL存储模式：优先从表查询
            String tableName = resolveTableName(sourceType, workflowId, outputTable, taskId);
            if (tableExists(tableName)) {
                return queryPivotFromTable(tableName, effectiveLimit);
            } else {
                logger.info("表{}不存在，降级为实时执行", tableName);
                return executeTaskSqlRealtime(task, effectiveLimit);
            }
        } catch (Exception e) {
            logger.error("查询透视数据失败: taskId={}", taskId, e);
            return List.of();
        }
    }

    /**
     * 解析chartConfig JSON
     */
    private JsonNode parseChartConfig(String chartConfig) {
        if (chartConfig == null || chartConfig.isEmpty()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(chartConfig);
        } catch (Exception e) {
            logger.debug("解析chartConfig失败: {}", e.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    /**
     * 解析表名（根据sourceType）
     */
    private String resolveTableName(String sourceType, Long workflowId, String outputTable, Long taskId) {
        if ("WORKFLOW".equals(sourceType) && workflowId > 0) {
            Workflow wf = workflowMapper.findById(workflowId);
            if (wf != null && wf.getOutputTable() != null && !wf.getOutputTable().isEmpty()) {
                return wf.getOutputTable();
            }
            return "monitor_wf_result_" + workflowId;
        } else {
            if (outputTable != null && !outputTable.trim().isEmpty()) {
                return outputTable.trim();
            }
            return "monitor_data_task_" + taskId;
        }
    }

    /**
     * 检查表是否存在
     */
    private boolean tableExists(String tableName) {
        try {
            String sql = "SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql, tableName);
            return !result.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从表查询透视数据
     */
    private List<Map<String, Object>> queryPivotFromTable(String tableName, Integer limit) {
        String sql = "SELECT * FROM " + tableName + " ORDER BY execution_time DESC";
        if (limit != null && limit > 0) {
            sql += " LIMIT " + limit;
        }

        List<Map<String, Object>> rawData = jdbcTemplate.queryForList(sql);
        return formatPivotData(rawData);
    }

    /**
     * 格式化透视数据（过滤系统字段、格式化时间）
     */
    private List<Map<String, Object>> formatPivotData(List<Map<String, Object>> rawData) {
        return rawData.stream().map(row -> {
            Map<String, Object> filtered = new LinkedHashMap<>();
            row.forEach((key, value) -> {
                if (key.equals(SystemFields.EXECUTION_TIME) && value != null) {
                    filtered.put(SystemFields.EXECUTION_TIME, formatDateTime(value));
                } else if (!SystemFields.isSystemField(key)) {
                    filtered.put(key, value);
                }
            });
            return filtered;
        }).collect(Collectors.toList());
    }

    /**
     * 格式化日期时间
     */
    private String formatDateTime(Object value) {
        if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).format(TIME_FORMATTER);
        } else if (value instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) value).toLocalDateTime().format(TIME_FORMATTER);
        }
        return value.toString();
    }

    /**
     * 实时执行任务SQL（降级方案）
     */
    private List<Map<String, Object>> executeTaskSqlRealtime(MonitorTask task, Integer limit) {
        if (task.getSqlScript() == null || task.getSqlScript().isEmpty()) {
            return List.of();
        }

        try {
            DataSource ds = dataSourceManager.getDataSource(task.getDatasourceId());
            JdbcTemplate targetJdbc = new JdbcTemplate(ds);

            String processedSql = sqlPlaceholderService.resolvePlaceholders(task.getSqlScript(), task.getId());
            String sql = addLimitClause(processedSql, limit);

            List<Map<String, Object>> result = targetJdbc.queryForList(sql);
            logger.info("实时执行SQL获取{}条数据: taskId={}", result.size(), task.getId());

            // 只有当结果中没有execution_time字段时，才添加当前时间
            String now = LocalDateTime.now().format(TIME_FORMATTER);
            result.forEach(row -> {
                if (!row.containsKey(SystemFields.EXECUTION_TIME) || row.get(SystemFields.EXECUTION_TIME) == null) {
                    row.put(SystemFields.EXECUTION_TIME, now);
                } else {
                    // 格式化已有的execution_time
                    Object existingTime = row.get(SystemFields.EXECUTION_TIME);
                    row.put(SystemFields.EXECUTION_TIME, formatDateTime(existingTime));
                }
            });

            return result;
        } catch (Exception e) {
            logger.error("实时执行SQL失败: taskId={}", task.getId(), e);
            return List.of();
        }
    }

    /**
     * 为SQL添加LIMIT子句
     */
    private String addLimitClause(String sql, Integer limit) {
        if (limit == null || limit <= 0) {
            return sql;
        }
        String sqlUpper = sql.toUpperCase().trim();
        if (sqlUpper.contains("LIMIT")) {
            return sql;
        }
        return sql + " LIMIT " + limit;
    }

    /**
     * 根据 batch_id (即 record.id) 查询动态表数据，并过滤系统字段
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> queryByBatchId(Long taskId, String batchId) {
        String tableName = "monitor_data_task_" + taskId;
        try {
            String sql = "SELECT * FROM " + tableName + " WHERE batch_id = ? ORDER BY id";
            List<Map<String, Object>> rawData = jdbcTemplate.queryForList(sql, batchId);

            // 过滤掉系统字段，只保留业务数据
            return rawData.stream().map(row -> {
                Map<String, Object> filtered = new java.util.LinkedHashMap<>();
                row.forEach((key, value) -> {
                    if (!SystemFields.isSystemField(key)) {
                        filtered.put(key, value);
                    }
                });
                return filtered;
            }).collect(java.util.stream.Collectors.toList());
        } catch (Exception e) {
            logger.warn("Failed to query by batch_id from {}: {}", tableName, e.getMessage());
            return List.of();
        }
    }

    /**
     * 获取动态表的字段列表（排除系统字段）
     */
    @Transactional(readOnly = true)
    public List<String> getTableColumns(Long taskId) {
        String tableName = "monitor_data_task_" + taskId;
        try {
            String sql = "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? " +
                    "AND COLUMN_NAME NOT IN ('id', 'batch_id', 'execution_time') " +
                    "ORDER BY ORDINAL_POSITION";
            return jdbcTemplate.queryForList(sql, String.class, tableName);
        } catch (Exception e) {
            logger.error("Failed to get columns for table: " + tableName, e);
            return List.of();
        }
    }

    /**
     * 按执行时间分组查询聚合数据
     *
     * @param taskId       任务ID
     * @param groupByField 分组字段
     * @param valueField   数值字段
     * @param aggMethod    聚合方式 (SUM/COUNT/AVG)
     * @return 分组聚合结果
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> queryGroupedByTime(Long taskId, String groupByField,
                                                        String valueField, String aggMethod) {
        String tableName = "monitor_data_task_" + taskId;

        // 验证聚合方式
        String agg = switch (aggMethod.toUpperCase()) {
            case "SUM" -> "SUM";
            case "COUNT" -> "COUNT";
            case "AVG" -> "AVG";
            case "MAX" -> "MAX";
            case "MIN" -> "MIN";
            default -> "SUM";
        };

        try {
            // 安全检查：确保字段名不包含特殊字符
            if (isValidColumnName(groupByField) || isValidColumnName(valueField)) {
                logger.error("Invalid column name detected");
                return List.of();
            }

            String sql = String.format(
                    "SELECT execution_time, `%s` AS group_key, %s(`%s`) AS agg_value " +
                            "FROM %s " +
                            "GROUP BY execution_time, `%s` " +
                            "ORDER BY execution_time ASC, `%s` ASC",
                    groupByField, agg, valueField, tableName, groupByField, groupByField);

            return jdbcTemplate.queryForList(sql);
        } catch (Exception e) {
            logger.error("Failed to query grouped data for task: " + taskId, e);
            return List.of();
        }
    }

    /**
     * 验证列名是否合法（防止 SQL 注入）
     */
    private boolean isValidColumnName(String columnName) {
        if (columnName == null || columnName.isEmpty()) {
            return true;
        }
        // 只允许字母、数字、下划线
        return !columnName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$");
    }

    // ==================== 工作流结果表相关方法 ====================

    /**
     * 保存工作流执行结果到指定表
     * 表名格式: monitor_wf_result_{workflowId} 或自定义表名
     *
     * @param tableName   结果表名
     * @param data        结果数据
     * @param executionId 执行批次ID
     */
    @Transactional
    public void saveWorkflowResult(String tableName, List<Map<String, Object>> data, Long executionId) {
        if (data == null || data.isEmpty()) {
            logger.info("No data to save for workflow result table: {}", tableName);
            return;
        }

        // 确保表存在
        ensureWorkflowResultTableExists(tableName, data.get(0));

        // 插入数据
        Map<String, Object> firstRow = data.get(0);
        List<String> keys = firstRow.keySet().stream().collect(Collectors.toList());

        StringJoiner colNames = new StringJoiner(", ");
        colNames.add(SystemFields.EXECUTION_ID);
        colNames.add(SystemFields.EXECUTION_TIME);
        keys.forEach(k -> colNames.add("`" + k + "`"));

        StringJoiner placeHolders = new StringJoiner(", ");
        placeHolders.add("?"); // execution_id
        placeHolders.add("?"); // execution_time
        keys.forEach(k -> placeHolders.add("?"));

        String sql = "INSERT INTO " + tableName + " (" + colNames.toString() + ") VALUES (" + placeHolders.toString() + ")";

        List<Object[]> batchArgs = data.stream().map(row -> {
            Object[] args = new Object[keys.size() + 2];
            args[0] = executionId;
            args[1] = new Date();
            for (int i = 0; i < keys.size(); i++) {
                args[i + 2] = row.get(keys.get(i));
            }
            return args;
        }).collect(Collectors.toList());

        try {
            jdbcTemplate.batchUpdate(sql, batchArgs);
            logger.info("Saved {} rows to workflow result table: {}", data.size(), tableName);
        } catch (Exception e) {
            logger.error("Failed to save workflow result to " + tableName, e);
            throw e;
        }
    }

    /**
     * 确保工作流结果表存在，并确保所有需要的列都存在
     */
    private void ensureWorkflowResultTableExists(String tableName, Map<String, Object> sampleRow) {
        // 1. 创建表（如果不存在）
        StringBuilder createSql = new StringBuilder("CREATE TABLE IF NOT EXISTS " + tableName + " (");
        createSql.append("id BIGINT AUTO_INCREMENT PRIMARY KEY, ");
        createSql.append("execution_id BIGINT COMMENT '执行批次ID', ");
        createSql.append("execution_time DATETIME COMMENT '执行时间', ");

        for (Map.Entry<String, Object> entry : sampleRow.entrySet()) {
            String colName = "`" + entry.getKey() + "`";
            String colType = inferSqlType(entry.getValue());
            createSql.append(colName).append(" ").append(colType).append(", ");
        }

        // 添加索引
        createSql.append("INDEX idx_execution (execution_id)");
        createSql.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流结果表'");

        logger.info("Creating/Ensuring workflow result table: {}", tableName);
        jdbcTemplate.execute(createSql.toString());

        // 2. 检查并添加缺失的列
        try {
            String checkColumnSql = "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
            List<String> existingColumns = jdbcTemplate.queryForList(checkColumnSql, String.class, tableName);

            for (Map.Entry<String, Object> entry : sampleRow.entrySet()) {
                String colName = entry.getKey();
                if (!existingColumns.contains(colName)) {
                    String colType = inferSqlType(entry.getValue());
                    String alterSql = "ALTER TABLE " + tableName + " ADD COLUMN `" + colName + "` " + colType;
                    logger.info("Adding missing column to {}: {}", tableName, colName);
                    jdbcTemplate.execute(alterSql);
                }
            }
        } catch (Exception e) {
            logger.warn("Error checking/adding columns to {}: {}", tableName, e.getMessage());
        }
    }

    /**
     * 查询工作流结果表最新一批数据
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> queryLatestWorkflowResult(String tableName) {
        try {
            // 先获取最新的 execution_id
            String maxIdSql = "SELECT MAX(execution_id) FROM " + tableName;
            Long latestExecutionId = jdbcTemplate.queryForObject(maxIdSql, Long.class);

            if (latestExecutionId == null) {
                return List.of();
            }

            // 查询该批次的所有数据
            String sql = "SELECT * FROM " + tableName + " WHERE execution_id = ? ORDER BY id";
            List<Map<String, Object>> rawData = jdbcTemplate.queryForList(sql, latestExecutionId);

            // 过滤系统字段
            return rawData.stream().map(row -> {
                Map<String, Object> filtered = new java.util.LinkedHashMap<>();
                row.forEach((key, value) -> {
                    if (!key.equals(SystemFields.ID) && !key.equals(SystemFields.EXECUTION_ID)) {
                        filtered.put(key, value);
                    }
                });
                return filtered;
            }).collect(Collectors.toList());
        } catch (Exception e) {
            logger.warn("Failed to query workflow result from {}: {}", tableName, e.getMessage());
            return List.of();
        }
    }

    /**
     * 根据 taskId 和可选的自定义表名返回实际表名
     *
     * @param taskId          任务ID
     * @param customTableName 自定义表名（可选，为空时使用默认名称）
     * @return 实际表名
     */
    public static String getTaskResultTableName(Long taskId, String customTableName) {
        if (customTableName != null && !customTableName.trim().isEmpty()) {
            // 使用自定义表名（移除可能的特殊字符，保证安全）
            return customTableName.trim().replaceAll("[^a-zA-Z0-9_]", "_");
        }
        // 使用默认表名
        return "monitor_data_task_" + taskId;
    }

    /**
     * 确保任务结果表存在（支持自定义表名）
     *
     * @param tableName 表名
     * @param sampleRow 用于推断 schema 的样本行
     */
    @Transactional
    public void ensureTaskTableWithCustomName(String tableName, Map<String, Object> sampleRow) {
        StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS `" + tableName + "` (");
        sql.append("id BIGINT AUTO_INCREMENT PRIMARY KEY, ");
        sql.append("batch_id VARCHAR(50), ");
        sql.append("execution_time DATETIME, ");

        for (Map.Entry<String, Object> entry : sampleRow.entrySet()) {
            String colName = "`" + entry.getKey() + "`";
            String colType = inferSqlType(entry.getValue());
            sql.append(colName).append(" ").append(colType).append(", ");
        }

        // Remove last comma
        sql.setLength(sql.length() - 2);
        sql.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        logger.info("Creating/Ensuring task result table: {}", tableName);
        jdbcTemplate.execute(sql.toString());
    }

    /**
     * 保存数据到指定表名（支持自定义表名）
     *
     * @param tableName 表名
     * @param batchId   批次ID
     * @param data      数据列表
     */
    @Transactional
    public void saveTaskDataToTable(String tableName, String batchId, List<Map<String, Object>> data) {
        if (data == null || data.isEmpty())
            return;

        Map<String, Object> firstRow = data.get(0);
        List<String> keys = firstRow.keySet().stream().toList();

        StringJoiner colNames = new StringJoiner(", ");
        colNames.add(SystemFields.BATCH_ID);
        colNames.add(SystemFields.EXECUTION_TIME);
        keys.forEach(k -> colNames.add("`" + k + "`"));

        StringJoiner placeHolders = new StringJoiner(", ");
        placeHolders.add("?"); // batch_id
        placeHolders.add("?"); // execution_time
        keys.forEach(k -> placeHolders.add("?"));

        String sql = "INSERT INTO `" + tableName + "` (" + colNames.toString() + ") VALUES (" + placeHolders.toString()
                + ")";

        List<Object[]> batchArgs = data.stream().map(row -> {
            Object[] args = new Object[keys.size() + 2];
            args[0] = batchId;
            args[1] = new Date();
            for (int i = 0; i < keys.size(); i++) {
                args[i + 2] = row.get(keys.get(i));
            }
            return args;
        }).collect(Collectors.toList());

        try {
            jdbcTemplate.batchUpdate(sql, batchArgs);
            logger.info("Inserted {} rows into {}", data.size(), tableName);
        } catch (Exception e) {
            logger.error("Failed to insert data into " + tableName, e);
            throw e;
        }
    }

    /**
     * 确保指定字段的索引存在
     *
     * @param tableName   表名
     * @param indexFields 需要创建索引的字段列表
     */
    @Transactional
    public void ensureIndexes(String tableName, List<String> indexFields) {
        if (indexFields == null || indexFields.isEmpty()) {
            return;
        }

        try {
            // 查询已存在的索引
            String checkIndexSql = "SELECT INDEX_NAME, COLUMN_NAME FROM INFORMATION_SCHEMA.STATISTICS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
            List<Map<String, Object>> existingIndexes = jdbcTemplate.queryForList(checkIndexSql, tableName);

            // 提取已有索引的列名
            java.util.Set<String> indexedColumns = existingIndexes.stream()
                    .map(row -> (String) row.get("COLUMN_NAME"))
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());

            // 为缺失索引的字段创建索引
            for (String field : indexFields) {
                String fieldLower = field.toLowerCase();
                if (!indexedColumns.contains(fieldLower)) {
                    String indexName = "idx_" + field.replaceAll("[^a-zA-Z0-9]", "_");
                    String createIndexSql = String.format(
                            "CREATE INDEX `%s` ON `%s` (`%s`)",
                            indexName, tableName, field);
                    logger.info("Creating index on {}.{}", tableName, field);
                    jdbcTemplate.execute(createIndexSql);
                }
            }
        } catch (Exception e) {
            logger.warn("Error creating indexes for table {}: {}", tableName, e.getMessage());
        }
    }

    /**
     * 获取工作流结果表名
     */
    public static String getWorkflowResultTableName(Long workflowId) {
        return "monitor_wf_result_" + workflowId;
    }
}


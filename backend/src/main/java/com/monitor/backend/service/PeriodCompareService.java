package com.monitor.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitor.backend.constant.CompareConstants;
import com.monitor.backend.constant.SystemFields;
import com.monitor.backend.dto.ChartConfigDto;
import com.monitor.backend.dto.CompareConfig;
import com.monitor.backend.dto.CompareResult;
import com.monitor.backend.dto.GroupCompareItem;
import com.monitor.backend.entity.MonitorTask;
import com.monitor.backend.enums.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 同比环比对比服务
 * <p>
 * 统一处理简单对比、聚合对比、分组对比三种模式
 * </p>
 */
@Service
public class PeriodCompareService {
    
    private static final Logger logger = LoggerFactory.getLogger(PeriodCompareService.class);
    
    /** 从 SQL 中提取 FROM 子句表名的正则 */
    private static final Pattern FROM_PATTERN = Pattern.compile(
            "\\bFROM\\s+[`]?([a-zA-Z_][a-zA-Z0-9_]*)[`]?",
            Pattern.CASE_INSENSITIVE);
    
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final DynamicSqlExecutor sqlExecutor;
    private final SqlPlaceholderService placeholderService;
    
    public PeriodCompareService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
            DynamicSqlExecutor sqlExecutor, SqlPlaceholderService placeholderService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.sqlExecutor = sqlExecutor;
        this.placeholderService = placeholderService;
    }
    
    /**
     * 内部对比模式（对应 CompareMode 枚举扩展为支持 AGGREGATED）
     */
    private enum InternalMode {
        SIMPLE,      // 简单对比（单值）
        AGGREGATED,  // 聚合对比（先聚合再对比）
        GROUPED      // 分组对比（逐条匹配）
    }
    
    /**
     * 执行对比（统一入口）
     * <p>
     * 根据任务配置确定历史数据表名：
     * 1. 任务持久化且配置自定义表名 → 使用自定义表名
     * 2. 任务持久化但无自定义表名 → 使用默认 monitor_data_task_{id}
     * 3. 任务未持久化 → 从 SQL 中提取表名
     * </p>
     * 
     * @param task 监控任务（包含 SQL 和配置信息）
     * @param currentResult 当前结果（Number或List）
     * @param config 对比配置
     * @return 对比结果
     */
    public CompareResult compare(MonitorTask task, Object currentResult, CompareConfig config) {
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            return null;
        }
        
        if (task == null) {
            logger.warn("MonitorTask is null, cannot perform period compare");
            return null;
        }
        
        try {
            // 判断是否为持久化任务
            boolean isStoreData = task.getIsStoreData() != null && task.getIsStoreData() == 1;
            
            // 0. 解析历史数据表名（根据任务配置决定）
            String historyTableName = resolveHistoryTableName(task);
            if (isStoreData && (historyTableName == null || historyTableName.isEmpty())) {
                logger.warn("Cannot resolve history table name for task {}", task.getId());
                return null;
            }
            if (isStoreData) {
                logger.info("Task {} using history table: {}", task.getId(), historyTableName);
            }
            
            // 确定时间字段名：持久化任务用 execution_time，非持久化任务可选配置 timeField
            String timeFieldName;
            if (isStoreData) {
                timeFieldName = SystemFields.EXECUTION_TIME;
            } else {
                timeFieldName = config.getTimeField();
                // 非持久化任务不强制 timeField，因为会使用 SQL 占位符替换
            }
            
            // 1. 自动识别对比模式
            InternalMode mode = resolveCompareMode(currentResult, config);
            logger.info("Task {} using compare mode: {}, isStoreData: {}", task.getId(), mode, isStoreData);
            
            // 2. 计算历史时间点
            LocalDateTime historyTime = calculateHistoryTime(config);
            
            // 3. 生成对比周期标签
            String periodLabel = buildPeriodLabel(config);
            
            // 4. 根据模式执行不同策略
            // 非持久化任务需要传递 task 以便执行历史 SQL
            return switch (mode) {
                case SIMPLE -> compareSimple(task, historyTableName, timeFieldName, isStoreData, toDouble(currentResult), config, historyTime, periodLabel);
                case AGGREGATED -> compareAggregated(task, historyTableName, timeFieldName, isStoreData, toList(currentResult), config, historyTime, periodLabel);
                case GROUPED -> compareGrouped(task, historyTableName, timeFieldName, isStoreData, toList(currentResult), config, historyTime, periodLabel);
            };
        } catch (Exception e) {
            logger.error("Period compare failed for task {}: {}", task.getId(), e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 解析历史数据表名
     * <p>
     * 优先级：
     * 1. 任务持久化 && chartConfig.outputTable 不为空 → 使用自定义表名
     * 2. 任务持久化 && isStoreData=1 → 使用默认表名 monitor_data_task_{id}
     * 3. 任务未持久化 → 从 SQL 中提取 FROM 子句的表名
     * </p>
     */
    private String resolveHistoryTableName(MonitorTask task) {
        boolean isStoreData = task.getIsStoreData() != null && task.getIsStoreData() == 1;
        
        // 1. 解析 chartConfig 获取自定义表名
        String customTableName = null;
        if (task.getChartConfig() != null && !task.getChartConfig().isEmpty()) {
            try {
                ChartConfigDto chartConfig = objectMapper.readValue(task.getChartConfig(), ChartConfigDto.class);
                customTableName = chartConfig.getOutputTable();
            } catch (Exception e) {
                logger.debug("Failed to parse chartConfig for task {}: {}", task.getId(), e.getMessage());
            }
        }
        
        // 2. 持久化任务：使用存储表
        if (isStoreData) {
            if (customTableName != null && !customTableName.trim().isEmpty()) {
                logger.info("Task {} uses custom output table: {}", task.getId(), customTableName);
                return customTableName.trim();
            }
            // 默认存储表名
            return "monitor_data_task_" + task.getId();
        }
        
        // 3. 非持久化任务：从 SQL 中提取表名
        String sql = task.getSqlScript();
        if (sql == null || sql.isEmpty()) {
            return null;
        }
        
        Matcher matcher = FROM_PATTERN.matcher(sql);
        if (matcher.find()) {
            String tableName = matcher.group(1);
            logger.info("Task {} (non-persistent) extracted table from SQL: {}", task.getId(), tableName);
            return tableName;
        }
        
        logger.warn("Task {} cannot extract table name from SQL", task.getId());
        return null;
    }
    
    /**
     * 自动识别对比模式
     */
    private InternalMode resolveCompareMode(Object currentResult, CompareConfig config) {
        CompareMode explicitMode = CompareMode.fromCode(config.getCompareMode());
        
        // 用户手动指定
        if (explicitMode == CompareMode.SIMPLE) {
            return InternalMode.SIMPLE;
        }
        if (explicitMode == CompareMode.GROUPED) {
            return InternalMode.GROUPED;
        }
        
        // AUTO模式：自动识别
        if (currentResult instanceof Number) {
            return InternalMode.SIMPLE;
        }
        
        if (currentResult instanceof List<?> list) {
            if (list.isEmpty()) {
                return InternalMode.SIMPLE;
            }
            
            // 有分组字段配置 → 分组对比
            if (config.getGroupByFields() != null && !config.getGroupByFields().isEmpty()) {
                return InternalMode.GROUPED;
            }
            
            // 无分组但有多条数据 → 先聚合再对比
            return InternalMode.AGGREGATED;
        }
        
        return InternalMode.SIMPLE;
    }
    
    /**
     * 简单对比（单值）
     */
    private CompareResult compareSimple(MonitorTask task, String tableName, String timeFieldName, boolean isStoreData,
            Double currentValue, CompareConfig config, LocalDateTime historyTime, String periodLabel) {
        
        // 查询历史值
        Double previousValue = queryHistoryScalarValue(task, tableName, timeFieldName, isStoreData, config.getCompareField(), historyTime);
        
        // 计算变化
        Double changeValue = null;
        Double changeRate = null;
        boolean shouldAlert = false;
        
        if (currentValue != null && previousValue != null) {
            changeValue = currentValue - previousValue;
            changeRate = calculateChangeRate(currentValue, previousValue);
            shouldAlert = checkThreshold(changeRate, changeValue, config);
        } else if (MissingHandleType.fromCode(config.getHandleMissing()).shouldAlert() && previousValue == null) {
            // 历史缺失时告警
            shouldAlert = true;
        }
        
        logger.info("Simple compare: current={}, previous={}, changeRate={}%, shouldAlert={}",
                currentValue, previousValue, changeRate, shouldAlert);
        
        return CompareResult.ofSimple(currentValue, previousValue, changeValue, changeRate, shouldAlert, periodLabel);
    }
    
    /**
     * 聚合对比（先聚合成单值再对比）
     */
    private CompareResult compareAggregated(MonitorTask task, String tableName, String timeFieldName, boolean isStoreData,
            List<Map<String, Object>> currentData, CompareConfig config, LocalDateTime historyTime, String periodLabel) {
        
        // 对当前数据做聚合
        Double currentValue = aggregate(currentData, config.getCompareField(), config.getAggregateMethod());
        
        // 查询历史聚合值
        Double previousValue = queryHistoryAggregatedValue(task, tableName, timeFieldName, isStoreData,
                config.getCompareField(), config.getAggregateMethod(), historyTime);
        
        // 计算变化
        Double changeValue = null;
        Double changeRate = null;
        boolean shouldAlert = false;
        
        if (currentValue != null && previousValue != null) {
            changeValue = currentValue - previousValue;
            changeRate = calculateChangeRate(currentValue, previousValue);
            shouldAlert = checkThreshold(changeRate, changeValue, config);
        }
        
        logger.info("Aggregated compare: current={}, previous={}, changeRate={}%, shouldAlert={}",
                currentValue, previousValue, changeRate, shouldAlert);
        
        return CompareResult.ofSimple(currentValue, previousValue, changeValue, changeRate, shouldAlert, periodLabel);
    }
    
    /**
     * 分组对比（逐条匹配）
     */
    private CompareResult compareGrouped(MonitorTask task, String tableName, String timeFieldName, boolean isStoreData,
            List<Map<String, Object>> currentData, CompareConfig config, LocalDateTime historyTime, String periodLabel) {
        
        List<String> groupByFields = config.getGroupByFields();
        String compareField = config.getCompareField();
        
        MissingHandleType handleNew = MissingHandleType.fromCode(config.getHandleNew());
        MissingHandleType handleMissing = MissingHandleType.fromCode(config.getHandleMissing());
        AlertMode alertMode = AlertMode.fromCode(config.getAlertMode());
        
        // 查询历史数据
        List<Map<String, Object>> historyData = queryHistoryGroupedData(task, tableName, timeFieldName, isStoreData, historyTime);
        
        // 构建历史数据索引
        Map<String, Map<String, Object>> historyIndex = buildGroupIndex(historyData, groupByFields);
        
        // 逐条对比
        List<GroupCompareItem> items = new ArrayList<>();
        int alertCount = 0;
        
        for (Map<String, Object> current : currentData) {
            String groupKey = buildGroupKey(current, groupByFields);
            Map<String, Object> history = historyIndex.remove(groupKey);
            
            Double currentVal = toDouble(current.get(compareField));
            Double previousVal = history != null ? toDouble(history.get(compareField)) : null;
            
            GroupCompareStatus status;
            Double changeRate = null;
            
            if (previousVal == null) {
                // 新增分组
                status = handleNew.shouldAlert() ? GroupCompareStatus.ALERT : GroupCompareStatus.NEW;
                if (handleNew.shouldAlert()) {
                    alertCount++;
                }
            } else {
                changeRate = calculateChangeRate(currentVal, previousVal);
                boolean triggered = checkThreshold(changeRate, currentVal - previousVal, config);
                status = triggered ? GroupCompareStatus.ALERT : GroupCompareStatus.NORMAL;
                if (triggered) alertCount++;
            }
            
            items.add(GroupCompareItem.of(groupKey, currentVal, previousVal, changeRate, status.getCode()));
        }
        
        // 处理消失的分组
        for (Map.Entry<String, Map<String, Object>> entry : historyIndex.entrySet()) {
            GroupCompareStatus status = handleMissing.shouldAlert() 
                    ? GroupCompareStatus.ALERT : GroupCompareStatus.MISSING;
            if (handleMissing.shouldAlert()) {
                alertCount++;
            }
            Double previousVal = toDouble(entry.getValue().get(compareField));
            items.add(GroupCompareItem.of(entry.getKey(), null, previousVal, null, status.getCode()));
        }
        
        // 判断是否触发告警
        boolean shouldAlert;
        if (alertMode.isAll()) {
            shouldAlert = alertCount > 0 && alertCount == items.size();
        } else {
            // ANY模式
            shouldAlert = alertCount >= config.getMinAlertCount();
        }
        
        logger.info("Grouped compare: {} items, {} alerts, shouldAlert={}", 
                items.size(), alertCount, shouldAlert);
        
        return CompareResult.ofGrouped(items, alertCount, shouldAlert, periodLabel);
    }
    
    // ===== 辅助方法 =====
    
    /**
     * 计算历史时间点
     */
    private LocalDateTime calculateHistoryTime(CompareConfig config) {
        LocalDateTime now = LocalDateTime.now();
        int count = config.getPeriodCount();
        CompareType compareType = CompareType.fromCode(config.getCompareType());
        PeriodUnit periodUnit = PeriodUnit.fromCode(config.getPeriodUnit());
        
        // 同比：年
        if (compareType.isYearOverYear()) {
            return now.minusYears(1);
        }
        
        // 环比：按单位
        return periodUnit.subtract(now, count);
    }
    
    /**
     * 生成对比周期标签
     */
    private String buildPeriodLabel(CompareConfig config) {
        CompareType compareType = CompareType.fromCode(config.getCompareType());
        
        if (compareType.isYearOverYear()) {
            return "同比去年";
        }
        
        int count = config.getPeriodCount();
        PeriodUnit periodUnit = PeriodUnit.fromCode(config.getPeriodUnit());
        
        if (count == 1) {
            return "环比昨" + periodUnit.getShortLabel().charAt(0);
        }
        return String.format("环比%d%s前", count, periodUnit.getLabel());
    }
    
    /**
     * 计算变化率
     */
    private Double calculateChangeRate(Double current, Double previous) {
        if (current == null || previous == null || previous == 0) {
            return null;
        }
        return ((current - previous) / Math.abs(previous)) * CompareConstants.RATE_MULTIPLIER;
    }
    
    /**
     * 检查是否触发阈值
     */
    private boolean checkThreshold(Double changeRate, Double changeValue, CompareConfig config) {
        ChangeType changeType = ChangeType.fromCode(config.getChangeType());
        Double valueToCheck = changeType.isRate() ? changeRate : changeValue;
        
        if (valueToCheck == null) {
            return false;
        }
        
        // 使用绝对值
        if (Boolean.TRUE.equals(config.getUseAbsoluteValue())) {
            valueToCheck = Math.abs(valueToCheck);
        }
        
        Double threshold = config.getChangeThreshold();
        String operator = config.getChangeOperator();
        
        return CompareOperator.fromSymbol(operator).compare(valueToCheck, threshold);
    }
    
    /**
     * 聚合计算
     */
    private Double aggregate(List<Map<String, Object>> data, String field, String method) {
        if (data == null || data.isEmpty() || field == null) {
            return null;
        }
        
        AggregateMethod aggMethod = AggregateMethod.fromCode(method);
        if (aggMethod == null) {
            aggMethod = AggregateMethod.SUM;
        }
        
        double sum = 0;
        int count = 0;
        Double max = null;
        Double min = null;
        
        for (Map<String, Object> row : data) {
            Double val = toDouble(row.get(field));
            if (val != null) {
                sum += val;
                count++;
                if (max == null || val > max) max = val;
                if (min == null || val < min) min = val;
            }
        }
        
        if (count == 0) return null;
        
        return switch (aggMethod) {
            case SUM -> sum;
            case COUNT -> (double) count;
            case AVG -> sum / count;
            case MAX -> max;
            case MIN -> min;
        };
    }
    
    /**
     * 构建分组索引
     */
    private Map<String, Map<String, Object>> buildGroupIndex(List<Map<String, Object>> data, List<String> groupByFields) {
        Map<String, Map<String, Object>> index = new LinkedHashMap<>();
        if (data == null) return index;
        
        for (Map<String, Object> row : data) {
            String key = buildGroupKey(row, groupByFields);
            index.put(key, row);
        }
        return index;
    }
    
    /**
     * 构建分组键
     */
    private String buildGroupKey(Map<String, Object> row, List<String> groupByFields) {
        if (groupByFields == null || groupByFields.isEmpty()) {
            return "";
        }
        return groupByFields.stream()
                .map(f -> String.valueOf(row.getOrDefault(f, "")))
                .collect(Collectors.joining(CompareConstants.GROUP_KEY_SEPARATOR));
    }
    
    // ===== 数据查询方法 =====
    
    /**
     * 查询历史单值
     * 持久化任务：从结果表中查询最接近指定时间点的记录
     * 非持久化任务：重新执行任务SQL并替换时间占位符为历史时间
     */
    private Double queryHistoryScalarValue(MonitorTask task, String tableName, String timeFieldName, 
            boolean isStoreData, String field, LocalDateTime historyTime) {
        try {
            if (!isStoreData) {
                // 非持久化任务：使用历史时间占位符重新执行任务SQL
                String originalSql = task.getSqlScript();
                if (originalSql == null || originalSql.isEmpty()) {
                    logger.warn("Task {} has no SQL script for history query", task.getId());
                    return null;
                }
                String historySql = placeholderService.resolvePlaceholdersForHistory(originalSql, historyTime);
                List<Map<String, Object>> historyData = sqlExecutor.executeQuery(task.getDatasourceId(), historySql);
                if (!historyData.isEmpty() && historyData.get(0).get(field) != null) {
                    return toDouble(historyData.get(0).get(field));
                }
                logger.debug("No history scalar value found for task {} at {}", task.getId(), historyTime);
                return null;
            }
            
            // 持久化任务：从结果表查询
            int windowHours = CompareConstants.HISTORY_SEARCH_WINDOW_HOURS;
            
            String sql = """
                SELECT `%s` FROM `%s` 
                WHERE `%s` BETWEEN ? AND ?
                ORDER BY ABS(TIMESTAMPDIFF(SECOND, `%s`, ?)) 
                LIMIT 1
                """.formatted(field, tableName, timeFieldName, timeFieldName);
            
            LocalDateTime start = historyTime.minusHours(windowHours);
            LocalDateTime end = historyTime.plusHours(windowHours);
            
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql, start, end, historyTime);
            
            if (!result.isEmpty() && result.get(0).get(field) != null) {
                return toDouble(result.get(0).get(field));
            }
            logger.debug("No history scalar value found for table {} at {}", tableName, historyTime);
            return null;
        } catch (Exception e) {
            logger.warn("Query history scalar value failed: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 查询历史聚合值
     * 持久化任务：从结果表中查询指定时间点附近的数据并做聚合
     * 非持久化任务：重新执行任务SQL并替换时间占位符为历史时间，然后聚合
     */
    private Double queryHistoryAggregatedValue(MonitorTask task, String tableName, String timeFieldName, 
            boolean isStoreData, String field, String method, LocalDateTime historyTime) {
        try {
            AggregateMethod aggMethod = AggregateMethod.fromCode(method);
            if (aggMethod == null) aggMethod = AggregateMethod.SUM;
            
            if (!isStoreData) {
                // 非持久化任务：使用历史时间占位符重新执行任务SQL
                String originalSql = task.getSqlScript();
                if (originalSql == null || originalSql.isEmpty()) {
                    logger.warn("Task {} has no SQL script for history query", task.getId());
                    return null;
                }
                String historySql = placeholderService.resolvePlaceholdersForHistory(originalSql, historyTime);
                List<Map<String, Object>> historyData = sqlExecutor.executeQuery(task.getDatasourceId(), historySql);
                // 对结果进行聚合
                return aggregate(historyData, field, method);
            }
            
            // 持久化任务：从结果表查询
            int windowHours = CompareConstants.HISTORY_SEARCH_WINDOW_HOURS;
            String aggFunc = aggMethod.name();
            
            LocalDateTime start = historyTime.minusHours(windowHours);
            LocalDateTime end = historyTime.plusHours(windowHours);
            
            String sql;
            if (isStoreData) {
                sql = """
                    SELECT %s(`%s`) as agg_value FROM `%s` 
                    WHERE batch_id = (
                        SELECT batch_id FROM `%s` 
                        WHERE `%s` BETWEEN ? AND ?
                        ORDER BY ABS(TIMESTAMPDIFF(SECOND, `%s`, ?)) 
                        LIMIT 1
                    )
                    """.formatted(aggFunc, field, tableName, tableName, timeFieldName, timeFieldName);
            } else {
                sql = """
                    SELECT %s(`%s`) as agg_value FROM `%s` 
                    WHERE `%s` BETWEEN ? AND ?
                    """.formatted(aggFunc, field, tableName, timeFieldName);
            }
            
            List<Map<String, Object>> result;
            if (isStoreData) {
                result = jdbcTemplate.queryForList(sql, start, end, historyTime);
            } else {
                result = jdbcTemplate.queryForList(sql, start, end);
            }
            
            if (!result.isEmpty() && result.get(0).get("agg_value") != null) {
                return toDouble(result.get(0).get("agg_value"));
            }
            logger.debug("No history aggregated value found for table {} at {}", tableName, historyTime);
            return null;
        } catch (Exception e) {
            logger.warn("Query history aggregated value failed: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 查询历史分组数据
     * 持久化任务：从结果表中查询指定时间点对应batch的所有记录
     * 非持久化任务：重新执行任务SQL并替换时间占位符为历史时间
     */
    private List<Map<String, Object>> queryHistoryGroupedData(MonitorTask task, String tableName, 
            String timeFieldName, boolean isStoreData, LocalDateTime historyTime) {
        try {
            if (!isStoreData) {
                // 非持久化任务：使用历史时间占位符重新执行任务SQL
                String originalSql = task.getSqlScript();
                if (originalSql == null || originalSql.isEmpty()) {
                    logger.warn("Task {} has no SQL script for history query", task.getId());
                    return new ArrayList<>();
                }
                String historySql = placeholderService.resolvePlaceholdersForHistory(originalSql, historyTime);
                List<Map<String, Object>> historyData = sqlExecutor.executeQuery(task.getDatasourceId(), historySql);
                logger.debug("Query history grouped data for task {} at {}, found {} records", 
                        task.getId(), historyTime, historyData.size());
                return historyData;
            }
            
            // 持久化任务：从结果表查询
            int windowHours = CompareConstants.HISTORY_SEARCH_WINDOW_HOURS;
            LocalDateTime start = historyTime.minusHours(windowHours);
            LocalDateTime end = historyTime.plusHours(windowHours);
            
            List<Map<String, Object>> rawData;
            
            // 先找到最接近指定时间的batch_id，再查询该批次所有数据
            String batchSql = """
                SELECT DISTINCT batch_id FROM `%s` 
                WHERE `%s` BETWEEN ? AND ?
                ORDER BY ABS(TIMESTAMPDIFF(SECOND, `%s`, ?)) 
                LIMIT 1
                """.formatted(tableName, timeFieldName, timeFieldName);
            
            List<Map<String, Object>> batchResult = jdbcTemplate.queryForList(batchSql, start, end, historyTime);
            
            if (batchResult.isEmpty() || batchResult.get(0).get(SystemFields.BATCH_ID) == null) {
                logger.debug("No history batch found for table {} at {}", tableName, historyTime);
                return new ArrayList<>();
            }
            
            String batchId = batchResult.get(0).get(SystemFields.BATCH_ID).toString();
            
            // 查询该batch的所有记录
            String dataSql = "SELECT * FROM `%s` WHERE batch_id = ?".formatted(tableName);
            rawData = jdbcTemplate.queryForList(dataSql, batchId);
            
            // 过滤系统字段
            return rawData.stream().map(row -> {
                Map<String, Object> filtered = new LinkedHashMap<>();
                row.forEach((key, value) -> {
                    if (!com.monitor.backend.constant.SystemFields.isSystemField(key)) {
                        filtered.put(key, value);
                    }
                });
                return filtered;
            }).collect(Collectors.toList());
        } catch (Exception e) {
            logger.warn("Query history grouped data failed: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    // ===== 类型转换工具 =====
    
    private Double toDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number num) return num.doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toList(Object value) {
        if (value instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return new ArrayList<>();
    }
}

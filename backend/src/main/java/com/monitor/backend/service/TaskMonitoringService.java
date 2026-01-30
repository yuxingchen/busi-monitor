package com.monitor.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitor.backend.alarm.AlarmContext;
import com.monitor.backend.alarm.AlarmService;
import com.monitor.backend.dto.CompareConfig;
import com.monitor.backend.dto.CompareResult;
import com.monitor.backend.dto.DatasetAlarmConfig;
import com.monitor.backend.enums.AggregateMethod;
import com.monitor.backend.enums.AlarmTriggerType;
import com.monitor.backend.enums.CompareOperator;
import com.monitor.backend.entity.MonitorRecord;
import com.monitor.backend.entity.MonitorTask;
import com.monitor.backend.enums.MonitorTaskType;
import com.monitor.backend.mapper.MonitorRecordMapper;
import com.monitor.backend.mapper.MonitorTaskMapper;
import com.monitor.backend.util.DateTimeUtils;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
public class TaskMonitoringService {

    private static final Logger logger = LoggerFactory.getLogger(TaskMonitoringService.class);

    private final MonitorTaskMapper taskMapper;
    private final MonitorRecordMapper recordMapper;
    private final DynamicSqlExecutor sqlExecutor;
    private final DynamicTableService dynamicTableService;
    private final AlarmService alarmService;
    private final PeriodCompareService periodCompareService;
    private final ThreadPoolTaskScheduler taskScheduler;
    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public TaskMonitoringService(MonitorTaskMapper taskMapper, MonitorRecordMapper recordMapper,
                                 DynamicSqlExecutor sqlExecutor, DynamicTableService dynamicTableService,
                                 AlarmService alarmService, PeriodCompareService periodCompareService,
                                 ObjectMapper objectMapper) {
        this.taskMapper = taskMapper;
        this.recordMapper = recordMapper;
        this.sqlExecutor = sqlExecutor;
        this.dynamicTableService = dynamicTableService;
        this.alarmService = alarmService;
        this.periodCompareService = periodCompareService;
        this.objectMapper = objectMapper;
        this.taskScheduler = new ThreadPoolTaskScheduler();
        this.taskScheduler.setPoolSize(10);
        this.taskScheduler.setThreadNamePrefix("MonitorJob-");
        this.taskScheduler.initialize();
    }

    @PostConstruct
    public void init() {
        List<MonitorTask> activeTasks = taskMapper.findAllActive();
        activeTasks.forEach(this::scheduleTask);
        logger.info("Initialized {} monitoring tasks.", activeTasks.size());
    }

    public void refreshTask(Long taskId) {
        cancelTask(taskId);
        MonitorTask task = taskMapper.findById(taskId);
        if (task != null && task.getIsActive() == 1) {
            scheduleTask(task);
        }
    }

    public void cancelTask(Long taskId) {
        ScheduledFuture<?> future = scheduledTasks.remove(taskId);
        if (future != null) {
            future.cancel(false);
        }
    }

    private void scheduleTask(MonitorTask task) {
        try {
            ScheduledFuture<?> future = taskScheduler.schedule(
                    () -> executeTask(task),
                    new CronTrigger(task.getCronExpression()));
            scheduledTasks.put(task.getId(), future);
            logger.info("Scheduled task: {} (Cron: {})", task.getName(), task.getCronExpression());
        } catch (Exception e) {
            logger.error("Failed to schedule task: " + task.getId(), e);
        }
    }

    public void executeTask(MonitorTask task) {
        logger.info("Executing task: {}", task.getName());
        MonitorRecord record = new MonitorRecord();
        record.setTaskId(task.getId());
        record.setExecutionTime(DateTimeUtils.now());

        try {
            if ("SCALAR".equalsIgnoreCase(task.getResultType())) {
                Double result = sqlExecutor.executeScalar(task.getDatasourceId(), task.getSqlScript(), task.getId());
                record.setResultNumber(result);
                record.setIsSuccess(1);
                recordMapper.insert(record); // 保存获取 ID

                // Alarm Logic for SCALAR
                checkAlarm(task, result, null);

            } else {
                List<Map<String, Object>> resultSet = sqlExecutor.executeQuery(task.getDatasourceId(), task.getSqlScript(), task.getId());
                record.setIsSuccess(1);

                // 根据数据量决定 resultJson 内容....//[
                if (resultSet.isEmpty()) {
                    record.setResultJson("[]");
                } else if (resultSet.size() <= 10) {
                    record.setResultJson(objectMapper.writeValueAsString(resultSet));
                } else {
                    // 大数据集：先存占位符
                    record.setResultJson("[{\"info\":\"Data too large, stored in physical table\"}]");
                }

                // 先保存 record 获取 ID
                recordMapper.insert(record);

                // 使用 record.id 作为 batch_id 存储详细数据
                if (task.getIsStoreData() != null && task.getIsStoreData() == 1 && !resultSet.isEmpty()) {
                    String batchId = String.valueOf(record.getId());

                    // 解析 chartConfig 获取自定义表名和索引字段
                    String customTableName = null;
                    List<String> indexFields = null;
                    if (task.getChartConfig() != null && !task.getChartConfig().isEmpty()) {
                        try {
                            com.monitor.backend.dto.ChartConfigDto chartConfig = objectMapper
                                    .readValue(task.getChartConfig(), com.monitor.backend.dto.ChartConfigDto.class);
                            customTableName = chartConfig.getOutputTable();
                            indexFields = chartConfig.getIndexFields();
                        } catch (Exception e) {
                            logger.warn("Failed to parse chartConfig for task {}: {}", task.getId(), e.getMessage());
                        }
                    }

                    // 确定最终表名
                    String tableName = DynamicTableService.getTaskResultTableName(task.getId(), customTableName);

                    // 创建表并保存数据
                    dynamicTableService.ensureTaskTableWithCustomName(tableName, resultSet.get(0));
                    dynamicTableService.saveTaskDataToTable(tableName, batchId, resultSet);

                    // 创建索引（如果配置了）
                    if (indexFields != null && !indexFields.isEmpty()) {
                        dynamicTableService.ensureIndexes(tableName, indexFields);
                        logger.info("Created indexes on {} for fields: {}", tableName, indexFields);
                    }

                    logger.info("Saved {} rows to {} with batch_id={}", resultSet.size(), tableName, batchId);
                }

                // 检查 DATASET 告警
                checkAlarm(task, null, resultSet);
            }
        } catch (Exception e) {
            logger.error("Task execution failed: {}", task.getName(), e);
            record.setIsSuccess(0);
            record.setErrorMessage(e.getMessage());
            recordMapper.insert(record);
        }
    }

    /**
     * 统一的告警检查方法
     * 支持 SCALAR 和 DATASET 两种结果类型
     *
     * @param task        监控任务
     * @param scalarValue SCALAR 类型的结果值（DATASET 类型传 null）
     * @param resultSet   DATASET 类型的结果集（SCALAR 类型传 null）
     */
    private void checkAlarm(MonitorTask task, Double scalarValue, List<Map<String, Object>> resultSet) {
        if (task.getAlarmConfig() == null || task.getAlarmConfig().isEmpty()) {
            return;
        }

        try {
            DatasetAlarmConfig alarmConfig = objectMapper.readValue(task.getAlarmConfig(), DatasetAlarmConfig.class);

            // 检查告警是否启用
            if (alarmConfig == null || !Boolean.TRUE.equals(alarmConfig.getEnabled())) {
                return;
            }

            String operator = alarmConfig.getOperator();
            if (operator == null) {
                return;
            }

            boolean isAlarm = false;
            AlarmTriggerType triggerType = AlarmTriggerType.fromCode(alarmConfig.getTriggerType());
            Double currentValue = null;
            Double thresholdValue = alarmConfig.getThreshold();

            // 根据结果类型和触发类型计算告警条件
            if (scalarValue != null) {
                // SCALAR 类型：直接使用查询结果值
                currentValue = scalarValue;
                triggerType = AlarmTriggerType.THRESHOLD;  // SCALAR 使用阈值触发
                if (thresholdValue != null) {
                    isAlarm = CompareOperator.fromSymbol(operator).compare(scalarValue, thresholdValue);
                }
            } else if (resultSet != null) {
                // DATASET 类型：根据触发类型处理
                if (triggerType == null) {
                    return;
                }

                if (AlarmTriggerType.FIELD_VALUE.equals(triggerType)) {
                    // 字段值支持字符串比较
                    isAlarm = checkFieldValueAlarm(alarmConfig, resultSet, operator);
                } else if (AlarmTriggerType.COMPARE_PERIOD.equals(triggerType)) {
                    // 同比环比对比
                    CompareConfig compareConfig = alarmConfig.getCompareConfig();
                    if (compareConfig != null && Boolean.TRUE.equals(compareConfig.getEnabled())) {
                        CompareResult compareResult = periodCompareService.compare(task.getId(), resultSet, compareConfig);
                        if (compareResult != null && compareResult.isShouldAlert()) {
                            isAlarm = true;
                            // 将对比结果保存到extraParams中，供后续构建告警上下文使用
                            currentValue = compareResult.getCurrentValue();
                            thresholdValue = compareResult.getPreviousValue();
                        }
                    }
                } else {
                    // ROW_COUNT 和 FIELD_AGG 使用数值比较
                    if (thresholdValue == null) {
                        return;
                    }
                    currentValue = calculateTriggerValue(triggerType, alarmConfig, resultSet);
                    if (currentValue != null) {
                        isAlarm = CompareOperator.fromSymbol(operator).compare(currentValue, thresholdValue);
                    }
                }
            } else {
                return;
            }

            if (isAlarm) {
                logger.warn("ALARM TRIGGERED for Task {}: type={}, operator={}",
                        task.getName(), triggerType, operator);

                // 构建告警上下文
                AlarmContext context = new AlarmContext();
                context.setTaskId(task.getId());
                context.setTaskType(MonitorTaskType.MONITOR_TASK.name());
                context.setTaskName(task.getName());
                context.setTriggerType(triggerType.getCode());
                context.setOperator(operator);

                // 设置数值（如果有）
                if (currentValue != null) {
                    context.setCurrentValue(currentValue);
                }
                if (thresholdValue != null) {
                    context.setThresholdValue(thresholdValue);
                }

                // 设置告警模板ID
                if (alarmConfig.getAlarmTemplateId() != null) {
                    context.setAlarmTemplateId(alarmConfig.getAlarmTemplateId());
                }

                // 设置告警渠道
                if (alarmConfig.getChannelIds() != null && !alarmConfig.getChannelIds().isEmpty()) {
                    context.setChannelIds(alarmConfig.getChannelIds());
                }

                // 构建模板参数（DATASET 类型时从结果集提取）
                if (resultSet != null && !resultSet.isEmpty()) {
                    Map<String, Object> extraParams = buildTemplateParams(alarmConfig, resultSet);
                    context.setExtraParams(extraParams);
                }

                alarmService.triggerAlarm(context);
            } else {
                // 值正常，恢复告警
                alarmService.resolveAlarm(task.getId(), MonitorTaskType.MONITOR_TASK.name());
            }

        } catch (Exception e) {
            logger.error("Failed to check alarm for task {}: {}", task.getId(), e.getMessage());
        }
    }

    /**
     * 检查 FIELD_VALUE 类型的告警（支持字符串比较）
     */
    private boolean checkFieldValueAlarm(DatasetAlarmConfig config,
                                         List<Map<String, Object>> resultSet, String operator) {

        if (config.getTriggerField() == null || resultSet == null || resultSet.isEmpty()) {
            return false;
        }

        Object fieldValue = resultSet.get(0).get(config.getTriggerField());
        if (fieldValue == null) {
            return false;
        }

        try {
            CompareOperator op = CompareOperator.fromSymbol(operator);
            return op.smartCompare(fieldValue, config.getThreshold(), config.getThresholdStr());
        } catch (IllegalArgumentException e) {
            logger.warn("Unknown operator for FIELD_VALUE: {}", operator);
            return false;
        }
    }

    /**
     * 根据触发类型计算触发值
     */
    private Double calculateTriggerValue(AlarmTriggerType type,
                                         com.monitor.backend.dto.DatasetAlarmConfig config,
                                         List<Map<String, Object>> resultSet) {

        if (resultSet == null) {
            return null;
        }

        if (type == null) {
            logger.warn("trigger type is null");
            return null;
        }

        return switch (type) {
            case ROW_COUNT -> (double) resultSet.size();
            case FIELD_VALUE -> {
                // 检查第一行的指定字段值
                if (config.getTriggerField() == null || resultSet.isEmpty()) {
                    yield null;
                }
                Object value = resultSet.get(0).get(config.getTriggerField());
                yield toDouble(value);
            }
            case FIELD_AGG -> {
                // 对指定字段进行聚合计算
                if (config.getTriggerField() == null || resultSet.isEmpty()) {
                    yield null;
                }
                yield calculateAggregate(config.getAggregateMethod(), config.getTriggerField(), resultSet);
            }
            default -> null;
        };
    }

    /**
     * 对结果集指定字段进行聚合计算
     */
    private Double calculateAggregate(String method, String field, List<Map<String, Object>> resultSet) {
        if (method == null || field == null) {
            return null;
        }

        double sum = 0;
        int count = 0;
        Double max = null;
        Double min = null;

        for (Map<String, Object> row : resultSet) {
            Double val = toDouble(row.get(field));
            if (val != null) {
                sum += val;
                count++;
                if (max == null || val > max) max = val;
                if (min == null || val < min) min = val;
            }
        }

        if (count == 0) {
            return null;
        }

        AggregateMethod aggMethod = AggregateMethod.fromCode(method);
        if (aggMethod == null) {
            return null;
        }

        return switch (aggMethod) {
            case SUM -> sum;
            case COUNT -> (double) count;
            case AVG -> sum / count;
            case MAX -> max;
            case MIN -> min;
        };
    }

    /**
     * 构建模板参数，将结果集中指定字段的值用分隔符连接
     */
    private Map<String, Object> buildTemplateParams(
            com.monitor.backend.dto.DatasetAlarmConfig config,
            List<Map<String, Object>> resultSet) {

        Map<String, Object> params = new java.util.HashMap<>();

        if (config.getTemplateFields() == null || config.getTemplateFields().isEmpty() || resultSet.isEmpty()) {
            return params;
        }

        String separator = config.getFieldSeparator();
        int maxRows = config.getMaxRows();

        // 限制处理的行数
        List<Map<String, Object>> limitedRows = resultSet.size() > maxRows
                ? resultSet.subList(0, maxRows)
                : resultSet;

        // 为每个模板字段构建值列表
        for (String field : config.getTemplateFields()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < limitedRows.size(); i++) {
                Object value = limitedRows.get(i).get(field);
                if (i > 0) {
                    sb.append(separator);
                }
                sb.append(value != null ? value.toString() : "");
            }
            params.put(field, sb.toString());
        }

        // 添加记录条数参数
        params.put("rowCount", resultSet.size());
        params.put("limitedRowCount", limitedRows.size());

        return params;
    }

    /**
     * 将对象转换为 Double
     */
    private Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}


package com.monitor.backend.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitor.backend.alarm.AlarmContext;
import com.monitor.backend.alarm.AlarmService;
import com.monitor.backend.entity.MonitorRecord;
import com.monitor.backend.entity.MonitorTask;
import com.monitor.backend.mapper.MonitorRecordMapper;
import com.monitor.backend.mapper.MonitorTaskMapper;

import jakarta.annotation.PostConstruct;

@Service
public class TaskMonitoringService {

    private static final Logger logger = LoggerFactory.getLogger(TaskMonitoringService.class);

    private final MonitorTaskMapper taskMapper;
    private final MonitorRecordMapper recordMapper;
    private final DynamicSqlExecutor sqlExecutor;
    private final DynamicTableService dynamicTableService;
    private final AlarmService alarmService;
    private final ThreadPoolTaskScheduler taskScheduler;
    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public TaskMonitoringService(MonitorTaskMapper taskMapper, MonitorRecordMapper recordMapper,
            DynamicSqlExecutor sqlExecutor, DynamicTableService dynamicTableService,
            AlarmService alarmService, ObjectMapper objectMapper) {
        this.taskMapper = taskMapper;
        this.recordMapper = recordMapper;
        this.sqlExecutor = sqlExecutor;
        this.dynamicTableService = dynamicTableService;
        this.alarmService = alarmService;
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
        record.setExecutionTime(java.time.LocalDateTime.now());

        try {
            List<Map<String, Object>> resultSet;
            if ("SCALAR".equalsIgnoreCase(task.getResultType())) {
                Double result = sqlExecutor.executeScalar(task.getDatasourceId(), task.getSqlScript(), task.getId());
                record.setResultNumber(result);
                record.setIsSuccess(1);
                recordMapper.insert(record); // 保存获取 ID

                // Alarm Logic for SCALAR
                checkAlarm(task, result);

            } else {
                resultSet = sqlExecutor.executeQuery(task.getDatasourceId(), task.getSqlScript(), task.getId());
                record.setIsSuccess(1);

                // 根据数据量决定 resultJson 内容
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
            }
        } catch (Exception e) {
            logger.error("Task execution failed: " + task.getName(), e);
            record.setIsSuccess(0);
            record.setErrorMessage(e.getMessage());
            recordMapper.insert(record);
        }
    }

    private void checkAlarm(MonitorTask task, Double value) {
        if (value == null || task.getAlarmThresholdRule() == null || task.getAlarmThresholdRule().isEmpty())
            return;

        try {
            Map<String, Object> rule = objectMapper.readValue(task.getAlarmThresholdRule(), Map.class);
            String operator = (String) rule.get("operator");
            Number threshold = (Number) rule.get("value");

            if (operator != null && threshold != null) {
                double thresholdVal = threshold.doubleValue();
                boolean isAlarm = false;
                switch (operator) {
                    case ">":
                        isAlarm = value > thresholdVal;
                        break;
                    case ">=":
                        isAlarm = value >= thresholdVal;
                        break;
                    case "<":
                        isAlarm = value < thresholdVal;
                        break;
                    case "<=":
                        isAlarm = value <= thresholdVal;
                        break;
                    case "=":
                        isAlarm = Math.abs(value - thresholdVal) < 0.0001;
                        break;
                    default:
                        break;
                }

                if (isAlarm) {
                    logger.warn("ALARM TRIGGERED for Task {}: Value {} {} {}", task.getName(), value, operator,
                            thresholdVal);

                    // 触发告警服务
                    AlarmContext context = new AlarmContext();
                    context.setTaskId(task.getId());
                    context.setTaskType("MONITOR_TASK");
                    context.setTaskName(task.getName());
                    context.setTriggerType("THRESHOLD");
                    context.setCurrentValue(value);
                    context.setThresholdValue(thresholdVal);
                    context.setOperator(operator);
                    alarmService.triggerAlarm(context);
                } else {
                    // 值正常，检查是否需要恢复告警
                    alarmService.resolveAlarm(task.getId(), "MONITOR_TASK");
                }
            }
        } catch (Exception e) {
            logger.error("Failed to parse alarm rule", e);
        }
    }
}

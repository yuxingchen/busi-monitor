package com.monitor.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitor.backend.alarm.AlarmContext;
import com.monitor.backend.alarm.AlarmService;
import com.monitor.backend.constant.BatchDefaults;
import com.monitor.backend.constant.CompareOperator;
import com.monitor.backend.entity.MonitorTemplate;
import com.monitor.backend.entity.ServerAsset;
import com.monitor.backend.entity.ServerMonitorTask;
import com.monitor.backend.mapper.MonitorTemplateMapper;
import com.monitor.backend.mapper.ServerAssetMapper;
import com.monitor.backend.mapper.ServerMonitorTaskMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 服务器监控任务定时调度服务
 * <p>
 * 负责根据 cronExpression 定时执行服务器监控任务
 * </p>
 */
@Service
public class ServerMonitorTaskScheduler {

    private static final Logger logger = LoggerFactory.getLogger(ServerMonitorTaskScheduler.class);

    private final ServerMonitorTaskMapper taskMapper;
    private final ServerAssetMapper serverMapper;
    private final MonitorTemplateMapper templateMapper;
    private final SshExecutorService sshExecutorService;
    private final AlarmService alarmService;
    private final ObjectMapper objectMapper;
    private final ThreadPoolTaskScheduler taskScheduler;
    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    public ServerMonitorTaskScheduler(ServerMonitorTaskMapper taskMapper,
                                      ServerAssetMapper serverMapper,
                                      MonitorTemplateMapper templateMapper,
                                      SshExecutorService sshExecutorService,
                                      AlarmService alarmService,
                                      ObjectMapper objectMapper) {
        this.taskMapper = taskMapper;
        this.serverMapper = serverMapper;
        this.templateMapper = templateMapper;
        this.sshExecutorService = sshExecutorService;
        this.alarmService = alarmService;
        this.objectMapper = objectMapper;
        this.taskScheduler = new ThreadPoolTaskScheduler();
        this.taskScheduler.setPoolSize(10);
        this.taskScheduler.setThreadNamePrefix("ServerMonitorJob-");
        this.taskScheduler.initialize();
    }

    @PostConstruct
    public void init() {
        List<ServerMonitorTask> activeTasks = taskMapper.findAllActive();
        activeTasks.forEach(this::scheduleTask);
        logger.info("初始化服务器监控任务调度器，共 {} 个活跃任务", activeTasks.size());
    }

    /**
     * 刷新任务调度（任务更新后调用）
     */
    public void refreshTask(Long taskId) {
        cancelTask(taskId);
        ServerMonitorTask task = taskMapper.findById(taskId);
        if (task != null && task.getIsActive() != null && task.getIsActive() == 1) {
            scheduleTask(task);
        }
    }

    /**
     * 取消任务调度
     */
    public void cancelTask(Long taskId) {
        ScheduledFuture<?> future = scheduledTasks.remove(taskId);
        if (future != null) {
            future.cancel(false);
            logger.info("取消服务器监控任务调度: taskId={}", taskId);
        }
    }

    /**
     * 调度任务
     */
    private void scheduleTask(ServerMonitorTask task) {
        if (task.getCronExpression() == null || task.getCronExpression().isEmpty()) {
            logger.debug("任务 {} 未配置 cronExpression，跳过调度", task.getId());
            return;
        }

        try {
            ScheduledFuture<?> future = taskScheduler.schedule(
                    () -> executeTask(task.getId()),
                    new CronTrigger(task.getCronExpression()));
            scheduledTasks.put(task.getId(), future);
            logger.info("调度服务器监控任务: {} (Cron: {})", task.getName(), task.getCronExpression());
        } catch (Exception e) {
            logger.error("调度服务器监控任务失败: taskId={}, error={}", task.getId(), e.getMessage());
        }
    }

    /**
     * 执行任务
     */
    public void executeTask(Long taskId) {
        ServerMonitorTask task = taskMapper.findById(taskId);
        if (task == null) {
            logger.warn("服务器监控任务不存在: taskId={}", taskId);
            cancelTask(taskId);
            return;
        }

        ServerAsset server = serverMapper.findById(task.getServerId());
        if (server == null) {
            logger.warn("服务器不存在: serverId={}", task.getServerId());
            return;
        }

        logger.info("执行服务器监控任务: {} [{}]", task.getName(), server.getIp());

        try {
            // 替换脚本中的参数变量
            String script = task.getCollectScript();
            if (task.getParams() != null && !task.getParams().isEmpty()) {
                script = script.replace("${port}", extractParam(task.getParams(), "port", "80"));
                script = script.replace("${path}", extractParam(task.getParams(), "path", "/"));
                script = script.replace("${log_path}", extractParam(task.getParams(), "log_path", "/var/log/app.log"));
            }

            String output = sshExecutorService.executeScript(server, script);

            // 更新运行状态
            task.setLastRunTime(LocalDateTime.now());
            task.setLastRunStatus("SUCCESS");
            task.setLastRunValue(output.trim());
            taskMapper.updateRunStatus(task);

            // 检查告警阈值
            checkServerAlarm(task, server, output.trim());

            logger.info("服务器监控任务执行成功: {} = {}", task.getName(), output.trim());

        } catch (Exception e) {
            logger.error("服务器监控任务执行失败: {} - {}", task.getName(), e.getMessage());
            task.setLastRunTime(LocalDateTime.now());
            task.setLastRunStatus("FAILED");
            task.setLastRunValue(e.getMessage());
            taskMapper.updateRunStatus(task);
        }
    }

    /**
     * 检查服务器监控告警阈值
     */
    private void checkServerAlarm(ServerMonitorTask task, ServerAsset server, String valueStr) {
        if (task.getThresholdRule() == null || task.getThresholdRule().isEmpty()) {
            return;
        }

        try {
            Double value = parseNumericValue(valueStr);
            if (value == null) {
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> rule = objectMapper.readValue(task.getThresholdRule(), Map.class);
            String operator = (String) rule.get("operator");
            Number threshold = (Number) rule.get("value");

            if (operator == null || threshold == null) {
                return;
            }

            double thresholdVal = threshold.doubleValue();
            boolean isAlarm = CompareOperator.fromSymbol(operator).compare(value, thresholdVal);

            if (isAlarm) {
                logger.warn("服务器告警触发: {} [{}] - 值: {} {} {}",
                        task.getName(), server.getIp(), value, operator, thresholdVal);

                AlarmContext context = new AlarmContext();
                context.setTaskId(task.getId());
                context.setTaskType("SERVER_MONITOR");
                context.setTaskName(task.getName() + " [" + server.getName() + "]");
                context.setTriggerType("THRESHOLD");
                context.setCurrentValue(value);
                context.setThresholdValue(thresholdVal);
                context.setOperator(operator);

                // 设置告警模板
                if (task.getAlarmTemplateId() != null) {
                    context.setAlarmTemplateId(task.getAlarmTemplateId());
                } else if (task.getTemplateId() != null) {
                    MonitorTemplate monitorTemplate = templateMapper.findById(task.getTemplateId());
                    if (monitorTemplate != null && monitorTemplate.getAlarmTemplateId() != null) {
                        context.setAlarmTemplateId(monitorTemplate.getAlarmTemplateId());
                    }
                }

                // 设置额外参数
                Map<String, Object> extraParams = new HashMap<>();
                extraParams.put("serverName", server.getName());
                extraParams.put("ip", server.getIp());
                context.setExtraParams(extraParams);

                // 设置告警渠道
                if (task.getAlarmChannels() != null && !task.getAlarmChannels().isEmpty()) {
                    List<Long> channelIds = java.util.Arrays.stream(task.getAlarmChannels().split(BatchDefaults.DEFAULT_SEPARATOR))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .map(Long::parseLong)
                            .collect(java.util.stream.Collectors.toList());
                    context.setChannelIds(channelIds);
                }

                alarmService.triggerAlarm(context);
            } else {
                alarmService.resolveAlarm(task.getId(), "SERVER_MONITOR");
            }
        } catch (Exception e) {
            logger.error("服务器告警检查失败: {}", e.getMessage());
        }
    }

    private Double parseNumericValue(String valueStr) {
        if (valueStr == null || valueStr.isEmpty()) {
            return null;
        }
        try {
            String cleaned = valueStr.replaceAll("[%\\s]", "").trim();
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String extractParam(String paramsJson, String key, String defaultValue) {
        try {
            if (paramsJson.contains("\"" + key + "\"")) {
                int start = paramsJson.indexOf("\"" + key + "\"");
                int colonPos = paramsJson.indexOf(":", start);
                int valueStart = colonPos + 1;
                while (valueStart < paramsJson.length() &&
                        (paramsJson.charAt(valueStart) == ' ' || paramsJson.charAt(valueStart) == '"')) {
                    valueStart++;
                }
                int valueEnd = valueStart;
                while (valueEnd < paramsJson.length() &&
                        paramsJson.charAt(valueEnd) != '"' &&
                        paramsJson.charAt(valueEnd) != ',' &&
                        paramsJson.charAt(valueEnd) != '}') {
                    valueEnd++;
                }
                return paramsJson.substring(valueStart, valueEnd);
            }
        } catch (Exception e) {
            // ignore
        }
        return defaultValue;
    }

    /**
     * 获取调度统计信息
     */
    public Map<String, Object> getSchedulerStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("scheduledTaskCount", scheduledTasks.size());
        stats.put("taskIds", scheduledTasks.keySet());
        return stats;
    }
}

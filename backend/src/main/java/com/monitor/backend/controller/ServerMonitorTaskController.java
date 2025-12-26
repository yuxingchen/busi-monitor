package com.monitor.backend.controller;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitor.backend.alarm.AlarmContext;
import com.monitor.backend.alarm.AlarmService;
import com.monitor.backend.entity.MonitorTemplate;
import com.monitor.backend.entity.ServerAsset;
import com.monitor.backend.entity.ServerMonitorTask;
import com.monitor.backend.mapper.MonitorTemplateMapper;
import com.monitor.backend.mapper.ServerAssetMapper;
import com.monitor.backend.mapper.ServerMonitorTaskMapper;
import com.monitor.backend.service.ServerMonitorTaskScheduler;
import com.monitor.backend.service.SshExecutorService;

@RestController
@RequestMapping("/api/server-monitor-task")
public class ServerMonitorTaskController {

    private static final Logger logger = LoggerFactory.getLogger(ServerMonitorTaskController.class);

    private final ServerMonitorTaskMapper taskMapper;
    private final ServerAssetMapper serverMapper;
    private final MonitorTemplateMapper templateMapper;
    private final SshExecutorService sshExecutorService;
    private final AlarmService alarmService;
    private final ObjectMapper objectMapper;
    private final ServerMonitorTaskScheduler taskScheduler;

    public ServerMonitorTaskController(ServerMonitorTaskMapper taskMapper,
            ServerAssetMapper serverMapper,
            MonitorTemplateMapper templateMapper,
            SshExecutorService sshExecutorService,
            AlarmService alarmService,
            ObjectMapper objectMapper,
            ServerMonitorTaskScheduler taskScheduler) {
        this.taskMapper = taskMapper;
        this.serverMapper = serverMapper;
        this.templateMapper = templateMapper;
        this.sshExecutorService = sshExecutorService;
        this.alarmService = alarmService;
        this.objectMapper = objectMapper;
        this.taskScheduler = taskScheduler;
    }

    @GetMapping
    public List<ServerMonitorTask> getAll() {
        return taskMapper.findAll();
    }

    @GetMapping("/{id}")
    public ServerMonitorTask getById(@PathVariable Long id) {
        return taskMapper.findById(id);
    }

    @GetMapping("/server/{serverId}")
    public List<ServerMonitorTask> getByServer(@PathVariable Long serverId) {
        return taskMapper.findByServerId(serverId);
    }

    @PostMapping
    public Map<String, Object> add(@RequestBody ServerMonitorTask task) {
        Map<String, Object> result = new HashMap<>();
        task.setIsActive(1);

        // 如果使用模板且未指定脚本，从模板获取
        if (task.getTemplateId() != null && (task.getCollectScript() == null || task.getCollectScript().isEmpty())) {
            MonitorTemplate template = templateMapper.findById(task.getTemplateId());
            if (template != null) {
                task.setCollectScript(template.getCollectScript());
                if (task.getThresholdRule() == null) {
                    task.setThresholdRule(template.getDefaultThreshold());
                }
                if (task.getName() == null || task.getName().isEmpty()) {
                    task.setName(template.getName());
                }
            }
        }

        taskMapper.insert(task);
        taskScheduler.refreshTask(task.getId()); // 注册调度
        result.put("status", "success");
        result.put("id", task.getId());
        return result;
    }

    /**
     * 批量添加（为一台服务器绑定多个模板）
     */
    @PostMapping("/batch")
    public Map<String, Object> batchAdd(@RequestBody Map<String, Object> params) {
        Long serverId = Long.valueOf(params.get("serverId").toString());
        @SuppressWarnings("unchecked")
        List<Number> templateIds = (List<Number>) params.get("templateIds");

        int successCount = 0;
        for (Number templateId : templateIds) {
            try {
                MonitorTemplate template = templateMapper.findById(templateId.longValue());
                if (template != null) {
                    ServerMonitorTask task = new ServerMonitorTask();
                    task.setServerId(serverId);
                    task.setTemplateId(templateId.longValue());
                    task.setName(template.getName());
                    task.setCollectScript(template.getCollectScript());
                    task.setThresholdRule(template.getDefaultThreshold());
                    task.setCronExpression(template.getDefaultCron());
                    task.setIsActive(1);
                    taskMapper.insert(task);
                    successCount++;
                }
            } catch (Exception e) {
                // 忽略重复
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("count", successCount);
        return result;
    }

    @PutMapping
    public String update(@RequestBody ServerMonitorTask task) {
        taskMapper.update(task);
        taskScheduler.refreshTask(task.getId()); // 刷新调度
        return "success";
    }

    @PutMapping("/{id}")
    public String updateById(@PathVariable Long id, @RequestBody Map<String, Object> updates) {
        ServerMonitorTask task = taskMapper.findById(id);
        if (task == null) {
            return "error: task not found";
        }

        // 支持部分更新
        if (updates.containsKey("isActive")) {
            Object isActiveValue = updates.get("isActive");
            if (isActiveValue instanceof Boolean) {
                task.setIsActive((Boolean) isActiveValue ? 1 : 0);
            } else if (isActiveValue instanceof Integer) {
                task.setIsActive((Integer) isActiveValue);
            } else if (isActiveValue instanceof Number) {
                task.setIsActive(((Number) isActiveValue).intValue());
            }
        }
        if (updates.containsKey("cronExpression")) {
            task.setCronExpression((String) updates.get("cronExpression"));
        }

        taskMapper.update(task);
        taskScheduler.refreshTask(id); // 刷新调度
        return "success";
    }

    @DeleteMapping("/{id}")
    public String delete(@PathVariable Long id) {
        taskScheduler.cancelTask(id); // 取消调度
        taskMapper.deleteById(id);
        return "success";
    }

    /**
     * 执行监控任务（立即采集）
     */
    @PostMapping("/{id}/run")
    public Map<String, Object> runTask(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        ServerMonitorTask task = taskMapper.findById(id);
        if (task == null) {
            result.put("status", "error");
            result.put("message", "任务不存在");
            return result;
        }

        ServerAsset server = serverMapper.findById(task.getServerId());
        if (server == null) {
            result.put("status", "error");
            result.put("message", "服务器不存在");
            return result;
        }

        try {
            // 替换脚本中的参数变量
            String script = task.getCollectScript();
            if (task.getParams() != null && !task.getParams().isEmpty()) {
                // 简单的变量替换（生产环境应使用JSON解析）
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

            result.put("status", "success");
            result.put("value", output.trim());
            result.put("runTime", task.getLastRunTime());
        } catch (Exception e) {
            task.setLastRunTime(LocalDateTime.now());
            task.setLastRunStatus("FAILED");
            task.setLastRunValue(e.getMessage());
            taskMapper.updateRunStatus(task);

            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    /**
     * 检查服务器监控告警阈值
     */
    private void checkServerAlarm(ServerMonitorTask task, ServerAsset server, String valueStr) {
        logger.info("检查告警: taskId={}, taskName={}, value={}, thresholdRule={}",
                task.getId(), task.getName(), valueStr, task.getThresholdRule());

        if (task.getThresholdRule() == null || task.getThresholdRule().isEmpty()) {
            logger.info("跳过告警检查: 未配置阈值规则");
            return;
        }

        try {
            // 尝试将输出转为数值
            Double value = parseNumericValue(valueStr);
            if (value == null) {
                logger.debug("无法解析服务器监控值为数字: {}", valueStr);
                return;
            }

            // 解析阈值规则
            @SuppressWarnings("unchecked")
            Map<String, Object> rule = objectMapper.readValue(task.getThresholdRule(), Map.class);
            String operator = (String) rule.get("operator");
            Number threshold = (Number) rule.get("value");

            if (operator == null || threshold == null) {
                return;
            }

            double thresholdVal = threshold.doubleValue();
            boolean isAlarm = evaluateThreshold(value, operator, thresholdVal);

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

                // 设置告警模板（优先级：1. 任务指定的告警模板ID 2. 监控模板关联的告警模板ID 3. 默认格式）
                if (task.getAlarmTemplateId() != null) {
                    // 使用任务指定的告警模板ID
                    context.setAlarmTemplateId(task.getAlarmTemplateId());
                } else if (task.getTemplateId() != null) {
                    // 回退：使用 monitor_template 关联的 alarm_template_id
                    MonitorTemplate monitorTemplate = templateMapper.findById(task.getTemplateId());
                    if (monitorTemplate != null && monitorTemplate.getAlarmTemplateId() != null) {
                        context.setAlarmTemplateId(monitorTemplate.getAlarmTemplateId());
                    }
                }

                // 设置额外参数用于模板变量替换
                Map<String, Object> extraParams = new HashMap<>();
                extraParams.put("serverName", server.getName());
                extraParams.put("ip", server.getIp());
                context.setExtraParams(extraParams);

                // 设置配置的告警渠道
                if (task.getAlarmChannels() != null && !task.getAlarmChannels().isEmpty()) {
                    List<Long> channelIds = java.util.Arrays.stream(task.getAlarmChannels().split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .map(Long::parseLong)
                            .collect(java.util.stream.Collectors.toList());
                    context.setChannelIds(channelIds);
                }

                alarmService.triggerAlarm(context);
            } else {
                // 值正常，恢复告警
                alarmService.resolveAlarm(task.getId(), "SERVER_MONITOR");
            }
        } catch (Exception e) {
            logger.error("服务器告警检查失败: {}", e.getMessage());
        }
    }

    /**
     * 解析数值（支持百分比等格式）
     */
    private Double parseNumericValue(String valueStr) {
        if (valueStr == null || valueStr.isEmpty()) {
            return null;
        }
        try {
            // 移除百分号和空白
            String cleaned = valueStr.replaceAll("[%\\s]", "").trim();
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 评估阈值条件
     */
    private boolean evaluateThreshold(double value, String operator, double threshold) {
        return switch (operator) {
            case ">" -> value > threshold;
            case ">=" -> value >= threshold;
            case "<" -> value < threshold;
            case "<=" -> value <= threshold;
            case "=" -> Math .abs(value - threshold) < 0.0001;
            default -> false ;
        };
    }

    private String extractParam(String paramsJson, String key, String defaultValue) {
        // 简单的JSON参数提取
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
}

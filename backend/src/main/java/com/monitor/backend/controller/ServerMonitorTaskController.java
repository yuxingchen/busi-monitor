package com.monitor.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitor.backend.alarm.AlarmContext;
import com.monitor.backend.alarm.AlarmService;
import com.monitor.backend.common.ApiResponse;
import com.monitor.backend.constant.BatchDefaults;
import com.monitor.backend.enums.CompareOperator;
import com.monitor.backend.dto.task.ServerMonitorTaskRequest;
import com.monitor.backend.entity.MonitorTemplate;
import com.monitor.backend.entity.ServerAsset;
import com.monitor.backend.entity.ServerMonitorTask;
import com.monitor.backend.exception.BusinessException;
import com.monitor.backend.exception.ErrorCode;
import com.monitor.backend.mapper.MonitorTemplateMapper;
import com.monitor.backend.mapper.ServerAssetMapper;
import com.monitor.backend.mapper.ServerMonitorTaskMapper;
import com.monitor.backend.service.ServerMonitorTaskScheduler;
import com.monitor.backend.service.SshExecutorService;
import com.monitor.backend.util.DateTimeUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务器监控任务控制器
 *
 * @author monitor-system
 */
@Tag(name = "服务器监控任务", description = "服务器监控任务的增删改查和执行")
@RestController
@RequestMapping("/api/server-monitor-task")
public class ServerMonitorTaskController {

    private static final Logger log = LoggerFactory.getLogger(ServerMonitorTaskController.class);

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

    @Operation(summary = "获取所有服务器监控任务")
    @GetMapping
    public ApiResponse<List<ServerMonitorTask>> getAll() {
        return ApiResponse.ok(taskMapper.findAll());
    }

    @Operation(summary = "获取任务详情")
    @GetMapping("/{id}")
    public ApiResponse<ServerMonitorTask> getById(
            @Parameter(description = "任务ID") @PathVariable Long id) {
        ServerMonitorTask task = taskMapper.findById(id);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }
        return ApiResponse.ok(task);
    }

    @Operation(summary = "按服务器获取任务")
    @GetMapping("/server/{serverId}")
    public ApiResponse<List<ServerMonitorTask>> getByServer(
            @Parameter(description = "服务器ID") @PathVariable Long serverId) {
        return ApiResponse.ok(taskMapper.findByServerId(serverId));
    }

    @Operation(summary = "添加监控任务")
    @PostMapping
    public ApiResponse<Map<String, Object>> add(@RequestBody ServerMonitorTaskRequest request) {
        log.info("添加服务器监控任务: serverId={}", request.getServerId());

        ServerMonitorTask task = convertToEntity(request);
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
        taskScheduler.refreshTask(task.getId());

        Map<String, Object> data = new HashMap<>();
        data.put(BatchDefaults.DEFAULT_ID_COLUMN, task.getId());
        return ApiResponse.ok("添加成功", data);
    }

    @Operation(summary = "批量添加监控任务", description = "为一台服务器绑定多个模板")
    @PostMapping("/batch")
    public ApiResponse<Map<String, Object>> batchAdd(@RequestBody Map<String, Object> params) {
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

        Map<String, Object> data = new HashMap<>();
        data.put("count", successCount);
        return ApiResponse.ok("批量添加成功", data);
    }

    @Operation(summary = "更新监控任务")
    @PutMapping
    public ApiResponse<Void> update(@RequestBody ServerMonitorTaskRequest request) {
        log.info("更新服务器监控任务: id={}", request.getId());
        ServerMonitorTask task = convertToEntity(request);
        taskMapper.update(task);
        taskScheduler.refreshTask(task.getId());
        return ApiResponse.ok("更新成功", null);
    }

    @Operation(summary = "部分更新监控任务")
    @PutMapping("/{id}")
    public ApiResponse<Void> updateById(
            @Parameter(description = "任务ID") @PathVariable Long id,
            @RequestBody Map<String, Object> updates) {
        ServerMonitorTask task = taskMapper.findById(id);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }

        if (updates.containsKey("isActive")) {
            Object isActiveValue = updates.get("isActive");
            if (isActiveValue instanceof Boolean) {
                task.setIsActive((Boolean) isActiveValue ? 1 : 0);
            } else if (isActiveValue instanceof Number) {
                task.setIsActive(((Number) isActiveValue).intValue());
            }
        }
        if (updates.containsKey("cronExpression")) {
            task.setCronExpression((String) updates.get("cronExpression"));
        }

        taskMapper.update(task);
        taskScheduler.refreshTask(id);
        return ApiResponse.ok("更新成功", null);
    }

    @Operation(summary = "删除监控任务")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @Parameter(description = "任务ID") @PathVariable Long id) {
        log.info("删除服务器监控任务: id={}", id);
        taskScheduler.cancelTask(id);
        taskMapper.deleteById(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "执行监控任务", description = "立即采集一次")
    @PostMapping("/{id}/run")
    public ApiResponse<Map<String, Object>> runTask(
            @Parameter(description = "任务ID") @PathVariable Long id) {
        ServerMonitorTask task = taskMapper.findById(id);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }

        ServerAsset server = serverMapper.findById(task.getServerId());
        if (server == null) {
            throw new BusinessException(ErrorCode.SERVER_NOT_FOUND);
        }

        try {
            String script = task.getCollectScript();
            // 动态替换所有参数
            if (task.getParams() != null && !task.getParams().isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> params = objectMapper.readValue(task.getParams(), Map.class);
                for (Map.Entry<String, Object> entry : params.entrySet()) {
                    script = script.replace("${" + entry.getKey() + "}",
                            entry.getValue() != null ? entry.getValue().toString() : "");
                }
            }

            String output = sshExecutorService.executeScript(server, script);

            task.setLastRunTime(DateTimeUtils.now());
            task.setLastRunStatus("SUCCESS");
            task.setLastRunValue(output.trim());
            taskMapper.updateRunStatus(task);

            checkServerAlarm(task, server, output.trim());

            Map<String, Object> data = new HashMap<>();
            data.put("value", output.trim());
            data.put("runTime", task.getLastRunTime());
            return ApiResponse.ok("执行成功", data);
        } catch (Exception e) {
            task.setLastRunTime(DateTimeUtils.now());
            task.setLastRunStatus("FAILED");
            task.setLastRunValue(e.getMessage());
            taskMapper.updateRunStatus(task);

            throw new BusinessException(ErrorCode.SSH_EXECUTE_FAILED, e.getMessage());
        }
    }

    private void checkServerAlarm(ServerMonitorTask task, ServerAsset server, String valueStr) {
        log.debug("检查告警: taskId={}, taskName={}, value={}", task.getId(), task.getName(), valueStr);

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
                log.warn("服务器告警触发: {} [{}] - 值: {} {} {}",
                        task.getName(), server.getIp(), value, operator, thresholdVal);

                AlarmContext context = new AlarmContext();
                context.setTaskId(task.getId());
                context.setTaskType("SERVER_MONITOR");
                context.setTaskName(task.getName() + " [" + server.getName() + "]");
                context.setTriggerType("THRESHOLD");
                context.setCurrentValue(value);
                context.setThresholdValue(thresholdVal);
                context.setOperator(operator);

                if (task.getAlarmTemplateId() != null) {
                    context.setAlarmTemplateId(task.getAlarmTemplateId());
                } else if (task.getTemplateId() != null) {
                    MonitorTemplate monitorTemplate = templateMapper.findById(task.getTemplateId());
                    if (monitorTemplate != null && monitorTemplate.getAlarmTemplateId() != null) {
                        context.setAlarmTemplateId(monitorTemplate.getAlarmTemplateId());
                    }
                }

                Map<String, Object> extraParams = new HashMap<>();
                extraParams.put("serverName", server.getName());
                extraParams.put("ip", server.getIp());
                // 将任务参数也加入 extraParams，用于告警模板变量替换
                if (task.getParams() != null && !task.getParams().isEmpty()) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> taskParams = objectMapper.readValue(task.getParams(), Map.class);
                        extraParams.putAll(taskParams);
                    } catch (Exception ex) {
                        log.warn("解析任务参数失败: {}", ex.getMessage());
                    }
                }
                context.setExtraParams(extraParams);

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
            log.error("服务器告警检查失败: {}", e.getMessage(), e);
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

    /**
     * 将请求DTO转换为实体
     */
    private ServerMonitorTask convertToEntity(ServerMonitorTaskRequest request) {
        ServerMonitorTask task = new ServerMonitorTask();
        task.setId(request.getId());
        task.setName(request.getName());
        task.setServerId(request.getServerId());
        task.setTemplateId(request.getTemplateId());
        task.setCollectScript(request.getCollectScript());
        task.setParams(request.getParams());
        task.setThresholdRule(request.getThresholdRule());
        task.setCronExpression(request.getCronExpression());
        task.setAlarmChannels(request.getAlarmChannels());
        task.setAlarmTemplateId(request.getAlarmTemplateId());
        task.setIsActive(request.getIsActive());
        return task;
    }

}

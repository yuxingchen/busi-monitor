package com.monitor.backend.controller;

import com.monitor.backend.common.ApiResponse;
import com.monitor.backend.dto.task.MonitorTaskRequest;
import com.monitor.backend.entity.MonitorTask;
import com.monitor.backend.exception.BusinessException;
import com.monitor.backend.exception.ErrorCode;
import com.monitor.backend.mapper.MonitorTaskMapper;
import com.monitor.backend.service.DynamicSqlExecutor;
import com.monitor.backend.service.TaskMonitoringService;
import com.monitor.backend.util.DateTimeUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 监控任务控制器
 *
 * @author monitor-system
 */
@Tag(name = "监控任务", description = "监控任务的增删改查和执行")
@RestController
@RequestMapping("/api/task")
public class TaskController {

    private static final Logger log = LoggerFactory.getLogger(TaskController.class);

    private final MonitorTaskMapper taskMapper;
    private final TaskMonitoringService monitoringService;
    private final DynamicSqlExecutor sqlExecutor;

    public TaskController(MonitorTaskMapper taskMapper, TaskMonitoringService monitoringService,
            DynamicSqlExecutor sqlExecutor) {
        this.taskMapper = taskMapper;
        this.monitoringService = monitoringService;
        this.sqlExecutor = sqlExecutor;
    }

    @Operation(summary = "获取所有任务")
    @GetMapping
    public ApiResponse<List<MonitorTask>> list() {
        return ApiResponse.ok(taskMapper.findAll());
    }

    @Operation(summary = "获取任务详情")
    @GetMapping("/{id}")
    public ApiResponse<MonitorTask> get(
            @Parameter(description = "任务ID") @PathVariable Long id) {
        MonitorTask task = taskMapper.findById(id);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }
        return ApiResponse.ok(task);
    }

    @Operation(summary = "添加任务")
    @PostMapping
    public ApiResponse<MonitorTask> add(@RequestBody MonitorTaskRequest request) {
        log.info("添加监控任务: name={}", request.getName());
        MonitorTask task = convertToEntity(request);
        task.setCreateTime(DateTimeUtils.now());
        task.setUpdateTime(DateTimeUtils.now());
        taskMapper.insert(task);
        monitoringService.refreshTask(task.getId());
        return ApiResponse.ok("添加成功", task);
    }

    @Operation(summary = "更新任务")
    @PutMapping
    public ApiResponse<Void> update(@RequestBody MonitorTaskRequest request) {
        log.info("更新监控任务: id={}", request.getId());
        MonitorTask task = convertToEntity(request);
        taskMapper.update(task);
        monitoringService.refreshTask(task.getId());
        return ApiResponse.ok("更新成功", null);
    }

    @Operation(summary = "删除任务")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @Parameter(description = "任务ID") @PathVariable Long id) {
        log.info("删除监控任务: id={}", id);
        taskMapper.deleteById(id);
        monitoringService.cancelTask(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "测试SQL", description = "测试执行SQL语句")
    @PostMapping("/test-sql")
    public ApiResponse<Object> testSql(@RequestBody Map<String, Object> body) {
        Long datasourceId = Long.valueOf(body.get("datasourceId").toString());
        String sql = (String) body.get("sql");
        try {
            Object result = sqlExecutor.executeQuery(datasourceId, sql);
            return ApiResponse.ok(result);
        } catch (Exception e) {
            log.warn("SQL测试失败: {}", e.getMessage());
            return ApiResponse.fail(ErrorCode.TASK_EXECUTE_FAILED, "执行失败: " + e.getMessage());
        }
    }

    @Operation(summary = "执行任务", description = "手动执行监控任务")
    @PostMapping("/{id}/execute")
    public ApiResponse<Void> executeTask(
            @Parameter(description = "任务ID") @PathVariable Long id) {
        MonitorTask task = taskMapper.findById(id);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }
        try {
            log.info("手动执行任务: id={}", id);
            monitoringService.executeTask(task);
            return ApiResponse.ok("任务执行成功", null);
        } catch (Exception e) {
            log.error("任务执行失败: id={}", id, e);
            throw new BusinessException(ErrorCode.TASK_EXECUTE_FAILED, "执行失败: " + e.getMessage());
        }
    }

    /**
     * 将请求DTO转换为实体
     */
    private MonitorTask convertToEntity(MonitorTaskRequest request) {
        MonitorTask task = new MonitorTask();
        task.setId(request.getId());
        task.setName(request.getName());
        task.setDatasourceId(request.getDatasourceId());
        task.setSqlScript(request.getSqlScript());
        task.setResultType(request.getResultType());
        task.setChartConfig(request.getChartConfig());
        task.setAlarmConfig(request.getAlarmConfig());
        task.setIsStoreData(request.getIsStoreData());
        task.setIsActive(request.getIsActive());
        task.setCronExpression(request.getCronExpression());
        return task;
    }
}

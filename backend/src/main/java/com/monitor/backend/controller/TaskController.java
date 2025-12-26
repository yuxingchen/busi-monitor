package com.monitor.backend.controller;

import com.monitor.backend.entity.MonitorTask;
import com.monitor.backend.mapper.MonitorTaskMapper;
import com.monitor.backend.service.DynamicSqlExecutor;
import com.monitor.backend.service.TaskMonitoringService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/task")
@CrossOrigin
public class TaskController {

    private final MonitorTaskMapper taskMapper;
    private final TaskMonitoringService monitoringService;
    private final DynamicSqlExecutor sqlExecutor;

    public TaskController(MonitorTaskMapper taskMapper, TaskMonitoringService monitoringService,
            DynamicSqlExecutor sqlExecutor) {
        this.taskMapper = taskMapper;
        this.monitoringService = monitoringService;
        this.sqlExecutor = sqlExecutor;
    }

    @GetMapping
    public List<MonitorTask> list() {
        return taskMapper.findAll();
    }

    @GetMapping("/{id}")
    public MonitorTask get(@PathVariable Long id) {
        return taskMapper.findById(id);
    }

    @PostMapping
    public String add(@RequestBody MonitorTask task) {
        task.setCreateTime(java.time.LocalDateTime.now());
        task.setUpdateTime(java.time.LocalDateTime.now());
        taskMapper.insert(task);
        monitoringService.refreshTask(task.getId());
        return "success";
    }

    @PutMapping
    public String update(@RequestBody MonitorTask task) {
        task.setUpdateTime(java.time.LocalDateTime.now());
        taskMapper.update(task);
        monitoringService.refreshTask(task.getId());
        return "success";
    }

    @DeleteMapping("/{id}")
    public String delete(@PathVariable Long id) {
        taskMapper.deleteById(id);
        monitoringService.cancelTask(id);
        return "success";
    }

    @PostMapping("/test-sql")
    public Object testSql(@RequestBody Map<String, Object> body) {
        Long datasourceId = Long.valueOf(body.get("datasourceId").toString());
        String sql = (String) body.get("sql");
        // For testing, we just always return list map
        try {
            return sqlExecutor.executeQuery(datasourceId, sql);
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * 手动执行任务
     */
    @PostMapping("/{id}/execute")
    public Map<String, Object> executeTask(@PathVariable Long id) {
        MonitorTask task = taskMapper.findById(id);
        if (task == null) {
            return Map.of("success", false, "message", "任务不存在");
        }
        try {
            monitoringService.executeTask(task);
            return Map.of("success", true, "message", "任务执行成功");
        } catch (Exception e) {
            return Map.of("success", false, "message", "执行失败: " + e.getMessage());
        }
    }
}

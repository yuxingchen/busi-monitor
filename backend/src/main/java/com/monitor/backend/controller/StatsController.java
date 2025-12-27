package com.monitor.backend.controller;

import com.monitor.backend.common.ApiResponse;
import com.monitor.backend.entity.MonitorRecord;
import com.monitor.backend.mapper.MonitorRecordMapper;
import com.monitor.backend.service.DynamicTableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 统计数据控制器
 *
 * @author monitor-system
 */
@Tag(name = "统计数据", description = "监控记录和统计数据查询")
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final MonitorRecordMapper recordMapper;
    private final DynamicTableService dynamicTableService;

    public StatsController(MonitorRecordMapper recordMapper, DynamicTableService dynamicTableService) {
        this.recordMapper = recordMapper;
        this.dynamicTableService = dynamicTableService;
    }

    @Operation(summary = "获取任务最近的记录")
    @GetMapping("/{taskId}")
    public ApiResponse<List<MonitorRecord>> getRecentStats(
            @Parameter(description = "任务ID") @PathVariable Long taskId,
            @Parameter(description = "条数限制") @RequestParam(defaultValue = "10") int limit) {
        
        List<MonitorRecord> records = recordMapper.findRecentByTaskId(taskId, limit);

        // 处理大数据集：从物理表获取数据
        if (!records.isEmpty()) {
            MonitorRecord latest = records.get(0);
            if (latest.getResultJson() != null && latest.getResultJson().contains("stored in physical table")) {
                String batchId = String.valueOf(latest.getId());
                List<Map<String, Object>> dynamicData = dynamicTableService.queryByBatchId(taskId, batchId);

                if (!dynamicData.isEmpty()) {
                    try {
                        latest.setResultJson(
                                new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(dynamicData));
                    } catch (Exception e) {
                        // 保持原样
                    }
                }
            }
        }

        return ApiResponse.ok(records);
    }

    @Operation(summary = "获取任务动态表的可选字段列表")
    @GetMapping("/task/{taskId}/columns")
    public ApiResponse<List<String>> getTaskColumns(
            @Parameter(description = "任务ID") @PathVariable Long taskId) {
        return ApiResponse.ok(dynamicTableService.getTableColumns(taskId));
    }

    @Operation(summary = "获取分组聚合数据")
    @GetMapping("/task/{taskId}/grouped-data")
    public ApiResponse<List<Map<String, Object>>> getGroupedData(
            @Parameter(description = "任务ID") @PathVariable Long taskId,
            @Parameter(description = "分组字段") @RequestParam String groupBy,
            @Parameter(description = "数值字段") @RequestParam String valueField,
            @Parameter(description = "聚合方式") @RequestParam(defaultValue = "SUM") String aggMethod) {
        return ApiResponse.ok(dynamicTableService.queryGroupedByTime(taskId, groupBy, valueField, aggMethod));
    }

    @Operation(summary = "获取透视表数据", description = "根据任务配置自动判断数据来源")
    @GetMapping("/task/{taskId}/pivot-data")
    public ApiResponse<List<Map<String, Object>>> getPivotData(
            @Parameter(description = "任务ID") @PathVariable Long taskId,
            @Parameter(description = "查询限制") @RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(dynamicTableService.queryPivotData(taskId, limit));
    }
}

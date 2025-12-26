package com.monitor.backend.controller;

import com.monitor.backend.entity.MonitorRecord;
import com.monitor.backend.mapper.MonitorRecordMapper;
import com.monitor.backend.service.DynamicTableService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stats")
@CrossOrigin
public class StatsController {

    private final MonitorRecordMapper recordMapper;
    private final DynamicTableService dynamicTableService;

    public StatsController(MonitorRecordMapper recordMapper, DynamicTableService dynamicTableService) {
        this.recordMapper = recordMapper;
        this.dynamicTableService = dynamicTableService;
    }

    @GetMapping("/{taskId}")
    public List<MonitorRecord> getRecentStats(@PathVariable Long taskId, @RequestParam(defaultValue = "10") int limit) {
        List<MonitorRecord> records = recordMapper.findRecentByTaskId(taskId, limit);

        // 处理大数据集：从物理表获取数据
        if (!records.isEmpty()) {
            MonitorRecord latest = records.get(0);
            if (latest.getResultJson() != null && latest.getResultJson().contains("stored in physical table")) {
                // 使用 record.id 作为 batch_id 查询动态表
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

        return records;
    }

    /**
     * 获取任务动态表的可选字段列表
     */
    @GetMapping("/task/{taskId}/columns")
    public List<String> getTaskColumns(@PathVariable Long taskId) {
        return dynamicTableService.getTableColumns(taskId);
    }

    /**
     * 获取分组聚合数据
     * 
     * @param taskId     任务ID
     * @param groupBy    分组字段
     * @param valueField 数值字段
     * @param aggMethod  聚合方式 (SUM/COUNT/AVG)
     */
    @GetMapping("/task/{taskId}/grouped-data")
    public List<Map<String, Object>> getGroupedData(
            @PathVariable Long taskId,
            @RequestParam String groupBy,
            @RequestParam String valueField,
            @RequestParam(defaultValue = "SUM") String aggMethod) {
        return dynamicTableService.queryGroupedByTime(taskId, groupBy, valueField, aggMethod);
    }

    /**
     * 获取透视表数据
     * 根据任务配置的sourceType自动判断数据来源（SQL任务或工作流）
     * 
     * @param taskId 任务ID
     * @param limit 查询限制（可选，null时使用任务配置的queryLimit，配置也没有则不限制）
     */
    @GetMapping("/task/{taskId}/pivot-data")
    public List<Map<String, Object>> getPivotData(
            @PathVariable Long taskId,
            @RequestParam(required = false) Integer limit) {
        return dynamicTableService.queryPivotData(taskId, limit);
    }
}

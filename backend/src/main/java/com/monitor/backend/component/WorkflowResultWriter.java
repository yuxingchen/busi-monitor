package com.monitor.backend.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitor.backend.constant.BatchDefaults;
import com.monitor.backend.constant.SystemFields;
import com.monitor.backend.entity.Workflow;
import com.monitor.backend.entity.WorkflowStep;
import com.monitor.backend.mapper.WorkflowMapper;
import com.monitor.backend.service.DynamicTableService;
import com.monitor.backend.service.IndexFieldCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 工作流结果表写入器
 * <p>
 * 负责将工作流执行结果写入动态结果表，并创建必要的索引。
 * 支持手动配置索引字段和自动索引检测两种模式。
 * </p>
 */
@Component
public class WorkflowResultWriter {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowResultWriter.class);

    private final DynamicTableService tableService;
    private final IndexFieldCollector indexFieldCollector;
    private final WorkflowMapper workflowMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public WorkflowResultWriter(DynamicTableService tableService, 
                                 IndexFieldCollector indexFieldCollector,
                                 WorkflowMapper workflowMapper) {
        this.tableService = tableService;
        this.indexFieldCollector = indexFieldCollector;
        this.workflowMapper = workflowMapper;
    }

    /**
     * 将结果写入结果表
     * 
     * @param data 要写入的数据列表
     * @param tableName 目标表名
     * @param executionId 执行ID
     */
    public void writeToResultTable(List<Map<String, Object>> data, String tableName, Long executionId) {
        if (data == null || data.isEmpty()) {
            logger.warn("writeToResultTable: 没有数据需要写入");
            return;
        }
        
        logger.info("准备写入结果表: tableName={}, count={}", tableName, data.size());
        
        try {
            tableService.saveWorkflowResult(tableName, data, executionId);
            logger.info("已保存 {} 行数据到结果表: {}", data.size(), tableName);
        } catch (Exception e) {
            logger.error("写入结果表失败: tableName={}, error={}", tableName, e.getMessage(), e);
            throw new RuntimeException("写入结果表失败: " + e.getMessage(), e);
        }
    }

    /**
     * 为结果表创建索引
     * <p>
     * 索引创建策略：
     * <ol>
     * <li><b>手动模式优先</b>：如果用户配置了 indexFields，使用用户指定的字段</li>
     * <li><b>自动模式</b>：如果 indexFields 为空，通过分析 SQL 自动检测需要索引的字段</li>
     * <li><b>固定索引</b>：execution_id 和 execution_time 始终创建索引</li>
     * </ol>
     * </p>
     * 
     * @param workflow 工作流配置
     * @param steps 工作流步骤列表
     * @param lastResult 最终执行结果
     * @param outputTable 输出表名
     */
    public void createResultTableIndexes(Workflow workflow, List<WorkflowStep> steps,
            List<Map<String, Object>> lastResult, String outputTable) {
        // 固定字段索引（始终创建）
        List<String> fixedIndexFields = SystemFields.FIXED_INDEX_FIELDS;

        // 确定业务字段索引
        List<String> businessIndexFields;
        List<String> manualIndexFields = parseIndexFields(workflow.getIndexFields());
        boolean wasAutoDetected = false;

        if (!manualIndexFields.isEmpty()) {
            businessIndexFields = manualIndexFields;
            logger.info("使用手动配置的索引字段: {}", businessIndexFields);
        } else {
            Set<String> resultFields = lastResult.isEmpty() ? Set.of() : lastResult.get(0).keySet();
            businessIndexFields = indexFieldCollector.collectIndexFields(steps, resultFields);
            wasAutoDetected = true;
            logger.info("自动检测的索引字段: {}", businessIndexFields);
        }

        // 合并固定字段和业务字段
        Set<String> allIndexFields = new HashSet<>(fixedIndexFields);
        allIndexFields.addAll(businessIndexFields);

        // 创建索引
        tableService.ensureIndexes(outputTable, new ArrayList<>(allIndexFields));
        logger.info("已创建索引: {} (共{}个)", allIndexFields, allIndexFields.size());

        // 自动检测模式下回写索引字段配置
        if (wasAutoDetected && !businessIndexFields.isEmpty()) {
            try {
                String indexFieldsJson = objectMapper.writeValueAsString(businessIndexFields);
                workflowMapper.updateIndexFields(workflow.getId(), indexFieldsJson);
                logger.info("已回写索引字段配置: {}", indexFieldsJson);
            } catch (Exception e) {
                logger.warn("回写索引字段配置失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 获取工作流输出表名
     */
    public String getOutputTableName(Workflow workflow) {
        if (workflow.getOutputTable() != null && !workflow.getOutputTable().isEmpty()) {
            return workflow.getOutputTable();
        }
        return DynamicTableService.getWorkflowResultTableName(workflow.getId());
    }

    /**
     * 解析索引字段配置
     */
    @SuppressWarnings("unchecked")
    private List<String> parseIndexFields(String indexFieldsConfig) {
        if (indexFieldsConfig == null || indexFieldsConfig.trim().isEmpty()) {
            return List.of();
        }

        String trimmed = indexFieldsConfig.trim();

        // 尝试解析JSON格式
        if (trimmed.startsWith("[")) {
            try {
                return objectMapper.readValue(trimmed, List.class);
            } catch (Exception e) {
                logger.warn("解析JSON格式索引字段失败: {}", indexFieldsConfig);
            }
        }

        // 逗号分隔格式
        String[] parts = trimmed.split(BatchDefaults.DEFAULT_SEPARATOR);
        List<String> fields = new ArrayList<>();
        for (String part : parts) {
            String field = part.trim();
            if (!field.isEmpty()) {
                fields.add(field);
            }
        }
        return fields;
    }
}

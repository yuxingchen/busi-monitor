package com.monitor.backend.service;

import com.monitor.backend.component.WorkflowSqlParser;
import com.monitor.backend.entity.WorkflowStep;
import com.monitor.backend.component.WorkflowSqlParser.ParseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 索引字段收集器
 * <p>
 * 负责分析工作流所有SQL节点，自动收集需要创建索引的字段。
 * </p>
 * 
 * <h3>核心功能：</h3>
 * <ul>
 *   <li>遍历工作流所有节点，解析SQL提取索引候选字段</li>
 *   <li>追踪字段重命名（通过 SELECT ... AS ... 语法）</li>
 *   <li>与最终结果表字段取交集，返回需要创建索引的字段</li>
 * </ul>
 * 
 * <h3>字段血缘追踪算法：</h3>
 * <pre>
 * 1. 收集所有节点的索引候选字段 (来自 WHERE/GROUP BY/ORDER BY/JOIN ON)
 * 2. 对每个节点的 SELECT 别名映射，追踪字段重命名
 * 3. 最终需要索引的字段 = 候选字段 ∩ 结果表字段
 * </pre>
 * 
 * @author Monitor System
 * @since 1.0
 */
@Service
public class IndexFieldCollector {

    private static final Logger logger = LoggerFactory.getLogger(IndexFieldCollector.class);
    
    private final WorkflowSqlParser sqlParser;
    
    public IndexFieldCollector(WorkflowSqlParser sqlParser) {
        this.sqlParser = sqlParser;
    }

    /**
     * 分析工作流所有节点，返回需要在结果表上创建索引的字段列表
     * <p>
     * 此方法会遍历所有SQL类型的工作流步骤，提取WHERE/GROUP BY/ORDER BY/JOIN ON
     * 中出现的字段，然后追踪字段重命名，最后与结果表字段取交集。
     * </p>
     * 
     * <h4>示例场景：</h4>
     * <pre>
     * 节点1: SELECT order_id, product_name FROM orders WHERE status = 'active'
     *        → 候选字段: [status]
     * 
     * 节点2: SELECT t.order_id AS orderId FROM step1 t GROUP BY t.order_id
     *        → 候选字段: [order_id]
     *        → 字段映射: order_id → orderId
     * 
     * 结果表字段: [orderId, ...]
     * 
     * 最终需要索引的字段: [orderId] (order_id 被重命名为 orderId，且在结果表中存在)
     * </pre>
     * 
     * @param steps 工作流步骤列表（按执行顺序）
     * @param finalResultFields 最终结果表的字段集合
     * @return 需要创建索引的字段名列表（不重复）
     */
    public List<String> collectIndexFields(List<WorkflowStep> steps, Set<String> finalResultFields) {
        if (steps == null || steps.isEmpty()) {
            return List.of();
        }
        
        logger.debug("开始分析 {} 个工作流节点的索引候选字段", steps.size());
        
        // 1. 收集所有节点的索引候选字段（去重后的字段名集合）
        Set<String> allCandidateFields = new HashSet<>();
        
        // 2. 存储所有节点的字段别名映射（用于追踪重命名）
        //    fieldMappings: Map<原始字段名, 新字段名>
        Map<String, String> fieldMappings = new LinkedHashMap<>();
        
        // 3. 遍历所有SQL节点
        for (WorkflowStep step : steps) {
            if (!"SQL".equals(step.getStepType()) || step.getSqlScript() == null) {
                continue;
            }
            
            // 解析SQL
            ParseResult parseResult = sqlParser.parse(step.getSqlScript(), Collections.emptySet());
            
            // 3.1 收集索引候选字段
            Set<String> stepCandidates = parseResult.getUniqueIndexCandidateFieldNames();
            allCandidateFields.addAll(stepCandidates);
            logger.debug("节点 [{}] 提取索引候选字段: {}", step.getName(), stepCandidates);
            
            // 3.2 收集字段别名映射
            Map<String, String> aliasMap = parseResult.getFieldAliasMap();
            for (Map.Entry<String, String> entry : aliasMap.entrySet()) {
                String originalField = extractSimpleFieldName(entry.getKey());
                String aliasField = entry.getValue();
                
                // 如果原字段和别名不同，记录映射
                if (originalField != null && !originalField.equals(aliasField)) {
                    fieldMappings.put(originalField, aliasField);
                    logger.debug("检测到字段重命名: {} → {}", originalField, aliasField);
                }
            }
        }
        
        logger.debug("所有候选字段（重命名前）: {}", allCandidateFields);
        logger.debug("字段映射关系: {}", fieldMappings);
        
        // 4. 追踪字段重命名，将候选字段映射到最终名称
        Set<String> trackedFields = trackFieldLineage(allCandidateFields, fieldMappings);
        logger.debug("追踪重命名后的候选字段: {}", trackedFields);
        
        // 5. 与结果表字段取交集
        Set<String> indexFields = trackedFields.stream()
                .filter(field -> finalResultFields.contains(field))
                .collect(Collectors.toSet());
        
        logger.info("最终需要创建索引的字段: {} (结果表字段: {})", indexFields, finalResultFields);
        
        return new ArrayList<>(indexFields);
    }
    
    /**
     * 追踪字段在工作流执行过程中的重命名
     * <p>
     * 将候选索引字段通过字段映射链转换为最终名称。
     * 如果一个候选字段在映射中找到，则用映射后的名称替换。
     * </p>
     * 
     * @param candidateFields 原始候选字段集合
     * @param fieldMappings 字段映射（原名 → 新名）
     * @return 追踪重命名后的字段集合
     */
    private Set<String> trackFieldLineage(Set<String> candidateFields, Map<String, String> fieldMappings) {
        Set<String> result = new HashSet<>();
        
        for (String field : candidateFields) {
            // 查找是否有重命名映射
            String mappedField = fieldMappings.get(field);
            if (mappedField != null) {
                // 有重命名，添加映射后的字段
                result.add(mappedField);
                // 同时保留原字段（可能在其他节点仍使用原名）
                result.add(field);
            } else {
                // 无重命名，直接添加
                result.add(field);
            }
        }
        
        // 递归追踪多级重命名（如 a→b→c）
        boolean changed = true;
        while (changed) {
            changed = false;
            Set<String> newMappedFields = new HashSet<>();
            for (String field : result) {
                String mapped = fieldMappings.get(field);
                if (mapped != null && !result.contains(mapped)) {
                    newMappedFields.add(mapped);
                    changed = true;
                }
            }
            result.addAll(newMappedFields);
        }
        
        return result;
    }
    
    /**
     * 从可能带表别名的字段表达式中提取简单字段名
     * <p>
     * 例如：将 "t.order_id" 转换为 "order_id"
     * </p>
     * 
     * @param fieldExpression 字段表达式
     * @return 简单字段名
     */
    private String extractSimpleFieldName(String fieldExpression) {
        if (fieldExpression == null || fieldExpression.isEmpty()) {
            return null;
        }
        
        // 去空格
        String trimmed = fieldExpression.trim();
        
        // 如果包含点号，取最后一部分
        if (trimmed.contains(".")) {
            String[] parts = trimmed.split("\\.");
            return parts[parts.length - 1].trim();
        }
        
        return trimmed;
    }
}

package com.monitor.backend.component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 工作流内存JOIN执行器
 * <p>
 * 负责在内存中执行表连接操作，支持 LEFT/RIGHT/INNER JOIN。
 * 使用 Hash Join 算法提升大数据量的连接性能。
 * </p>
 */
@Component
public class WorkflowMemoryJoiner {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowMemoryJoiner.class);

    /**
     * 执行内存JOIN操作
     * 
     * @param leftData 左表数据
     * @param rightData 右表数据
     * @param leftKey 左表JOIN键
     * @param rightKey 右表JOIN键
     * @param joinType JOIN类型（LEFT/RIGHT/INNER）
     * @param selectFields SELECT字段列表
     * @param aliasMap 字段别名映射
     * @return JOIN结果
     */
    public List<Map<String, Object>> performJoin(
            List<Map<String, Object>> leftData,
            List<Map<String, Object>> rightData,
            String leftKey,
            String rightKey,
            String joinType,
            List<String> selectFields,
            Map<String, String> aliasMap) {
        
        logger.debug("执行内存JOIN: leftSize={}, rightSize={}, joinType={}", 
                leftData.size(), rightData.size(), joinType);
        
        // 构建右表索引（Hash Join）
        Map<Object, List<Map<String, Object>>> rightIndex = buildIndex(rightData, rightKey);
        
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (Map<String, Object> leftRow : leftData) {
            Object key = getFieldValue(leftRow, leftKey);
            List<Map<String, Object>> matchedRights = rightIndex.get(key);
            
            if (matchedRights != null && !matchedRights.isEmpty()) {
                // 有匹配，合并数据
                for (Map<String, Object> rightRow : matchedRights) {
                    Map<String, Object> merged = new LinkedHashMap<>(leftRow);
                    merged.putAll(rightRow);
                    result.add(filterFields(merged, selectFields, aliasMap));
                }
            } else if ("LEFT".equalsIgnoreCase(joinType)) {
                // LEFT JOIN: 无匹配也保留左表记录
                result.add(filterFields(new LinkedHashMap<>(leftRow), selectFields, aliasMap));
            }
            // INNER JOIN: 无匹配则不添加
        }
        
        // RIGHT JOIN 处理
        if ("RIGHT".equalsIgnoreCase(joinType)) {
            Map<Object, List<Map<String, Object>>> leftIndex = buildIndex(leftData, leftKey);
            for (Map<String, Object> rightRow : rightData) {
                Object key = getFieldValue(rightRow, rightKey);
                if (!leftIndex.containsKey(key)) {
                    result.add(filterFields(new LinkedHashMap<>(rightRow), selectFields, aliasMap));
                }
            }
        }
        
        logger.debug("内存JOIN完成: resultSize={}", result.size());
        return result;
    }
    
    /**
     * 构建索引（Hash表）提升JOIN性能
     */
    private Map<Object, List<Map<String, Object>>> buildIndex(List<Map<String, Object>> data, String keyField) {
        Map<Object, List<Map<String, Object>>> index = new HashMap<>();
        for (Map<String, Object> row : data) {
            Object key = getFieldValue(row, keyField);
            index.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }
        return index;
    }
    
    /**
     * 获取字段值（支持别名前缀）
     */
    public Object getFieldValue(Map<String, Object> row, String fieldName) {
        if (fieldName == null || row == null) {
            return null;
        }
        // 移除可能的别名前缀
        String cleanField = fieldName.contains(".") 
                ? fieldName.substring(fieldName.indexOf(".") + 1) 
                : fieldName;
        return row.get(cleanField);
    }
    
    /**
     * 根据SELECT字段过滤并应用别名
     */
    public Map<String, Object> filterFields(Map<String, Object> row, 
            List<String> selectFields, Map<String, String> aliasMap) {
        if (selectFields == null || selectFields.isEmpty() || 
            (selectFields.size() == 1 && "*".equals(selectFields.get(0)))) {
            return row;
        }
        
        Map<String, Object> filtered = new LinkedHashMap<>();
        for (String field : selectFields) {
            String cleanField = field.contains(".") 
                    ? field.substring(field.indexOf(".") + 1) 
                    : field;
            String outputKey = aliasMap != null && aliasMap.containsKey(field) 
                    ? aliasMap.get(field) 
                    : cleanField;
            if (row.containsKey(cleanField)) {
                filtered.put(outputKey, row.get(cleanField));
            }
        }
        return filtered;
    }
}

package com.monitor.backend.component;

import com.monitor.backend.constant.JoinType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * 内存JOIN执行器
 * 在Java内存中对两个数据集执行JOIN操作，支持多线程并行处理
 */
@Component
public class MemoryJoinExecutor {

    private static final Logger logger = LoggerFactory.getLogger(MemoryJoinExecutor.class);

    private static final int THREAD_POOL_SIZE = 4;
    private static final int WARN_THRESHOLD = 100000; // 10万行警告阈值

    private final ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

    /**
     * 执行内存JOIN
     * 
     * @param leftData      左表数据
     * @param rightData     右表数据
     * @param leftAlias     左表别名
     * @param rightAlias    右表别名
     * @param leftKey       左表关联字段
     * @param rightKey      右表关联字段
     * @param joinType      JOIN类型（LEFT, INNER, RIGHT）
     * @param selectFields  要保留的字段列表（null表示保留全部）
     * @param fieldAliasMap 字段别名映射（原始字段 -> 别名），用于AS别名支持
     * @return 合并后的结果集
     */
    public List<Map<String, Object>> join(
            List<Map<String, Object>> leftData,
            List<Map<String, Object>> rightData,
            String leftAlias,
            String rightAlias,
            String leftKey,
            String rightKey,
            String joinType,
            List<String> selectFields,
            Map<String, String> fieldAliasMap) {
        // 性能警告
        if (leftData.size() > WARN_THRESHOLD || rightData.size() > WARN_THRESHOLD) {
            logger.warn("Large dataset detected: left={}, right={}. Consider optimizing.",
                    leftData.size(), rightData.size());
        }

        logger.info("Starting memory JOIN: {}({}) {} JOIN {}({}) ON {}.{} = {}.{}",
                leftAlias, leftData.size(), joinType, rightAlias, rightData.size(),
                leftAlias, leftKey, rightAlias, rightKey);

        // 调试：打印实际字段名
        if (!leftData.isEmpty()) {
            logger.debug("Left table fields: {}", leftData.get(0).keySet());
        }
        if (!rightData.isEmpty()) {
            logger.debug("Right table fields: {}", rightData.get(0).keySet());
        }

        long startTime = System.currentTimeMillis();

        // 1. 构建右表索引 (O(n))
        Map<String, List<Map<String, Object>>> rightIndex = buildIndex(rightData, rightKey);

        logger.debug("Right index size: {}, sample keys: {}",
                rightIndex.size(),
                rightIndex.keySet().stream().limit(5).collect(Collectors.toList()));

        // 2. 并行处理左表数据
        List<Map<String, Object>> result;
        if (leftData.size() > 1000) {
            // 大数据量使用多线程
            result = parallelJoin(leftData, rightIndex, leftAlias, rightAlias, leftKey, rightKey, joinType);
        } else {
            // 小数据量单线程处理
            result = singleThreadJoin(leftData, rightIndex, leftAlias, rightAlias, leftKey, rightKey, joinType);
        }

        // 3. 字段过滤（如果有字段别名映射，使用它；否则使用selectFields）
        if (fieldAliasMap != null && !fieldAliasMap.isEmpty()) {
            result = filterFieldsWithAlias(result, fieldAliasMap);
        } else if (selectFields != null && !selectFields.isEmpty()) {
            result = filterFields(result, selectFields);
        }

        long elapsed = System.currentTimeMillis() - startTime;
        logger.info("Memory JOIN completed: {} rows in {}ms", result.size(), elapsed);

        return result;
    }

    /**
     * 多字段复合关联
     */
    public List<Map<String, Object>> joinMultiKey(
            List<Map<String, Object>> leftData,
            List<Map<String, Object>> rightData,
            String leftAlias,
            String rightAlias,
            List<String> leftKeys,
            List<String> rightKeys,
            String joinType,
            List<String> selectFields) {
        // 构建复合键索引
        Map<String, List<Map<String, Object>>> rightIndex = buildMultiKeyIndex(rightData, rightKeys);

        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> leftRow : leftData) {
            String leftKeyValue = buildCompositeKey(leftRow, leftKeys);
            List<Map<String, Object>> matchedRows = rightIndex.get(leftKeyValue);

            mergeRows(result, leftRow, matchedRows, leftAlias, rightAlias, joinType);
        }

        if (selectFields != null && !selectFields.isEmpty()) {
            result = filterFields(result, selectFields);
        }

        return result;
    }

    /**
     * 构建单字段索引
     */
    private Map<String, List<Map<String, Object>>> buildIndex(
            List<Map<String, Object>> data, String keyField) {
        return data.stream().collect(Collectors.groupingBy(
                row -> String.valueOf(row.get(keyField))));
    }

    /**
     * 构建多字段复合索引
     */
    private Map<String, List<Map<String, Object>>> buildMultiKeyIndex(
            List<Map<String, Object>> data, List<String> keyFields) {
        return data.stream().collect(Collectors.groupingBy(
                row -> buildCompositeKey(row, keyFields)));
    }

    /**
     * 构建复合键
     */
    private String buildCompositeKey(Map<String, Object> row, List<String> keyFields) {
        return keyFields.stream()
                .map(k -> String.valueOf(row.get(k)))
                .collect(Collectors.joining("|"));
    }

    /**
     * 智能获取字段值 - 支持多种匹配方式
     * 1. 直接匹配字段名
     * 2. 匹配 "任意别名.字段名" 形式
     */
    private Object getFieldValue(Map<String, Object> row, String fieldName) {
        // 1. 直接匹配
        if (row.containsKey(fieldName)) {
            return row.get(fieldName);
        }

        // 2. 如果字段名带有别名（如 p.chargingPolicyID），尝试直接匹配
        if (fieldName.contains(".")) {
            String baseName = fieldName.substring(fieldName.indexOf('.') + 1);
            // 尝试匹配不带任何别名的字段
            if (row.containsKey(baseName)) {
                return row.get(baseName);
            }
            // 尝试匹配任意别名的字段
            for (String key : row.keySet()) {
                if (key.endsWith("." + baseName)) {
                    return row.get(key);
                }
            }
        }

        // 3. 如果字段名不带别名，尝试匹配任意别名的字段
        for (String key : row.keySet()) {
            if (key.endsWith("." + fieldName)) {
                return row.get(key);
            }
        }

        return null;
    }

    /**
     * 单线程JOIN
     */
    private List<Map<String, Object>> singleThreadJoin(
            List<Map<String, Object>> leftData,
            Map<String, List<Map<String, Object>>> rightIndex,
            String leftAlias, String rightAlias,
            String leftKey, String rightKey, String joinType) {

        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> leftRow : leftData) {
            Object keyObj = getFieldValue(leftRow, leftKey);
            String keyValue = keyObj != null ? String.valueOf(keyObj) : "null";
            List<Map<String, Object>> matchedRows = rightIndex.get(keyValue);

            mergeRows(result, leftRow, matchedRows, leftAlias, rightAlias, joinType);
        }

        return result;
    }

    /**
     * 多线程并行JOIN
     */
    private List<Map<String, Object>> parallelJoin(
            List<Map<String, Object>> leftData,
            Map<String, List<Map<String, Object>>> rightIndex,
            String leftAlias, String rightAlias,
            String leftKey, String rightKey, String joinType) {

        int batchSize = Math.max(1, leftData.size() / THREAD_POOL_SIZE);
        List<Future<List<Map<String, Object>>>> futures = new ArrayList<>();

        for (int i = 0; i < leftData.size(); i += batchSize) {
            int start = i;
            int end = Math.min(i + batchSize, leftData.size());
            List<Map<String, Object>> batch = leftData.subList(start, end);

            futures.add(executor.submit(() -> {
                List<Map<String, Object>> batchResult = new ArrayList<>();
                for (Map<String, Object> leftRow : batch) {
                    Object keyObj = getFieldValue(leftRow, leftKey);
                    String keyValue = keyObj != null ? String.valueOf(keyObj) : "null";
                    List<Map<String, Object>> matchedRows = rightIndex.get(keyValue);
                    mergeRows(batchResult, leftRow, matchedRows, leftAlias, rightAlias, joinType);
                }
                return batchResult;
            }));
        }

        // 收集结果
        List<Map<String, Object>> result = new ArrayList<>();
        for (Future<List<Map<String, Object>>> future : futures) {
            try {
                result.addAll(future.get());
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Parallel JOIN error", e);
                throw new RuntimeException("JOIN执行失败", e);
            }
        }

        return result;
    }

    /**
     * 合并行
     */
    private void mergeRows(
            List<Map<String, Object>> result,
            Map<String, Object> leftRow,
            List<Map<String, Object>> matchedRows,
            String leftAlias, String rightAlias,
            String joinType) {

        if (matchedRows != null && !matchedRows.isEmpty()) {
            // 有匹配：左表字段加别名前缀，合并每个匹配行
            for (Map<String, Object> rightRow : matchedRows) {
                Map<String, Object> merged = new LinkedHashMap<>();

                // 添加左表字段（带别名前缀）
                for (Map.Entry<String, Object> entry : leftRow.entrySet()) {
                    merged.put(leftAlias + "." + entry.getKey(), entry.getValue());
                }

                // 添加右表字段（带别名前缀）
                for (Map.Entry<String, Object> entry : rightRow.entrySet()) {
                    merged.put(rightAlias + "." + entry.getKey(), entry.getValue());
                }

                result.add(merged);
            }
        } else if (JoinType.LEFT.matches(joinType) || JoinType.FULL.matches(joinType)) {
            // LEFT JOIN：保留左表行，右表字段为null
            Map<String, Object> merged = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : leftRow.entrySet()) {
                merged.put(leftAlias + "." + entry.getKey(), entry.getValue());
            }
            result.add(merged);
        }
        // INNER JOIN：无匹配则不添加
    }

    /**
     * 根据SELECT字段过滤结果
     */
    private List<Map<String, Object>> filterFields(
            List<Map<String, Object>> data, List<String> selectFields) {

        return data.stream().map(row -> {
            Map<String, Object> filtered = new LinkedHashMap<>();
            for (String selectField : selectFields) {
                Object value = findFieldValue(row, selectField);
                if (value != null) {
                    filtered.put(selectField, value);
                } else {
                    // 如果找不到，也要保留字段但值为null
                    filtered.put(selectField, null);
                }
            }
            return filtered;
        }).collect(Collectors.toList());
    }

    /**
     * 智能查找字段值 - 支持多层别名匹配
     * 例如：查找 p.count 时，可能匹配到：
     * - p.count (直接匹配)
     * - p.t1.orderCount (嵌套别名 + 基础字段名)
     * - t1.orderCount (只有内层别名)
     * - orderCount (无别名基础字段)
     */
    private Object findFieldValue(Map<String, Object> row, String selectField) {
        // 1. 直接匹配
        if (row.containsKey(selectField)) {
            return row.get(selectField);
        }

        // 提取基础字段名（去掉所有别名前缀）
        String baseName = selectField.contains(".")
                ? selectField.substring(selectField.lastIndexOf('.') + 1)
                : selectField;

        // 2. 尝试直接用基础字段名匹配
        if (row.containsKey(baseName)) {
            return row.get(baseName);
        }

        // 3. 遍历所有字段，查找以基础字段名结尾的
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String key = entry.getKey();
            String keyBaseName = key.contains(".")
                    ? key.substring(key.lastIndexOf('.') + 1)
                    : key;

            if (keyBaseName.equalsIgnoreCase(baseName)) {
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * 根据字段别名映射过滤结果
     * 
     * @param data          数据列表
     * @param fieldAliasMap 原始字段 -> 别名映射
     */
    private List<Map<String, Object>> filterFieldsWithAlias(
            List<Map<String, Object>> data, Map<String, String> fieldAliasMap) {

        // 调试：打印字段映射和第一行数据的字段
        if (!data.isEmpty()) {
            logger.debug("Field alias map: {}", fieldAliasMap);
            logger.debug("First row keys: {}", data.get(0).keySet());
        }

        return data.stream().map(row -> {
            Map<String, Object> filtered = new LinkedHashMap<>();
            for (Map.Entry<String, String> fieldEntry : fieldAliasMap.entrySet()) {
                String originalField = fieldEntry.getKey(); // 原始字段，如 p.orderID 或 c.baseFee/100
                String aliasField = fieldEntry.getValue(); // 别名，如 order 或 productPrice

                Object value;
                // 检查是否是表达式（包含运算符）
                if (isExpression(originalField)) {
                    value = evaluateExpression(row, originalField);
                } else {
                    // 简单字段，直接查找
                    value = findFieldValue(row, originalField);
                }
                // 使用别名作为输出key
                filtered.put(aliasField, value);
            }
            return filtered;
        }).collect(Collectors.toList());
    }

    /**
     * 检查字段是否是表达式（包含运算符）
     */
    private boolean isExpression(String field) {
        // 检查是否包含算术运算符（排除字段名中的点号）
        return field.matches(".*[\\+\\-\\*/].*");
    }

    /**
     * 计算表达式的值
     * 支持简单的二元运算：field + N, field - N, field * N, field / N
     * 例如：c.baseFee/100, t.count*2, a.price+10
     */
    private Object evaluateExpression(Map<String, Object> row, String expression) {
        // 解析运算符
        char operator = 0;
        int operatorIndex = -1;

        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (c == '+' || c == '-' || c == '*' || c == '/') {
                operator = c;
                operatorIndex = i;
                break;
            }
        }

        if (operatorIndex <= 0) {
            logger.warn("无法解析表达式: {}", expression);
            return null;
        }

        String leftPart = expression.substring(0, operatorIndex).trim();
        String rightPart = expression.substring(operatorIndex + 1).trim();

        // 获取左操作数（字段值）
        Object leftValue = findFieldValue(row, leftPart);
        if (leftValue == null) {
            logger.debug("表达式左操作数字段 {} 未找到", leftPart);
            return null;
        }

        // 尝试将左值转为数字
        double leftNum;
        try {
            leftNum = Double.parseDouble(String.valueOf(leftValue));
        } catch (NumberFormatException e) {
            logger.warn("无法将 {} 转为数字: {}", leftPart, leftValue);
            return null;
        }

        // 获取右操作数（可能是数字常量或字段）
        double rightNum;
        try {
            // 首先尝试解析为数字常量
            rightNum = Double.parseDouble(rightPart);
        } catch (NumberFormatException e) {
            // 不是数字，可能是字段引用
            Object rightValue = findFieldValue(row, rightPart);
            if (rightValue == null) {
                logger.debug("表达式右操作数字段 {} 未找到", rightPart);
                return null;
            }
            try {
                rightNum = Double.parseDouble(String.valueOf(rightValue));
            } catch (NumberFormatException e2) {
                logger.warn("无法将 {} 转为数字: {}", rightPart, rightValue);
                return null;
            }
        }

        // 执行计算
        double result;
        switch (operator) {
            case '+':
                result = leftNum + rightNum;
                break;
            case '-':
                result = leftNum - rightNum;
                break;
            case '*':
                result = leftNum * rightNum;
                break;
            case '/':
                if (rightNum == 0) {
                    logger.warn("除零错误: {} / 0", leftPart);
                    return null;
                }
                result = leftNum / rightNum;
                break;
            default:
                return null;
        }

        logger.debug("表达式计算: {} {} {} = {}", leftNum, operator, rightNum, result);
        return result;
    }
}

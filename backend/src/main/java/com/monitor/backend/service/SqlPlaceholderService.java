package com.monitor.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitor.backend.constant.BatchDefaults;
import com.monitor.backend.mapper.MonitorRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * SQL 占位符替换服务
 * 支持的占位符：
 * - ${now} - 当前时间（精确到秒）
 * - ${today} - 今天日期
 * - ${yesterday} - 昨天日期
 * - ${lastRunTime} - 上次执行时间（支持 taskId 或 workflowId）
 * - ${todayStart} - 今天 00:00:00
 * - ${todayEnd} - 今天 23:59:59
 * 
 * 工作流变量占位符：
 * - ${step1.field} - 步骤结果中某字段（取第一行）
 * - ${step1[0].field} - 指定行的字段
 * - ${step1.*.field} - 所有行的某字段，生成 IN 列表
 * - ${step1.__COUNT__} - 结果行数
 * - ${step1.__JSON__} - 整个结果集的 JSON
 * 
 * 常量和环境变量占位符：
 * - ${const.XXX} - 常量值（来自 CONSTANT 步骤或上下文）
 * - ${env.XXX} - 系统环境变量
 * 
 * 循环占位符（用于 LOOP 步骤内）：
 * - ${loop.index} - 当前循环索引（从0开始）
 * - ${loop.value} - 当前循环值
 * - ${loop.total} - 循环总次数
 */
@Service
public class SqlPlaceholderService {

    private static final Logger logger = LoggerFactory.getLogger(SqlPlaceholderService.class);

    private final MonitorRecordMapper recordMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");
    // 工作流变量模式: ${stepName.field} 或 ${stepName[index].field} 或 ${stepName.*.field}
    private static final Pattern WORKFLOW_VAR_PATTERN = Pattern
            .compile("^([a-zA-Z_][a-zA-Z0-9_]*)(?:\\[(\\d+|\\*)])?(?:\\.(.+))?$");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // 默认的首次执行时间（任务从来没有执行过时使用）
    private static final String DEFAULT_LAST_RUN_TIME = "1970-01-01 00:00:00";

    public SqlPlaceholderService(MonitorRecordMapper recordMapper) {
        this.recordMapper = recordMapper;
    }

    /**
     * 解析并替换 SQL 中的所有占位符
     * 
     * @param sql    原始 SQL
     * @param taskId 任务ID（用于获取上次执行时间）
     * @return 替换后的 SQL
     */
    public String resolvePlaceholders(String sql, Long taskId) {
        if (sql == null || sql.isEmpty()) {
            return sql;
        }

        // 构建占位符值映射
        Map<String, String> placeholderValues = buildPlaceholderValues(taskId);

        // 替换所有占位符
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(sql);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String placeholder = matcher.group(1);
            String value = placeholderValues.getOrDefault(placeholder, matcher.group(0));
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * 构建所有占位符的值映射
     */
    private Map<String, String> buildPlaceholderValues(Long taskId) {
        Map<String, String> values = new HashMap<>();

        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        // 当前时间
        values.put("now", now.format(DATETIME_FORMATTER));

        // 今天日期
        values.put("today", today.format(DATE_FORMATTER));

        // 昨天日期
        values.put("yesterday", yesterday.format(DATE_FORMATTER));

        // 今天 00:00:00
        values.put("todayStart", today.atStartOfDay().format(DATETIME_FORMATTER));

        // 今天 23:59:59
        values.put("todayEnd", today.atTime(23, 59, 59).format(DATETIME_FORMATTER));

        // 上次执行时间
        String lastRunTime = getLastRunTime(taskId);
        values.put("lastRunTime", lastRunTime);

        return values;
    }

    /**
     * 获取任务的上次成功执行时间
     */
    private String getLastRunTime(Long taskId) {
        if (taskId == null) {
            return DEFAULT_LAST_RUN_TIME;
        }

        try {
            LocalDateTime lastTime = recordMapper.findLastSuccessTime(taskId);
            if (lastTime != null) {
                return lastTime.format(DATETIME_FORMATTER);
            }
        } catch (Exception e) {
            // 忽略异常，返回默认值
        }

        return DEFAULT_LAST_RUN_TIME;
    }

    /**
     * 解析工作流变量占位符
     * 支持的格式:
     * - ${stepName.field} - 取第一行的指定字段
     * - ${stepName[0].field} - 取指定行的字段
     * - ${stepName.*.field} - 取所有行的某字段，生成 IN 列表 ('a','b','c')
     * - ${stepName.__COUNT__} - 结果行数
     * - ${stepName.__JSON__} - 整个结果集的 JSON
     * 
     * @param sql     原始 SQL
     * @param context 工作流上下文，key=变量名，value=步骤结果(List<Map<String, Object>>)
     * @return 替换后的 SQL
     */
    @SuppressWarnings("unchecked")
    public String resolveWorkflowPlaceholders(String sql, Map<String, Object> context) {
        if (sql == null || sql.isEmpty() || context == null) {
            return sql;
        }

        // 先处理基础时间占位符
        sql = resolvePlaceholders(sql, null);

        // 处理工作流变量
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(sql);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String placeholder = matcher.group(1); // e.g., "step1.field" or "step1[0].field"
            String replacement = null;

            // 0. 首先检查 context 中是否直接包含该占位符（支持任意命名空间的常量）
            // 例如：${orderConstant.productIds} -> context.get("orderConstant.productIds")
            if (context.containsKey(placeholder)) {
                Object value = context.get(placeholder);
                if (value != null) {
                    replacement = value.toString();
                    logger.debug("解析常量占位符: {} = {}", placeholder, replacement);
                }
            }
            // 1. 检查环境变量占位符 ${env.XXX}
            else if (placeholder.startsWith("env.")) {
                String envVar = placeholder.substring(4);
                replacement = System.getenv(envVar);
                if (replacement == null) {
                    replacement = System.getProperty(envVar, "");
                }
                logger.debug("解析环境变量占位符: {} = {}", placeholder, replacement);
            }
            // 2. 检查循环占位符 ${loop.xxx}
            else if (placeholder.startsWith("loop.")) {
                Object loopValue = context.get(placeholder);
                if (loopValue != null) {
                    replacement = loopValue.toString();
                } else {
                    logger.warn("循环变量未找到: {}", placeholder);
                    replacement = "";
                }
                logger.debug("解析循环占位符: {} = {}", placeholder, replacement);
            }
            // 3. 其他工作流变量（如 ${step1.field} 格式）
            else {
                replacement = resolveWorkflowVariable(placeholder, context);
            }

            if (replacement != null) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
            } else {
                // 保留原样
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * 解析单个工作流变量
     */
    @SuppressWarnings("unchecked")
    private String resolveWorkflowVariable(String placeholder, Map<String, Object> context) {
        Matcher varMatcher = WORKFLOW_VAR_PATTERN.matcher(placeholder);
        if (!varMatcher.matches()) {
            return null; // 不是工作流变量格式
        }

        String stepName = varMatcher.group(1); // 步骤变量名
        String indexStr = varMatcher.group(2); // 可选: 数字索引或 *
        String fieldName = varMatcher.group(3); // 可选: 字段名

        Object stepResult = context.get(stepName);
        if (stepResult == null) {
            logger.warn("Workflow variable not found: {}", stepName);
            return null;
        }

        // 步骤结果应该是 List<Map<String, Object>>
        if (!(stepResult instanceof List)) {
            logger.warn("Workflow variable {} is not a List", stepName);
            return null;
        }

        List<Map<String, Object>> rows = (List<Map<String, Object>>) stepResult;

        // 处理特殊字段
        if ("__COUNT__".equals(fieldName)) {
            return String.valueOf(rows.size());
        }
        if ("__JSON__".equals(fieldName) || (fieldName == null && indexStr == null)) {
            try {
                return objectMapper.writeValueAsString(rows);
            } catch (Exception e) {
                logger.error("Failed to serialize workflow result", e);
                return "[]";
            }
        }

        // 处理通配符 * - 提取所有行的某字段
        if (BatchDefaults.WILDCARD.equals(indexStr) && fieldName != null) {
            return rows.stream()
                    .map(row -> row.get(fieldName))
                    .filter(v -> v != null)
                    .map(v -> "'" + v.toString().replace("'", "''") + "'")
                    .collect(Collectors.joining(", "));
        }

        // 处理索引访问
        int index = 0;
        if (indexStr != null && !indexStr.equals(BatchDefaults.WILDCARD)) {
            try {
                index = Integer.parseInt(indexStr);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        if (index >= rows.size()) {
            logger.warn("Index {} out of bounds for workflow variable {}", index, stepName);
            return null;
        }

        Map<String, Object> row = rows.get(index);

        // 如果没有指定字段名，返回整行 JSON
        if (fieldName == null) {
            try {
                return objectMapper.writeValueAsString(row);
            } catch (Exception e) {
                return "{}";
            }
        }

        // 返回指定字段的值
        Object value = row.get(fieldName);
        if (value == null) {
            return "NULL";
        }

        // 不自动添加引号，由用户在SQL中自行决定
        // 只对字符串中的单引号进行转义（防止SQL注入）
        if (value instanceof String) {
            return value.toString().replace("'", "''");
        }

        return value.toString();
    }
}

package com.monitor.backend.batch;

import com.monitor.backend.constant.BatchDefaults;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL批处理支持性分析器
 * 分析SQL语句是否适合分区批处理
 */
@Component
public class SqlBatchAnalyzer {

    /**
     * 分析结果
     */
    public static class AnalysisResult {
        private final boolean supported;
        private final SqlType sqlType;
        private final String reason;
        private final List<String> orderByFields;

        public AnalysisResult(boolean supported, SqlType sqlType, String reason) {
            this.supported = supported;
            this.sqlType = sqlType;
            this.reason = reason;
            this.orderByFields = new ArrayList<>();
        }

        public boolean isSupported() { return supported; }
        public SqlType getSqlType() { return sqlType; }
        public String getReason() { return reason; }
        public List<String> getOrderByFields() { return orderByFields; }
        public void addOrderByField(String field) { orderByFields.add(field); }
    }

    /**
     * SQL类型枚举
     */
    public enum SqlType {
        SIMPLE_SELECT,      // 简单查询，完全支持
        SELECT_WITH_ORDER,  // 带ORDER BY，需要归并排序
        GROUP_BY,           // GROUP BY聚合，不支持
        DISTINCT,           // DISTINCT去重，不支持
        WINDOW_FUNCTION,    // 窗口函数，不支持
        HAVING,             // HAVING子句，不支持
        SUBQUERY_AGG,       // 子查询聚合，不支持
        UNSUPPORTED         // 其他不支持
    }

    // 正则模式
    private static final Pattern GROUP_BY_PATTERN = Pattern.compile(
            "\\bGROUP\\s+BY\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DISTINCT_PATTERN = Pattern.compile(
            "\\bSELECT\\s+DISTINCT\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern HAVING_PATTERN = Pattern.compile(
            "\\bHAVING\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern WINDOW_FUNCTION_PATTERN = Pattern.compile(
            "\\b(ROW_NUMBER|RANK|DENSE_RANK|NTILE|LAG|LEAD|FIRST_VALUE|LAST_VALUE|SUM|COUNT|AVG|MIN|MAX)\\s*\\(.*\\)\\s*OVER\\s*\\(",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ORDER_BY_PATTERN = Pattern.compile(
            "\\bORDER\\s+BY\\s+(.+?)(?:\\bLIMIT\\b|\\bOFFSET\\b|$)",
            Pattern.CASE_INSENSITIVE);

    /**
     * 分析SQL是否支持批处理
     * @param sql SQL语句
     * @return 分析结果
     */
    public AnalysisResult analyze(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return new AnalysisResult(false, SqlType.UNSUPPORTED, "SQL语句为空");
        }

        String normalizedSql = sql.trim();

        // 检查DISTINCT
        if (DISTINCT_PATTERN.matcher(normalizedSql).find()) {
            return new AnalysisResult(false, SqlType.DISTINCT,
                    "DISTINCT查询不支持分区批处理，分区去重结果不一致");
        }

        // 检查窗口函数
        if (WINDOW_FUNCTION_PATTERN.matcher(normalizedSql).find()) {
            return new AnalysisResult(false, SqlType.WINDOW_FUNCTION,
                    "窗口函数(OVER)不支持分区批处理，依赖全局数据");
        }

        // 检查GROUP BY
        if (GROUP_BY_PATTERN.matcher(normalizedSql).find()) {
            return new AnalysisResult(false, SqlType.GROUP_BY,
                    "GROUP BY聚合不支持分区批处理，建议直接由数据库执行");
        }

        // 检查HAVING (通常与GROUP BY一起，但单独检测)
        if (HAVING_PATTERN.matcher(normalizedSql).find()) {
            return new AnalysisResult(false, SqlType.HAVING,
                    "HAVING子句不支持分区批处理");
        }

        // 检查ORDER BY - 支持，但需要归并排序
        Matcher orderByMatcher = ORDER_BY_PATTERN.matcher(normalizedSql);
        if (orderByMatcher.find()) {
            AnalysisResult result = new AnalysisResult(true, SqlType.SELECT_WITH_ORDER,
                    "ORDER BY查询支持批处理，将进行分区后归并排序");
            
            // 提取排序字段
            String orderByClause = orderByMatcher.group(1).trim();
            String[] fields = orderByClause.split(BatchDefaults.DEFAULT_SEPARATOR);
            for (String field : fields) {
                result.addOrderByField(field.trim());
            }
            return result;
        }

        // 简单查询，完全支持
        return new AnalysisResult(true, SqlType.SIMPLE_SELECT,
                "支持分区批处理");
    }

    /**
     * 批量校验工作流步骤
     * @param steps 步骤列表，每个步骤包含 batchEnabled 和 sqlScript
     * @return 不支持批处理的步骤信息列表
     */
    public List<String> validateSteps(List<StepInfo> steps) {
        List<String> errors = new ArrayList<>();
        for (StepInfo step : steps) {
            if (step.batchEnabled) {
                AnalysisResult result = analyze(step.sqlScript);
                if (!result.isSupported()) {
                    errors.add(String.format("步骤[%s]: %s", step.name, result.getReason()));
                }
            }
        }
        return errors;
    }

    /**
     * 步骤信息
     */
    public static class StepInfo {
        public String name;
        public String sqlScript;
        public boolean batchEnabled;

        public StepInfo(String name, String sqlScript, boolean batchEnabled) {
            this.name = name;
            this.sqlScript = sqlScript;
            this.batchEnabled = batchEnabled;
        }
    }
}

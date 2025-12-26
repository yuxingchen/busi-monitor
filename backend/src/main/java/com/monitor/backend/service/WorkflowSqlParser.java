package com.monitor.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工作流SQL解析器
 * <p>
 * 主要功能：
 * <ul>
 * <li>解析SQL中的FROM/JOIN子句，识别哪些是上游变量（内存数据），哪些是真实数据库表</li>
 * <li>提取SELECT字段的别名映射，用于追踪字段重命名</li>
 * <li><b>新增</b>：提取WHERE/GROUP BY/ORDER BY子句中的字段，作为索引候选字段</li>
 * </ul>
 * </p>
 * 
 * <h3>索引候选字段提取规则：</h3>
 * <p>
 * 以下子句中出现的字段会被标记为索引候选：
 * <ul>
 * <li>WHERE 子句 - 查询过滤条件中的字段</li>
 * <li>GROUP BY 子句 - 分组字段</li>
 * <li>ORDER BY 子句 - 排序字段</li>
 * <li>JOIN ON 子句 - 关联条件字段</li>
 * </ul>
 * </p>
 * 
 * @author Monitor System
 * @since 1.0
 */
@Service
public class WorkflowSqlParser {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowSqlParser.class);

    // ==================== 正则模式定义 ====================

    /** 匹配 SELECT ... FROM 之间的字段列表 */
    private static final Pattern SELECT_PATTERN = Pattern.compile(
            "SELECT\\s+(.+?)\\s+FROM", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** 匹配 FROM 后的主表 */
    private static final Pattern FROM_PATTERN = Pattern.compile(
            "FROM\\s+([\\w]+)(?:\\s+(?:AS\\s+)?([\\w]+))?", Pattern.CASE_INSENSITIVE);

    /** 匹配显式 JOIN 子句 */
    private static final Pattern JOIN_PATTERN = Pattern.compile(
            "(LEFT|RIGHT|INNER|FULL)?\\s*JOIN\\s+([\\w]+)(?:\\s+(?:AS\\s+)?([\\w]+))?\\s+ON\\s+([^\\s]+\\s*=\\s*[^\\s]+)",
            Pattern.CASE_INSENSITIVE);

    /** 匹配 ON 条件中的字段（格式：alias.field = alias.field） */
    private static final Pattern ON_CONDITION_PATTERN = Pattern.compile(
            "([\\w]+)\\.([\\w]+)\\s*=\\s*([\\w]+)\\.([\\w]+)", Pattern.CASE_INSENSITIVE);

    /** 匹配隐式 JOIN 的表（FROM a, b 格式） */
    private static final Pattern IMPLICIT_JOIN_TABLE_PATTERN = Pattern.compile(
            ",\\s*([\\w]+)(?:\\s+(?:AS\\s+)?([\\w]+))?", Pattern.CASE_INSENSITIVE);

    /** 匹配 WHERE 子句内容 */
    private static final Pattern WHERE_PATTERN = Pattern.compile(
            "WHERE\\s+(.+?)(?:GROUP|ORDER|LIMIT|$)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** 匹配 GROUP BY 子句内容 */
    private static final Pattern GROUP_BY_PATTERN = Pattern.compile(
            "GROUP\\s+BY\\s+(.+?)(?:HAVING|ORDER|LIMIT|$)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** 匹配 ORDER BY 子句内容 */
    private static final Pattern ORDER_BY_PATTERN = Pattern.compile(
            "ORDER\\s+BY\\s+(.+?)(?:LIMIT|$)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** SQL关键字集合，用于过滤非字段名 */
    private static final Set<String> SQL_KEYWORDS = Set.of(
            "AND", "OR", "NOT", "IN", "IS", "NULL", "LIKE", "BETWEEN", "EXISTS",
            "TRUE", "FALSE", "ASC", "DESC", "NULLS", "FIRST", "LAST",
            "SELECT", "FROM", "WHERE", "GROUP", "BY", "ORDER", "HAVING", "LIMIT");

    /**
     * 解析SQL语句
     * 
     * @param sql              SQL语句
     * @param contextVariables 上下文中已有的变量名集合
     * @return 解析结果
     */
    public ParseResult parse(String sql, Set<String> contextVariables) {
        // 预处理：将多个空白字符压缩为单个空格，方便正则匹配
        String normalizedSql = sql.replaceAll("\\s+", " ").trim();
        logger.debug("Normalized SQL: {}", normalizedSql);

        ParseResult result = new ParseResult();
        result.setOriginalSql(sql);

        // 1. 解析SELECT字段 (使用normalizedSql)
        Matcher selectMatcher = SELECT_PATTERN.matcher(normalizedSql);
        if (selectMatcher.find()) {
            String selectClause = selectMatcher.group(1).trim();
            result.setFieldAliasMap(parseSelectFieldsWithAlias(selectClause));
        }

        // 2. 解析FROM表 (使用normalizedSql)
        Matcher fromMatcher = FROM_PATTERN.matcher(normalizedSql);
        if (fromMatcher.find()) {
            String tableName = fromMatcher.group(1);
            String alias = fromMatcher.group(2);
            if (alias == null)
                alias = tableName;

            TableRef tableRef = new TableRef();
            tableRef.setTableName(tableName);
            tableRef.setAlias(alias);
            tableRef.setVariable(contextVariables.contains(tableName));
            result.setMainTable(tableRef);
        }

        // 3. 解析显式JOIN表 (使用normalizedSql)
        List<JoinClause> joins = new ArrayList<>();
        Matcher joinMatcher = JOIN_PATTERN.matcher(normalizedSql);
        while (joinMatcher.find()) {
            JoinClause join = new JoinClause();

            String joinType = joinMatcher.group(1);
            join.setJoinType(joinType != null ? joinType.toUpperCase() : "INNER");

            String tableName = joinMatcher.group(2);
            String alias = joinMatcher.group(3);
            if (alias == null)
                alias = tableName;

            TableRef tableRef = new TableRef();
            tableRef.setTableName(tableName);
            tableRef.setAlias(alias);
            tableRef.setVariable(contextVariables.contains(tableName));
            join.setTable(tableRef);

            // 解析ON条件
            String onClause = joinMatcher.group(4);
            logger.debug("Raw ON clause captured: '{}'", onClause);

            if (onClause != null) {
                onClause = onClause.trim();
                List<JoinCondition> conditions = parseOnConditions(onClause);
                logger.debug("Parsed {} conditions from ON clause", conditions.size());
                join.setConditions(conditions);
            }

            joins.add(join);
        }

        // 4. 解析隐式JOIN表 (FROM a, b WHERE ...)
        // 先找到FROM子句的范围
        int fromStart = normalizedSql.toUpperCase().indexOf("FROM ");
        int fromEnd = -1;
        String[] keywords = { "WHERE", "GROUP", "ORDER", "LIMIT", "LEFT JOIN", "RIGHT JOIN", "INNER JOIN", "FULL JOIN",
                "JOIN" };
        for (String kw : keywords) {
            int pos = normalizedSql.toUpperCase().indexOf(" " + kw + " ");
            if (pos > fromStart && (fromEnd == -1 || pos < fromEnd)) {
                fromEnd = pos;
            }
        }
        if (fromEnd == -1)
            fromEnd = normalizedSql.length();

        String fromClause = normalizedSql.substring(fromStart + 5, fromEnd).trim();
        logger.debug("FROM clause: '{}'", fromClause);

        // 检查是否有逗号连接的表
        Matcher implicitJoinMatcher = IMPLICIT_JOIN_TABLE_PATTERN.matcher(fromClause);
        while (implicitJoinMatcher.find()) {
            String tableName = implicitJoinMatcher.group(1);
            String alias = implicitJoinMatcher.group(2);
            if (alias == null)
                alias = tableName;

            logger.debug("Found implicit join table: {} AS {}", tableName, alias);

            JoinClause join = new JoinClause();
            join.setJoinType("INNER"); // 隐式JOIN相当于INNER JOIN

            TableRef tableRef = new TableRef();
            tableRef.setTableName(tableName);
            tableRef.setAlias(alias);
            tableRef.setVariable(contextVariables.contains(tableName));
            join.setTable(tableRef);

            // 从WHERE子句解析条件
            Matcher whereMatcher = WHERE_PATTERN.matcher(normalizedSql);
            if (whereMatcher.find()) {
                String whereClause = whereMatcher.group(1).trim();
                logger.debug("WHERE clause: '{}'", whereClause);
                List<JoinCondition> conditions = parseOnConditions(whereClause);
                // 过滤出与当前表相关的条件
                List<JoinCondition> relevantConditions = new ArrayList<>();
                for (JoinCondition cond : conditions) {
                    if (cond.getLeftAlias().equals(alias) || cond.getRightAlias().equals(alias)) {
                        relevantConditions.add(cond);
                        logger.debug("Found relevant condition: {}", cond);
                    }
                }
                join.setConditions(relevantConditions);
            }

            joins.add(join);
        }

        result.setJoins(joins);

        logger.debug("Parsed SQL: mainTable={}, joins={}, selectFields={}",
                result.getMainTable(), result.getJoins().size(), result.getSelectFields());

        // ===== 5. 解析索引候选字段（WHERE/GROUP BY/ORDER BY）=====
        List<IndexFieldInfo> indexCandidates = new ArrayList<>();

        // 5.1 从 WHERE 子句提取字段
        indexCandidates.addAll(extractFieldsFromWhere(normalizedSql));

        // 5.2 从 GROUP BY 子句提取字段
        indexCandidates.addAll(extractFieldsFromGroupBy(normalizedSql));

        // 5.3 从 ORDER BY 子句提取字段
        indexCandidates.addAll(extractFieldsFromOrderBy(normalizedSql));

        // 5.4 从 JOIN ON 条件提取字段
        for (JoinClause join : joins) {
            for (JoinCondition cond : join.getConditions()) {
                indexCandidates.add(new IndexFieldInfo(cond.getLeftField(), "JOIN_ON"));
                indexCandidates.add(new IndexFieldInfo(cond.getRightField(), "JOIN_ON"));
            }
        }

        result.setIndexCandidateFields(indexCandidates);
        logger.debug("Extracted {} index candidate fields", indexCandidates.size());

        return result;
    }

    // ==================== 索引字段提取方法 ====================

    /**
     * 从 WHERE 子句提取索引候选字段
     * <p>
     * 解析 WHERE 子句中的条件表达式，提取出所有被引用的字段名。
     * 这些字段通常是查询过滤的关键，适合创建索引以提升查询性能。
     * </p>
     * 
     * @param sql 标准化后的SQL语句
     * @return 索引候选字段列表
     */
    private List<IndexFieldInfo> extractFieldsFromWhere(String sql) {
        List<IndexFieldInfo> fields = new ArrayList<>();
        Matcher matcher = WHERE_PATTERN.matcher(sql);
        if (matcher.find()) {
            String whereClause = matcher.group(1).trim();
            fields.addAll(extractFieldReferences(whereClause, "WHERE"));
        }
        return fields;
    }

    /**
     * 从 GROUP BY 子句提取索引候选字段
     * <p>
     * GROUP BY 中的字段是分组聚合的依据，创建索引可以显著提升分组查询性能。
     * </p>
     * 
     * @param sql 标准化后的SQL语句
     * @return 索引候选字段列表
     */
    private List<IndexFieldInfo> extractFieldsFromGroupBy(String sql) {
        List<IndexFieldInfo> fields = new ArrayList<>();
        Matcher matcher = GROUP_BY_PATTERN.matcher(sql);
        if (matcher.find()) {
            String groupByClause = matcher.group(1).trim();
            // GROUP BY 后面直接是字段列表，用逗号分隔
            String[] parts = groupByClause.split(",");
            for (String part : parts) {
                String field = extractSimpleFieldName(part.trim());
                if (field != null && !SQL_KEYWORDS.contains(field.toUpperCase())) {
                    fields.add(new IndexFieldInfo(field, "GROUP_BY"));
                }
            }
        }
        return fields;
    }

    /**
     * 从 ORDER BY 子句提取索引候选字段
     * <p>
     * ORDER BY 中的字段用于结果排序，创建索引可以避免额外的排序操作。
     * </p>
     * 
     * @param sql 标准化后的SQL语句
     * @return 索引候选字段列表
     */
    private List<IndexFieldInfo> extractFieldsFromOrderBy(String sql) {
        List<IndexFieldInfo> fields = new ArrayList<>();
        Matcher matcher = ORDER_BY_PATTERN.matcher(sql);
        if (matcher.find()) {
            String orderByClause = matcher.group(1).trim();
            // ORDER BY 后面是字段列表，可能带 ASC/DESC
            String[] parts = orderByClause.split(",");
            for (String part : parts) {
                // 移除 ASC/DESC 后缀
                String cleaned = part.trim().replaceAll("(?i)\\s+(ASC|DESC)\\s*$", "").trim();
                String field = extractSimpleFieldName(cleaned);
                if (field != null && !SQL_KEYWORDS.contains(field.toUpperCase())) {
                    fields.add(new IndexFieldInfo(field, "ORDER_BY"));
                }
            }
        }
        return fields;
    }

    /**
     * 从条件表达式中提取字段引用
     * <p>
     * 解析复杂的条件表达式（如 WHERE a.x = 1 AND b.y > 2），
     * 提取其中所有的字段引用。
     * </p>
     * 
     * @param expression 条件表达式
     * @param source     来源标识（WHERE/GROUP_BY/ORDER_BY）
     * @return 索引候选字段列表
     */
    private List<IndexFieldInfo> extractFieldReferences(String expression, String source) {
        List<IndexFieldInfo> fields = new ArrayList<>();

        // 按空格和运算符分割，提取可能的字段引用
        String[] tokens = expression.split("[\\s,=<>!()\\+\\-\\*/]+");
        for (String token : tokens) {
            token = token.trim();
            if (token.isEmpty())
                continue;

            // 跳过纯数字和字符串字面量
            if (token.matches("^[0-9.]+$") || token.startsWith("'") || token.startsWith("\"")) {
                continue;
            }

            // 跳过SQL关键字
            if (SQL_KEYWORDS.contains(token.toUpperCase())) {
                continue;
            }

            // 提取字段名（支持 alias.field 格式）
            String fieldName = extractSimpleFieldName(token);
            if (fieldName != null) {
                fields.add(new IndexFieldInfo(fieldName, source));
            }
        }

        return fields;
    }

    /**
     * 提取简单字段名（去掉表别名前缀）
     * <p>
     * 将 "t.field_name" 转换为 "field_name"。
     * </p>
     * 
     * @param token 可能包含表别名的字段引用
     * @return 简单字段名，如果无法解析则返回null
     */
    private String extractSimpleFieldName(String token) {
        if (token == null || token.isEmpty())
            return null;

        // 如果包含点号，取最后一部分作为字段名
        if (token.contains(".")) {
            String[] parts = token.split("\\.");
            return parts[parts.length - 1].trim();
        }

        // 检查是否是有效的标识符
        if (token.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            return token;
        }

        return null;
    }

    /**
     * 解析SELECT字段列表，返回字段映射（原始字段 -> 别名/输出名）
     */
    private Map<String, String> parseSelectFieldsWithAlias(String selectClause) {
        Map<String, String> fieldMap = new LinkedHashMap<>();
        String[] parts = selectClause.split(",");
        for (String part : parts) {
            String field = part.trim();
            String originalField;
            String aliasField;

            // 处理 "field AS alias" 格式
            if (field.toUpperCase().contains(" AS ")) {
                int asPos = field.toUpperCase().lastIndexOf(" AS ");
                originalField = field.substring(0, asPos).trim();
                aliasField = field.substring(asPos + 4).trim();
            } else {
                // 处理 "field alias" 格式（空格分隔）
                int lastSpace = field.lastIndexOf(' ');
                if (lastSpace > 0 && !field.substring(0, lastSpace).trim().endsWith(")")) {
                    originalField = field.substring(0, lastSpace).trim();
                    aliasField = field.substring(lastSpace + 1).trim();
                } else {
                    // 无别名，使用字段本身
                    originalField = field;
                    aliasField = field;
                }
            }
            fieldMap.put(originalField, aliasField);
        }
        return fieldMap;
    }

    /**
     * 解析ON条件
     */
    private List<JoinCondition> parseOnConditions(String onClause) {
        List<JoinCondition> conditions = new ArrayList<>();
        Matcher matcher = ON_CONDITION_PATTERN.matcher(onClause);
        while (matcher.find()) {
            JoinCondition cond = new JoinCondition();
            cond.setLeftAlias(matcher.group(1));
            cond.setLeftField(matcher.group(2));
            cond.setRightAlias(matcher.group(3));
            cond.setRightField(matcher.group(4));
            conditions.add(cond);
        }
        return conditions;
    }

    // ========== 内部类 ==========

    /**
     * SQL解析结果
     * <p>
     * 包含SQL语句解析后的完整信息：
     * <ul>
     * <li>SELECT 字段列表及其别名映射</li>
     * <li>FROM/JOIN 表引用</li>
     * <li>索引候选字段列表</li>
     * </ul>
     * </p>
     */
    public static class ParseResult {
        private String originalSql;
        private List<String> selectFields = new ArrayList<>();
        private Map<String, String> fieldAliasMap = new LinkedHashMap<>(); // 原始字段 -> 别名
        private TableRef mainTable;
        private List<JoinClause> joins = new ArrayList<>();
        /** 索引候选字段列表（从 WHERE/GROUP BY/ORDER BY/JOIN ON 提取） */
        private List<IndexFieldInfo> indexCandidateFields = new ArrayList<>();

        // Getters and Setters
        public String getOriginalSql() {
            return originalSql;
        }

        public void setOriginalSql(String originalSql) {
            this.originalSql = originalSql;
        }

        public List<String> getSelectFields() {
            return selectFields;
        }

        public void setSelectFields(List<String> selectFields) {
            this.selectFields = selectFields;
        }

        public Map<String, String> getFieldAliasMap() {
            return fieldAliasMap;
        }

        public void setFieldAliasMap(Map<String, String> fieldAliasMap) {
            this.fieldAliasMap = fieldAliasMap;
            // 同时更新selectFields（使用别名作为输出字段）
            this.selectFields = new ArrayList<>(fieldAliasMap.values());
        }

        public TableRef getMainTable() {
            return mainTable;
        }

        public void setMainTable(TableRef mainTable) {
            this.mainTable = mainTable;
        }

        public List<JoinClause> getJoins() {
            return joins;
        }

        public void setJoins(List<JoinClause> joins) {
            this.joins = joins;
        }

        public List<IndexFieldInfo> getIndexCandidateFields() {
            return indexCandidateFields;
        }

        public void setIndexCandidateFields(List<IndexFieldInfo> indexCandidateFields) {
            this.indexCandidateFields = indexCandidateFields;
        }

        /**
         * 获取去重后的索引候选字段名集合
         * 
         * @return 不重复的字段名集合
         */
        public Set<String> getUniqueIndexCandidateFieldNames() {
            Set<String> names = new HashSet<>();
            for (IndexFieldInfo info : indexCandidateFields) {
                names.add(info.getFieldName());
            }
            return names;
        }

        /**
         * 获取所有变量表
         */
        public List<TableRef> getVariableTables() {
            List<TableRef> vars = new ArrayList<>();
            if (mainTable != null && mainTable.isVariable()) {
                vars.add(mainTable);
            }
            for (JoinClause join : joins) {
                if (join.getTable().isVariable()) {
                    vars.add(join.getTable());
                }
            }
            return vars;
        }

        /**
         * 获取所有真实数据库表
         */
        public List<TableRef> getRealTables() {
            List<TableRef> tables = new ArrayList<>();
            if (mainTable != null && !mainTable.isVariable()) {
                tables.add(mainTable);
            }
            for (JoinClause join : joins) {
                if (!join.getTable().isVariable()) {
                    tables.add(join.getTable());
                }
            }
            return tables;
        }
    }

    /**
     * 索引候选字段信息
     * <p>
     * 记录从SQL子句中提取的可能需要创建索引的字段信息，
     * 包括字段名和来源（WHERE/GROUP_BY/ORDER_BY/JOIN_ON）。
     * </p>
     */
    public static class IndexFieldInfo {
        /** 字段名（已去除表别名前缀） */
        private String fieldName;
        /** 来源标识：WHERE, GROUP_BY, ORDER_BY, JOIN_ON */
        private String source;

        public IndexFieldInfo() {
        }

        public IndexFieldInfo(String fieldName, String source) {
            this.fieldName = fieldName;
            this.source = source;
        }

        public String getFieldName() {
            return fieldName;
        }

        public void setFieldName(String fieldName) {
            this.fieldName = fieldName;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        @Override
        public String toString() {
            return fieldName + " [" + source + "]";
        }
    }

    /**
     * 表引用
     */
    public static class TableRef {
        private String tableName;
        private String alias;
        private boolean isVariable; // true表示是上游变量，false表示是真实表

        public String getTableName() {
            return tableName;
        }

        public void setTableName(String tableName) {
            this.tableName = tableName;
        }

        public String getAlias() {
            return alias;
        }

        public void setAlias(String alias) {
            this.alias = alias;
        }

        public boolean isVariable() {
            return isVariable;
        }

        public void setVariable(boolean variable) {
            isVariable = variable;
        }

        @Override
        public String toString() {
            return tableName + (alias != null && !alias.equals(tableName) ? " AS " + alias : "") +
                    (isVariable ? " [VAR]" : " [TABLE]");
        }
    }

    /**
     * JOIN子句
     */
    public static class JoinClause {
        private String joinType; // LEFT, RIGHT, INNER, FULL
        private TableRef table;
        private List<JoinCondition> conditions = new ArrayList<>();

        public String getJoinType() {
            return joinType;
        }

        public void setJoinType(String joinType) {
            this.joinType = joinType;
        }

        public TableRef getTable() {
            return table;
        }

        public void setTable(TableRef table) {
            this.table = table;
        }

        public List<JoinCondition> getConditions() {
            return conditions;
        }

        public void setConditions(List<JoinCondition> conditions) {
            this.conditions = conditions;
        }
    }

    /**
     * JOIN条件
     */
    public static class JoinCondition {
        private String leftAlias;
        private String leftField;
        private String rightAlias;
        private String rightField;

        public String getLeftAlias() {
            return leftAlias;
        }

        public void setLeftAlias(String leftAlias) {
            this.leftAlias = leftAlias;
        }

        public String getLeftField() {
            return leftField;
        }

        public void setLeftField(String leftField) {
            this.leftField = leftField;
        }

        public String getRightAlias() {
            return rightAlias;
        }

        public void setRightAlias(String rightAlias) {
            this.rightAlias = rightAlias;
        }

        public String getRightField() {
            return rightField;
        }

        public void setRightField(String rightField) {
            this.rightField = rightField;
        }

        @Override
        public String toString() {
            return leftAlias + "." + leftField + " = " + rightAlias + "." + rightField;
        }
    }
}

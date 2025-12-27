package com.monitor.backend.batch;

import com.monitor.backend.cache.CacheStrategy;
import com.monitor.backend.component.WorkflowSqlParser;
import com.monitor.backend.constant.BatchDefaults;
import com.monitor.backend.constant.PlaceholderPrefix;
import com.monitor.backend.constant.WorkflowStepType;
import com.monitor.backend.entity.WorkflowStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 批处理步骤解析器
 * <p>
 * 分析工作流步骤，提取批处理所需的参数：
 * - 源表名
 * - ID 列（用于分区）
 * - 分区 SQL（带 BETWEEN 条件）
 * </p>
 * 
 * @author Monitor System
 */
@Component
public class BatchStepResolver {

    private static final Logger log = LoggerFactory.getLogger(BatchStepResolver.class);

    // 匹配 FROM 子句中的表名
    private static final Pattern FROM_TABLE_PATTERN = Pattern.compile(
            "\\bFROM\\s+([`\\w]+\\.)?([`\\w]+)\\s*",
            Pattern.CASE_INSENSITIVE);

    // 匹配常见的主键列名模式
    private static final String[] COMMON_ID_COLUMNS = { "id", "pk_id", "order_id", "user_id", "record_id" };

    private final WorkflowSqlParser sqlParser;

    public BatchStepResolver(WorkflowSqlParser sqlParser) {
        this.sqlParser = sqlParser;
    }

    /**
     * 分析单个工作流步骤，提取批处理参数
     * <p>
     * 注意：此方法不判定执行模式，默认PURE_BATCH。
     * 如需判定执行模式，请使用 {@link #analyzeWithContext(WorkflowStep, Set)}
     * </p>
     * 
     * @param step 工作流步骤
     * @return 批处理步骤信息
     */
    public BatchStepInfo analyze(WorkflowStep step) {
        return analyzeWithContext(step, new HashSet<>());
    }
    
    /**
     * 分析工作流步骤，支持上下文变量识别和执行模式判定
     * <p>
     * 执行模式判定逻辑：
     * <ul>
     *   <li>PURE_BATCH: SQL不引用任何上下文变量，可直接使用Spring Batch分区查询</li>
     *   <li>CONTEXT_BATCH: SQL引用上下文变量 + 关联真实表，需混合执行</li>
     *   <li>CONTEXT_ONLY: SQL仅引用上下文变量，无真实表，使用内存处理</li>
     * </ul>
     * </p>
     * <p>
     * 上下文变量识别方式：
     * <ul>
     *   <li>${varName} 格式的占位符引用</li>
     *   <li>FROM/JOIN子句中的表名匹配availableVariables</li>
     * </ul>
     * </p>
     * 
     * @param step 工作流步骤
     * @param availableVariables 当前可用的上下文变量名（来自前序步骤的resultVariable）
     * @return 批处理步骤信息（包含执行模式）
     */
    public BatchStepInfo analyzeWithContext(WorkflowStep step, Set<String> availableVariables) {
        if (step == null || step.getSqlScript() == null) {
            throw new IllegalArgumentException("步骤或SQL脚本不能为空");
        }

        String sql = step.getSqlScript().trim();

        BatchStepInfo info = new BatchStepInfo();
        info.setStepId(step.getId());
        info.setStepName(step.getName());
        info.setDatasourceId(step.getDatasourceId());
        info.setResultVariable(step.getResultVariable());
        info.setOriginalSql(sql);

        // 解析源表名（可能是变量或真实表）
        String tableName = extractTableName(sql);
        info.setTableName(tableName);

        // 使用步骤配置或推断ID列
        String idColumn = resolveIdColumn(step, sql, tableName);
        info.setIdColumn(idColumn);
        
        // 使用步骤配置的分区参数
        info.setPartitionCount(BatchDefaults.getPartitionCount(step.getPartitionCount()));
        info.setChunkSize(BatchDefaults.getChunkSize(step.getChunkSize()));
        info.setCacheStrategy(CacheStrategy.Type.fromCode(step.getCacheStrategy()).name());

        // ===== 执行模式判定 =====
        
        // 1. 提取SQL中的${varName}格式变量引用
        Set<String> varRefs = extractVariableReferences(sql);
        Set<String> contextVars = new HashSet<>();
        for (String ref : varRefs) {
            if (availableVariables.contains(ref)) {
                contextVars.add(ref);
            }
        }
        
        // 2. 提取SQL中FROM/JOIN子句的所有表名
        Set<String> allTables = extractAllTableNames(sql);
        
        // 调试日志：打印可用变量和提取的表名
        log.info("DEBUG availableVariables={}, allTables={}", availableVariables, allTables);
        
        // 3. 检查表名是否匹配availableVariables（关键修复！）
        for (String tbl : allTables) {
            if (availableVariables.contains(tbl)) {
                contextVars.add(tbl);
                log.info("识别到表名[{}]为上下文变量", tbl);
            }
        }
        info.setContextVariables(contextVars);
        
        // 4. 真实表 = 所有表 - 上下文变量
        Set<String> realTables = new HashSet<>();
        for (String tbl : allTables) {
            if (!contextVars.contains(tbl)) {
                realTables.add(tbl);
            }
        }
        info.setRealTables(realTables);
        
        // 5. 判定执行模式
        if (contextVars.isEmpty()) {
            // 无变量依赖 → 纯批处理
            info.setExecutionMode(BatchStepInfo.ExecutionMode.PURE_BATCH);
            
            // 构建分区 SQL
            String partitionedSql = buildPartitionedSql(sql, idColumn);
            info.setPartitionedSql(partitionedSql);
        } else if (realTables.isEmpty()) {
            // 仅变量依赖，无真实表 → 纯内存处理
            info.setExecutionMode(BatchStepInfo.ExecutionMode.CONTEXT_ONLY);
        } else {
            // 变量 + 真实表 → 混合模式
            info.setExecutionMode(BatchStepInfo.ExecutionMode.CONTEXT_BATCH);
        }

        // 生成缓存键
        String cacheKey = generateCacheKey(step);
        info.setCacheKey(cacheKey);

        log.info("解析步骤完成: stepId={}, table={}, mode={}, contextVars={}, realTables={}",
                step.getId(), tableName, info.getExecutionMode(), contextVars, realTables);

        return info;
    }
    
    /**
     * 从SQL中提取所有表名（FROM和JOIN子句）
     * 
     * @param sql SQL语句
     * @return 所有表名集合
     */
    private Set<String> extractAllTableNames(String sql) {
        Set<String> tables = new HashSet<>();
        
        // 提取FROM子句的表名
        String mainTable = extractTableName(sql);
        if (mainTable != null) {
            tables.add(mainTable);
        }
        
        // 提取JOIN子句的表名
        Pattern joinPattern = Pattern.compile(
            "(?:LEFT|RIGHT|INNER|CROSS)?\\s*JOIN\\s+([`\\w]+\\.)?([`\\w]+)\\s+",
            Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = joinPattern.matcher(sql);
        while (matcher.find()) {
            String joinTable = matcher.group(2);
            if (joinTable != null) {
                joinTable = joinTable.replace("`", "");
                tables.add(joinTable);
            }
        }
        
        return tables;
    }

    /**
     * 分析多个工作流步骤，生成批处理执行计划
     * 
     * @param steps 工作流步骤列表
     * @return 批处理执行计划
     */
    public BatchExecutionPlan analyzeAll(List<WorkflowStep> steps) {
        BatchExecutionPlan plan = new BatchExecutionPlan();
        List<BatchStepInfo> batchSteps = new ArrayList<>();
        List<WorkflowStep> joinSteps = new ArrayList<>();

        for (WorkflowStep step : steps) {
            if (WorkflowStepType.SQL.matches(step.getStepType())) {
                String sql = step.getSqlScript();

                // 判断是否为独立数据抽取步骤（不引用其他变量）
                if (!containsVariableReference(sql)) {
                    // 可以使用 Spring Batch 批处理
                    batchSteps.add(analyze(step));
                } else {
                    // 引用其他变量的 JOIN 步骤，需要在批处理后执行
                    joinSteps.add(step);
                }
            }
        }

        plan.setBatchSteps(batchSteps);
        plan.setJoinSteps(joinSteps);
        plan.setHasJoinSteps(!joinSteps.isEmpty());

        log.info("执行计划分析完成: 批处理步骤数={}, JOIN步骤数={}",
                batchSteps.size(), joinSteps.size());

        return plan;
    }

    /**
     * 从 SQL 中提取主表名
     */
    private String extractTableName(String sql) {
        Matcher matcher = FROM_TABLE_PATTERN.matcher(sql);
        if (matcher.find()) {
            String tableName = matcher.group(2);
            // 去除反引号
            return tableName.replace("`", "");
        }
        log.warn("无法从SQL中提取表名: {}", sql.substring(0, Math.min(100, sql.length())));
        return BatchDefaults.UNKNOWN_TABLE;
    }

    /**
     * 解析ID列名
     * <p>
     * 优先级：
     * 1. 步骤配置的 idColumn
     * 2. 自动推断（匹配常见ID列名）
     * 3. 无法确定时抛出异常
     * </p>
     */
    private String resolveIdColumn(WorkflowStep step, String sql, String tableName) {
        // 1. 优先使用步骤配置
        if (step.getIdColumn() != null && !step.getIdColumn().trim().isEmpty()) {
            log.info("使用步骤配置的分区字段: {}", step.getIdColumn());
            return step.getIdColumn().trim();
        }
        
        // 2. 自动推断
        return inferIdColumn(sql, tableName);
    }

    /**
     * 推断 ID 列名
     * <p>
     * 策略：
     * 1. 检查 SQL 中是否有常见的 ID 列
     * 2. 无法确定时抛出异常
     * </p>
     */
    private String inferIdColumn(String sql, String tableName) {
        String upperSql = sql.toUpperCase();

        // 尝试匹配常见的 ID 列名
        for (String idColumn : COMMON_ID_COLUMNS) {
            if (upperSql.contains(idColumn.toUpperCase())) {
                log.info("自动识别分区字段: {}", idColumn);
                return idColumn;
            }
        }

        // 无法确定时抛出异常
        throw new RuntimeException(
            String.format("无法自动识别表 '%s' 的分区字段，请在步骤配置中手动指定 'idColumn'", tableName));
    }

    /**
     * 构建分区 SQL
     * <p>
     * 在原 SQL 的 WHERE 子句中添加 ID 范围条件。
     * 如果没有 WHERE 子句，则添加 WHERE。
     * </p>
     * 
     * @param sql      原始 SQL
     * @param idColumn ID 列名
     * @return 带分区条件的 SQL
     */
    private String buildPartitionedSql(String sql, String idColumn) {
        String partitionCondition = idColumn + " BETWEEN :minId AND :maxId";

        // 检查是否已有 WHERE 子句
        String upperSql = sql.toUpperCase();
        int whereIndex = upperSql.lastIndexOf(" WHERE ");

        if (whereIndex > 0) {
            // 已有 WHERE，添加 AND 条件
            // 需要在 WHERE 后、ORDER BY/GROUP BY/LIMIT 前插入
            int insertIndex = findInsertPosition(sql, whereIndex + 7);

            StringBuilder sb = new StringBuilder(sql);
            sb.insert(insertIndex, " AND " + partitionCondition);
            return sb.toString();
        } else {
            // 没有 WHERE，在 FROM 表名后添加 WHERE
            int fromEnd = findFromClauseEnd(sql);

            StringBuilder sb = new StringBuilder(sql);
            sb.insert(fromEnd, " WHERE " + partitionCondition);
            return sb.toString();
        }
    }

    /**
     * 查找插入分区条件的位置
     * 应该在 ORDER BY / GROUP BY / LIMIT / HAVING 之前
     */
    private int findInsertPosition(String sql, int startIndex) {
        String upperSql = sql.toUpperCase();

        String[] keywords = { " ORDER BY ", " GROUP BY ", " LIMIT ", " HAVING " };
        int minIndex = sql.length();

        for (String keyword : keywords) {
            int idx = upperSql.indexOf(keyword, startIndex);
            if (idx > 0 && idx < minIndex) {
                minIndex = idx;
            }
        }

        return minIndex;
    }

    /**
     * 查找 FROM 子句结束位置
     */
    private int findFromClauseEnd(String sql) {
        String upperSql = sql.toUpperCase();

        Matcher matcher = FROM_TABLE_PATTERN.matcher(upperSql);
        if (matcher.find()) {
            return matcher.end();
        }

        return sql.length();
    }

    /**
     * 检查 SQL 是否引用了其他变量（如 ${varName}）
     */
    private boolean containsVariableReference(String sql) {
        // 检查是否包含变量引用模式 ${xxx} 或 :xxx
        return sql.contains("${") ||
                Pattern.compile(":\\w+\\b(?!.*BETWEEN)").matcher(sql).find();
    }

    /**
     * 生成缓存键
     */
    private String generateCacheKey(WorkflowStep step) {
        return String.format("wf_%d_step%d_%s",
                step.getWorkflowId(),
                step.getStepOrder(),
                step.getResultVariable() != null ? step.getResultVariable() : "result");
    }

    // ==================== 依赖分析 ====================

    /**
     * 分析步骤间的依赖关系
     * 
     * @param steps 工作流步骤列表
     * @return 步骤依赖信息映射（步骤名 -> 依赖信息）
     */
    public Map<String, StepDependencyInfo> analyzeDependencies(List<WorkflowStep> steps) {
        // 1. 构建 resultVariable -> stepName 映射
        Map<String, String> varToStep = new HashMap<>();
        for (WorkflowStep step : steps) {
            if (step.getResultVariable() != null && !step.getResultVariable().isEmpty()) {
                varToStep.put(step.getResultVariable(), step.getName());
            }
        }
        
        // 2. 分析每个步骤的依赖
        Map<String, StepDependencyInfo> result = new HashMap<>();
        
        for (WorkflowStep step : steps) {
            StepDependencyInfo info = new StepDependencyInfo();
            info.setStepName(step.getName());
            info.setResultVariable(step.getResultVariable());
            info.setStepOrder(step.getStepOrder());
            
            // 从SQL中提取变量引用
            Set<String> refs = extractVariableReferences(step.getSqlScript());
            
            // 过滤出属于上游步骤的变量（排除内置变量）
            Set<String> stepDeps = new java.util.HashSet<>();
            for (String ref : refs) {
                if (varToStep.containsKey(ref)) {
                    stepDeps.add(ref);
                }
            }
            info.setDependsOn(stepDeps);
            result.put(step.getName(), info);
        }
        
        // 3. 计算依赖层级
        calculateDependencyLevels(result, varToStep);
        
        log.info("依赖分析完成: 共{}个步骤", result.size());
        return result;
    }

    /**
     * 从SQL中提取变量引用
     * 匹配格式: ${varName} 或 ${varName.field}
     */
    private Set<String> extractVariableReferences(String sql) {
        Set<String> refs = new java.util.HashSet<>();
        if (sql == null) return refs;
        
        // 正则匹配 ${xxx} 或 ${xxx.yyy}
        Pattern pattern = Pattern.compile("\\$\\{([a-zA-Z_][a-zA-Z0-9_]*)(\\.[a-zA-Z_][a-zA-Z0-9_]*)?\\}");
        Matcher matcher = pattern.matcher(sql);
        
        while (matcher.find()) {
            String varName = matcher.group(1);
            // 排除内置变量
            if (!PlaceholderPrefix.isBuiltinVar(varName)) {
                refs.add(varName);
            }
        }
        return refs;
    }



    /**
     * 计算依赖层级
     * 层级0：无依赖，可最先执行
     * 层级N：依赖层级N-1的步骤
     */
    private void calculateDependencyLevels(Map<String, StepDependencyInfo> depMap, Map<String, String> varToStep) {
        // 迭代计算层级，直到所有步骤都确定层级
        boolean changed = true;
        int maxIterations = 100;
        int iteration = 0;
        
        while (changed && iteration < maxIterations) {
            changed = false;
            iteration++;
            
            for (StepDependencyInfo info : depMap.values()) {
                if (info.getDependsOn().isEmpty()) {
                    // 无依赖，层级0
                    if (info.getDependencyLevel() != 0) {
                        info.setDependencyLevel(0);
                        changed = true;
                    }
                } else {
                    // 计算依赖的最大层级 + 1
                    int maxDepLevel = 0;
                    for (String depVar : info.getDependsOn()) {
                        String depStepName = varToStep.get(depVar);
                        if (depStepName != null && depMap.containsKey(depStepName)) {
                            int depLevel = depMap.get(depStepName).getDependencyLevel();
                            maxDepLevel = Math.max(maxDepLevel, depLevel);
                        }
                    }
                    int newLevel = maxDepLevel + 1;
                    if (info.getDependencyLevel() != newLevel) {
                        info.setDependencyLevel(newLevel);
                        changed = true;
                    }
                }
            }
        }
    }

    /**
     * 按依赖层级分组步骤
     * 
     * @param steps 工作流步骤列表
     * @param depMap 依赖信息映射
     * @return 按层级分组的步骤列表
     */
    public List<List<WorkflowStep>> groupByDependencyLevel(List<WorkflowStep> steps, Map<String, StepDependencyInfo> depMap) {
        // 找出最大层级
        int maxLevel = 0;
        for (StepDependencyInfo info : depMap.values()) {
            maxLevel = Math.max(maxLevel, info.getDependencyLevel());
        }
        
        // 按层级分组
        List<List<WorkflowStep>> layers = new ArrayList<>();
        for (int level = 0; level <= maxLevel; level++) {
            layers.add(new ArrayList<>());
        }
        
        for (WorkflowStep step : steps) {
            StepDependencyInfo info = depMap.get(step.getName());
            if (info != null) {
                int level = info.getDependencyLevel();
                layers.get(level).add(step);
            }
        }
        
        // 移除空层级
        layers.removeIf(List::isEmpty);
        
        log.info("步骤分层完成: 共{}层", layers.size());
        return layers;
    }

    // ==================== 内部类 ====================

    /**
     * 步骤依赖信息
     */
    public static class StepDependencyInfo {
        private String stepName;
        private String resultVariable;
        private Integer stepOrder;
        private Set<String> dependsOn = new java.util.HashSet<>();
        private int dependencyLevel = -1;

        public String getStepName() { return stepName; }
        public void setStepName(String stepName) { this.stepName = stepName; }
        public String getResultVariable() { return resultVariable; }
        public void setResultVariable(String resultVariable) { this.resultVariable = resultVariable; }
        public Integer getStepOrder() { return stepOrder; }
        public void setStepOrder(Integer stepOrder) { this.stepOrder = stepOrder; }
        public Set<String> getDependsOn() { return dependsOn; }
        public void setDependsOn(Set<String> dependsOn) { this.dependsOn = dependsOn; }
        public int getDependencyLevel() { return dependencyLevel; }
        public void setDependencyLevel(int dependencyLevel) { this.dependencyLevel = dependencyLevel; }
        
        public boolean hasDependency() { return !dependsOn.isEmpty(); }
    }

    /**
     * 批处理步骤信息
     * <p>
     * 包含步骤的批处理执行所需的全部信息：
     * - 执行模式（纯批处理/混合模式/纯内存）
     * - 上下文变量（来自前序步骤的数据）
     * - 真实表（需要从数据库查询的表）
     * - 分区配置（分区数、chunk大小）
     * </p>
     */
    public static class BatchStepInfo {
        
        // ========== 执行模式枚举 ==========
        
        /**
         * 批处理执行模式
         */
        public enum ExecutionMode {
            /** 纯批处理：无变量依赖，直接使用Spring Batch分区查询 */
            PURE_BATCH,
            
            /** 混合模式：依赖上游变量 + 关联真实表，变量从context获取，真实表批量查询，最后内存JOIN */
            CONTEXT_BATCH,
            
            /** 纯内存模式：仅依赖变量表，使用parallelStream处理 */
            CONTEXT_ONLY
        }
        
        // ========== 基本信息 ==========
        
        /** 步骤ID */
        private Long stepId;
        
        /** 步骤名称 */
        private String stepName;
        
        /** 数据源ID */
        private Long datasourceId;
        
        /** 结果变量名（存入context的key） */
        private String resultVariable;
        
        /** 主表名（可能是变量名或真实表名） */
        private String tableName;
        
        /** 分区ID列名 */
        private String idColumn;
        
        /** 原始SQL */
        private String originalSql;
        
        /** 分区查询SQL模板 */
        private String partitionedSql;
        
        /** 缓存键 */
        private String cacheKey;
        
        /** 分区数量 */
        private Integer partitionCount;
        
        /** 每批处理大小 */
        private Integer chunkSize;
        
        /** 缓存策略（FILE/REDIS/TEMP_TABLE） */
        private String cacheStrategy;
        
        // ========== 混合模式相关字段 ==========
        
        /** 执行模式 */
        private ExecutionMode executionMode = ExecutionMode.PURE_BATCH;
        
        /** 上下文变量名集合（来自前序步骤resultVariable的数据） */
        private Set<String> contextVariables = new HashSet<>();
        
        /** 真实表名集合（需要从数据库查询的表） */
        private Set<String> realTables = new HashSet<>();
        
        /** SQL解析结果（用于内存JOIN） */
        private Object parseResult;
        
        // ========== Getters and Setters ==========
        
        public Long getStepId() {
            return stepId;
        }

        public void setStepId(Long stepId) {
            this.stepId = stepId;
        }

        public String getStepName() {
            return stepName;
        }

        public void setStepName(String stepName) {
            this.stepName = stepName;
        }

        public Long getDatasourceId() {
            return datasourceId;
        }

        public void setDatasourceId(Long datasourceId) {
            this.datasourceId = datasourceId;
        }

        public String getResultVariable() {
            return resultVariable;
        }

        public void setResultVariable(String resultVariable) {
            this.resultVariable = resultVariable;
        }

        public String getTableName() {
            return tableName;
        }

        public void setTableName(String tableName) {
            this.tableName = tableName;
        }

        public String getIdColumn() {
            return idColumn;
        }

        public void setIdColumn(String idColumn) {
            this.idColumn = idColumn;
        }

        public String getOriginalSql() {
            return originalSql;
        }

        public void setOriginalSql(String originalSql) {
            this.originalSql = originalSql;
        }

        public String getPartitionedSql() {
            return partitionedSql;
        }

        public void setPartitionedSql(String partitionedSql) {
            this.partitionedSql = partitionedSql;
        }

        public String getCacheKey() {
            return cacheKey;
        }

        public void setCacheKey(String cacheKey) {
            this.cacheKey = cacheKey;
        }

        public Integer getPartitionCount() {
            return partitionCount;
        }

        public void setPartitionCount(Integer partitionCount) {
            this.partitionCount = partitionCount;
        }

        public Integer getChunkSize() {
            return chunkSize;
        }

        public void setChunkSize(Integer chunkSize) {
            this.chunkSize = chunkSize;
        }

        public String getCacheStrategy() {
            return cacheStrategy;
        }

        public void setCacheStrategy(String cacheStrategy) {
            this.cacheStrategy = cacheStrategy;
        }
        
        // ========== 混合模式 Getters and Setters ==========
        
        public ExecutionMode getExecutionMode() {
            return executionMode;
        }

        public void setExecutionMode(ExecutionMode executionMode) {
            this.executionMode = executionMode;
        }

        public Set<String> getContextVariables() {
            return contextVariables;
        }

        public void setContextVariables(Set<String> contextVariables) {
            this.contextVariables = contextVariables;
        }

        public Set<String> getRealTables() {
            return realTables;
        }

        public void setRealTables(Set<String> realTables) {
            this.realTables = realTables;
        }

        public Object getParseResult() {
            return parseResult;
        }

        public void setParseResult(Object parseResult) {
            this.parseResult = parseResult;
        }
        
        /**
         * 判断是否需要混合执行（有变量依赖）
         */
        public boolean requiresContextData() {
            return !contextVariables.isEmpty();
        }
        
        /**
         * 判断是否需要批量查询真实表
         */
        public boolean requiresBatchQuery() {
            return !realTables.isEmpty();
        }
    }

    /**
     * 批处理执行计划
     */
    public static class BatchExecutionPlan {
        private List<BatchStepInfo> batchSteps;
        private List<WorkflowStep> joinSteps;
        private boolean hasJoinSteps;

        public List<BatchStepInfo> getBatchSteps() {
            return batchSteps;
        }

        public void setBatchSteps(List<BatchStepInfo> batchSteps) {
            this.batchSteps = batchSteps;
        }

        public List<WorkflowStep> getJoinSteps() {
            return joinSteps;
        }

        public void setJoinSteps(List<WorkflowStep> joinSteps) {
            this.joinSteps = joinSteps;
        }

        public boolean isHasJoinSteps() {
            return hasJoinSteps;
        }

        public void setHasJoinSteps(boolean hasJoinSteps) {
            this.hasJoinSteps = hasJoinSteps;
        }
    }
}

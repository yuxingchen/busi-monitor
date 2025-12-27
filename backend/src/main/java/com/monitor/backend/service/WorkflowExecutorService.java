package com.monitor.backend.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.monitor.backend.component.MemoryJoinExecutor;
import com.monitor.backend.component.WorkflowSqlParser;
import com.monitor.backend.constant.BatchDefaults;
import com.monitor.backend.constant.ConfigKeys;
import com.monitor.backend.constant.ExecutionStatus;
import com.monitor.backend.constant.JoinType;
import com.monitor.backend.constant.LoopContextKeys;
import com.monitor.backend.constant.LoopSourceType;
import com.monitor.backend.constant.SystemFields;
import com.monitor.backend.constant.WorkflowStepType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitor.backend.entity.Workflow;
import com.monitor.backend.entity.WorkflowExecution;
import com.monitor.backend.entity.WorkflowStep;
import com.monitor.backend.mapper.WorkflowExecutionMapper;
import com.monitor.backend.mapper.WorkflowMapper;
import com.monitor.backend.mapper.WorkflowStepMapper;
import com.monitor.backend.component.WorkflowSqlParser.JoinClause;
import com.monitor.backend.component.WorkflowSqlParser.JoinCondition;
import com.monitor.backend.component.WorkflowSqlParser.ParseResult;
import com.monitor.backend.component.WorkflowSqlParser.TableRef;

/**
 * 工作流执行服务
 * <p>
 * 负责加载、执行工作流，处理步骤间的变量传递，并将结果写入结果表。
 * </p>
 * 
 * <h3>核心功能：</h3>
 * <ul>
 * <li>支持内存JOIN：当SQL中引用上游变量时，自动解析并执行内存JOIN</li>
 * <li><b>自动索引检测</b>：分析SQL中的WHERE/GROUP BY/ORDER BY子句，自动创建索引</li>
 * <li>手动模式优先：如果用户配置了indexFields，则使用用户指定的字段</li>
 * </ul>
 *
 * @author Monitor System
 * @since 1.0
 */
@Service
public class WorkflowExecutorService {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowExecutorService.class);

    private final WorkflowMapper workflowMapper;
    private final WorkflowStepMapper stepMapper;
    private final WorkflowExecutionMapper executionMapper;
    private final DynamicSqlExecutor sqlExecutor;
    private final DynamicTableService tableService;
    private final WorkflowSqlParser sqlParser;
    private final MemoryJoinExecutor joinExecutor;
    private final IndexFieldCollector indexFieldCollector;
    private final ObjectMapper objectMapper;

    // Spring Batch 组件（可选注入，避免未配置时启动失败）
    @Autowired(required = false)
    @Qualifier("asyncJobLauncher")
    private JobLauncher asyncJobLauncher;

    @Autowired(required = false)
    @Qualifier("workflowDataExtractionJob")
    private Job dataExtractionJob;

    // 批处理步骤解析器（可选注入）
    @Autowired(required = false)
    private com.monitor.backend.batch.BatchStepResolver batchStepResolver;

    // 中间存储服务（可选注入）
    @Autowired(required = false)
    private IntermediateStorageService intermediateStorage;

    public WorkflowExecutorService(WorkflowMapper workflowMapper, WorkflowStepMapper stepMapper,
            WorkflowExecutionMapper executionMapper, DynamicSqlExecutor sqlExecutor,
            DynamicTableService tableService, WorkflowSqlParser sqlParser,
            MemoryJoinExecutor joinExecutor, IndexFieldCollector indexFieldCollector) {
        this.workflowMapper = workflowMapper;
        this.stepMapper = stepMapper;
        this.executionMapper = executionMapper;
        this.sqlExecutor = sqlExecutor;
        this.tableService = tableService;
        this.sqlParser = sqlParser;
        this.joinExecutor = joinExecutor;
        this.indexFieldCollector = indexFieldCollector;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 执行工作流（入口方法）
     * 
     * 根据工作流配置的 batchMode 字段选择执行路径：
     * - batchMode = 0：普通模式，顺序执行各步骤
     * - 检查任意步骤开启了 batchEnabled：批处理模式，使用 Spring Batch 分区并行执行
     * 
     * 注意：此方法不使用 @Transactional，因为 Spring Batch 5.x 的 JobRepository
     * 要求在启动 Job 时不能存在外部事务，否则会抛出 IllegalStateException。
     * 数据库操作（如 executionMapper.insert/update）使用独立事务。
     * 
     * @param workflowId 工作流ID
     * @return 执行记录
     */
    public WorkflowExecution execute(Long workflowId) {
        // 1. 加载工作流
        Workflow workflow = workflowMapper.findById(workflowId);
        if (workflow == null) {
            throw new RuntimeException("Workflow not found: " + workflowId);
        }

        // 2. 加载步骤，检查是否有批处理步骤
        List<WorkflowStep> steps = stepMapper.findByWorkflowId(workflowId);
        boolean hasBatchStep = steps.stream()
                .anyMatch(s -> s.getBatchEnabled() != null && s.getBatchEnabled() == 1);

        if (hasBatchStep) {
            logger.info("工作流 {} 包含批处理步骤，使用分阶段执行模式", workflowId);
            return executeBatchMode(workflow, steps);
        }

        // 普通模式执行
        logger.info("工作流 {} 使用普通模式执行", workflowId);
        return executeNormalMode(workflow);
    }

    /**
     * 批处理模式执行（分层+混合执行）
     * <p>
     * 使用 Spring Batch 框架执行工作流，支持三种执行模式：
     * <ul>
     *   <li>PURE_BATCH: 纯批处理，无依赖步骤直接使用Spring Batch分区查询</li>
     *   <li>CONTEXT_BATCH: 混合模式，变量从context获取 + 真实表批量查询 + 内存JOIN</li>
     *   <li>CONTEXT_ONLY: 纯内存模式，仅依赖上游变量，使用parallelStream处理</li>
     * </ul>
     * </p>
     * <p>
     * 执行流程：
     * <ol>
     *   <li>分析步骤依赖关系</li>
     *   <li>按依赖层级分组</li>
     *   <li>分层执行：无依赖步骤优先，有依赖步骤等待上层完成</li>
     *   <li>根据步骤的ExecutionMode选择执行策略</li>
     * </ol>
     * </p>
     * 
     * @param workflow 工作流配置
     * @param steps 工作流步骤列表
     * @return 执行记录
     */
    private WorkflowExecution executeBatchMode(Workflow workflow, List<WorkflowStep> steps) {
        // 检查 Spring Batch 组件是否可用
        if (asyncJobLauncher == null || dataExtractionJob == null) {
            logger.error("Spring Batch 组件未配置，无法执行批处理模式");
            throw new RuntimeException("Spring Batch 组件未配置，请检查批处理配置或切换到普通模式");
        }

        // 检查 BatchStepResolver 是否可用
        if (batchStepResolver == null) {
            logger.error("BatchStepResolver 未注入，无法解析工作流步骤");
            throw new RuntimeException("BatchStepResolver 未配置");
        }

        // 创建执行记录
        WorkflowExecution execution = new WorkflowExecution();
        execution.setWorkflowId(workflow.getId());
        execution.setStatus(ExecutionStatus.BATCH_STARTING.getCode());
        execution.setStartTime(LocalDateTime.now());
        executionMapper.insert(execution);

        try {
            // ===== 1. 分析步骤依赖关系 =====
            Map<String, com.monitor.backend.batch.BatchStepResolver.StepDependencyInfo> depMap = 
                    batchStepResolver.analyzeDependencies(steps);
            
            // 按依赖层级分组
            List<List<WorkflowStep>> layers = batchStepResolver.groupByDependencyLevel(steps, depMap);
            logger.info("工作流分层完成: 共{}层", layers.size());
            
            // ===== 2. 执行上下文（存储各步骤结果） =====
            Map<String, Object> context = new HashMap<>();
            List<Map<String, Object>> lastResult = null;
            String outputTable = getOutputTableName(workflow);
            
            // ===== 3. 分层执行 =====
            for (int layerIndex = 0; layerIndex < layers.size(); layerIndex++) {
                List<WorkflowStep> layer = layers.get(layerIndex);
                logger.info("开始执行第{}层，共{}个步骤", layerIndex, layer.size());
                
                // 收集当前可用的变量名（所有上游步骤的resultVariable）
                // 注意：这里使用Set引用，后续步骤执行后会动态添加新变量
                Set<String> availableVars = new HashSet<>(context.keySet());
                logger.info("层{}开始时可用变量: {}", layerIndex, availableVars);
                
                for (WorkflowStep step : layer) {
                    // 跳过非批处理步骤或非SQL类型
                    if (step.getBatchEnabled() == null || step.getBatchEnabled() != 1) {
                        // 非批处理步骤：使用常规执行
                        lastResult = executeStep(step, context);
                        if (step.getResultVariable() != null) {
                            context.put(step.getResultVariable(), lastResult);
                            // 动态更新可用变量集合，供同层后续步骤使用
                            availableVars.add(step.getResultVariable());
                        }
                        continue;
                    }
                    
                    // 分析步骤执行模式
                    com.monitor.backend.batch.BatchStepResolver.BatchStepInfo stepInfo = 
                            batchStepResolver.analyzeWithContext(step, availableVars);
                    
                    logger.info("步骤[{}] 执行模式: {}, 变量依赖: {}, 真实表: {}", 
                            step.getName(), stepInfo.getExecutionMode(), 
                            stepInfo.getContextVariables(), stepInfo.getRealTables());
                    
                    // 根据执行模式选择策略
                    lastResult = switch (stepInfo.getExecutionMode()) {
                        case PURE_BATCH ->
                            // 纯批处理：无依赖，启动Spring Batch Job
                                executePureBatch(stepInfo, execution, workflow);
                        case CONTEXT_BATCH ->
                            // 混合模式：变量从context获取 + 真实表批量查询 + 内存JOIN
                                executeContextBatch(stepInfo, step, context);
                        case CONTEXT_ONLY ->
                            // 纯内存模式：降级到常规执行
                                executeStep(step, context);
                        default -> executeStep(step, context);
                    };
                    
                    // 存入context供后续步骤使用
                    if (step.getResultVariable() != null && lastResult != null) {
                        context.put(step.getResultVariable(), lastResult);
                        // 动态更新可用变量集合，供同层后续步骤使用
                        availableVars.add(step.getResultVariable());
                        logger.info("步骤[{}]执行完成，添加变量: {}", step.getName(), step.getResultVariable());
                    }
                }
            }
            
            // ===== 4. 写入结果表 =====
            if (lastResult != null && !lastResult.isEmpty()) {
                writeToResultTable(lastResult, outputTable, execution.getId());
                createResultTableIndexes(workflow, steps, lastResult, outputTable);
            }
            
            // 更新执行状态
            execution.setStatus(ExecutionStatus.SUCCESS.getCode());
            execution.setEndTime(LocalDateTime.now());
            Map<String, Object> resultInfo = new HashMap<>();
            resultInfo.put("layerCount", layers.size());
            resultInfo.put("totalSteps", steps.size());
            resultInfo.put("resultCount", lastResult != null ? lastResult.size() : 0);
            execution.setStepResults(objectMapper.writeValueAsString(resultInfo));
            executionMapper.update(execution);

            logger.info("批处理执行完成: workflowId={}, executionId={}, resultCount={}",
                    workflow.getId(), execution.getId(), lastResult != null ? lastResult.size() : 0);

        } catch (Exception e) {
            logger.error("批处理执行失败: " + workflow.getId(), e);
            execution.setStatus(ExecutionStatus.FAILED.getCode());
            execution.setEndTime(LocalDateTime.now());
            execution.setErrorMessage("批处理失败: " + e.getMessage());
            executionMapper.update(execution);
        }

        return execution;
    }
    
    /**
     * 纯批处理执行：无变量依赖，直接使用Spring Batch分区查询
     * 
     * @param stepInfo 步骤批处理信息
     * @param execution 执行记录
     * @param workflow 工作流配置
     * @return 查询结果列表
     */
    private List<Map<String, Object>> executePureBatch(
            com.monitor.backend.batch.BatchStepResolver.BatchStepInfo stepInfo,
            WorkflowExecution execution,
            Workflow workflow) throws Exception {
        
        logger.info("执行纯批处理: step={}, table={}", stepInfo.getStepName(), stepInfo.getTableName());
        
        // 构建Spring Batch Job参数
        JobParameters params = new JobParametersBuilder()
                .addLong("workflowId", workflow.getId())
                .addLong("executionId", execution.getId())
                .addLong("datasourceId", stepInfo.getDatasourceId() != null ? stepInfo.getDatasourceId() : 0L)
                .addString("tableName", stepInfo.getTableName())
                .addString("idColumn", stepInfo.getIdColumn())
                .addString("sql", stepInfo.getPartitionedSql())
                .addString("originalSql", stepInfo.getOriginalSql())
                .addString("cacheKey", stepInfo.getCacheKey())
                .addString("cacheStrategy", stepInfo.getCacheStrategy())
                .addLong("partitionCount", stepInfo.getPartitionCount().longValue())
                .addLong("chunkSize", stepInfo.getChunkSize().longValue())
                .addString("outputTable", getOutputTableName(workflow))
                .addLocalDateTime("startTime", LocalDateTime.now())
                .toJobParameters();
        
        // 同步执行Spring Batch Job
        org.springframework.batch.core.JobExecution jobExecution = asyncJobLauncher.run(dataExtractionJob, params);
        
        // 等待Job完成
        while (jobExecution.isRunning()) {
            Thread.sleep(100);
        }
        
        // 从缓存读取结果
        if (intermediateStorage != null) {
            return intermediateStorage.read(stepInfo.getCacheKey());
        }
        
        return new ArrayList<>();
    }
    
    /**
     * 混合批处理执行：变量表从context获取 + 真实表批量查询 + 内存JOIN
     * <p>
     * 执行流程：
     * <ol>
     *   <li>从context获取变量表数据（上游步骤的resultVariable）</li>
     *   <li>对真实表使用Spring Batch分区查询</li>
     *   <li>使用JoinExecutor在内存中执行JOIN</li>
     * </ol>
     * </p>
     * 
     * @param stepInfo 步骤批处理信息
     * @param step 工作流步骤
     * @param context 执行上下文（包含变量表数据）
     * @return JOIN结果列表
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> executeContextBatch(
            com.monitor.backend.batch.BatchStepResolver.BatchStepInfo stepInfo,
            WorkflowStep step,
            Map<String, Object> context) throws Exception {
        
        logger.info("执行混合批处理: step={}, contextVars={}, realTables={}", 
                step.getName(), stepInfo.getContextVariables(), stepInfo.getRealTables());
        
        // ===== 1. 获取变量表数据（从context） =====
        Map<String, List<Map<String, Object>>> varData = new HashMap<>();
        for (String varName : stepInfo.getContextVariables()) {
            Object data = context.get(varName);
            if (data instanceof List) {
                varData.put(varName, (List<Map<String, Object>>) data);
                logger.info("从context获取变量[{}]: {}条数据", varName, ((List<?>) data).size());
            } else {
                logger.warn("变量[{}]不是List类型，跳过", varName);
            }
        }
        
        if (varData.isEmpty()) {
            logger.warn("无法获取变量数据，降级到常规执行");
            return executeStep(step, context);
        }
        
        // ===== 2. 批量查询真实表 =====
        Map<String, List<Map<String, Object>>> tableData = new HashMap<>();
        for (String tableName : stepInfo.getRealTables()) {
            // 构建单表查询SQL
            String singleTableSql = "SELECT * FROM " + tableName;
            
            // 使用已有的sqlExecutor执行查询
            List<Map<String, Object>> data = sqlExecutor.executeQuery(
                    stepInfo.getDatasourceId(), singleTableSql);
            tableData.put(tableName, data);
            logger.info("批量查询表[{}]: {}条数据", tableName, data.size());
        }
        
        // ===== 3. 执行内存JOIN =====
        // 解析SQL获取JOIN信息
        Set<String> contextVariableNames = new HashSet<>(context.keySet());
        ParseResult parseResult = sqlParser.parse(step.getSqlScript(), contextVariableNames);
        
        // 获取主表数据
        String mainTableName = parseResult.getVariableTables().isEmpty() 
                ? parseResult.getRealTables().get(0).getTableName()
                : parseResult.getVariableTables().get(0).getTableName();
        
        List<Map<String, Object>> leftData = varData.containsKey(mainTableName) 
                ? varData.get(mainTableName) 
                : tableData.get(mainTableName);
        
        if (leftData == null) {
            logger.error("无法获取主表[{}]数据", mainTableName);
            return new ArrayList<>();
        }
        
        // 依次执行每个JOIN
        List<Map<String, Object>> result = new ArrayList<>(leftData);
        for (JoinClause join : parseResult.getJoins()) {
            String rightTableName = join.getTable().getTableName();
            List<Map<String, Object>> rightData = varData.containsKey(rightTableName) 
                    ? varData.get(rightTableName) 
                    : tableData.get(rightTableName);
            
            if (rightData == null) {
                logger.warn("无法获取右表[{}]数据，跳过JOIN", rightTableName);
                continue;
            }
            
            // 执行JOIN
            if (!join.getConditions().isEmpty()) {
                JoinCondition cond = join.getConditions().get(0);
                result = performMemoryJoin(result, rightData, 
                        cond.getLeftField(), cond.getRightField(), 
                        join.getJoinType(),
                        parseResult.getSelectFields(),
                        parseResult.getFieldAliasMap());
            }
        }
        
        logger.info("混合批处理完成: step={}, resultCount={}", step.getName(), result.size());
        return result;
    }
    
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
    private List<Map<String, Object>> performMemoryJoin(
            List<Map<String, Object>> leftData,
            List<Map<String, Object>> rightData,
            String leftKey,
            String rightKey,
            String joinType,
            List<String> selectFields,
            Map<String, String> aliasMap) {
        
        // 构建右表索引（提升JOIN性能）
        Map<Object, List<Map<String, Object>>> rightIndex = new HashMap<>();
        for (Map<String, Object> row : rightData) {
            Object key = getFieldValue(row, rightKey);
            rightIndex.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }
        
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
        }
        
        return result;
    }
    
    /**
     * 获取字段值（支持别名前缀）
     */
    private Object getFieldValue(Map<String, Object> row, String fieldName) {
        // 移除可能的别名前缀
        String cleanField = fieldName.contains(".") 
                ? fieldName.substring(fieldName.indexOf(".") + 1) 
                : fieldName;
        return row.get(cleanField);
    }
    
    /**
     * 根据SELECT字段过滤并应用别名
     */
    private Map<String, Object> filterFields(Map<String, Object> row, 
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
    
    /**
     * 将结果写入结果表（用于批处理执行后的结果存储）
     * 
     * @param data 要写入的数据列表
     * @param tableName 目标表名
     * @param executionId 执行ID
     */
    private void writeToResultTable(List<Map<String, Object>> data, String tableName, Long executionId) {
        if (data == null || data.isEmpty()) {
            logger.warn("writeToResultTable: 没有数据需要写入");
            return;
        }
        
        logger.info("批处理结果准备写入: tableName={}, count={}", tableName, data.size());
        
        try {
            // 调用tableService将数据写入结果表
            tableService.saveWorkflowResult(tableName, data, executionId);
            logger.info("已保存 {} 行数据到结果表: {}", data.size(), tableName);
        } catch (Exception e) {
            logger.error("写入结果表失败: tableName={}, error={}", tableName, e.getMessage(), e);
            throw new RuntimeException("写入结果表失败: " + e.getMessage(), e);
        }
    }

    /**
     * 普通模式执行（原有逻辑）
     * 
     * 顺序执行工作流的各个步骤，支持内存JOIN和步骤间变量传递。
     * 
     * @param workflow 工作流配置
     * @return 执行记录
     */
    private WorkflowExecution executeNormalMode(Workflow workflow) {
        Long workflowId = workflow.getId();

        // 加载步骤
        List<WorkflowStep> steps = stepMapper.findByWorkflowId(workflowId);
        if (steps.isEmpty()) {
            throw new RuntimeException("Workflow has no steps: " + workflowId);
        }

        // 3. 创建执行记录
        WorkflowExecution execution = new WorkflowExecution();
        execution.setWorkflowId(workflowId);
        execution.setStatus(ExecutionStatus.RUNNING.getCode());
        execution.setStartTime(LocalDateTime.now());
        executionMapper.insert(execution);

        // 4. 执行上下文
        Map<String, Object> context = new HashMap<>();
        Map<String, Object> stepResults = new LinkedHashMap<>();

        try {
            // 5. 按顺序执行步骤
            List<Map<String, Object>> lastResult = null;
            for (WorkflowStep step : steps) {
                logger.info("Executing step: {} ({})", step.getName(), step.getStepType());

                List<Map<String, Object>> result = executeStep(step, context);

                // 保存到上下文，供后续步骤引用
                if (step.getResultVariable() != null && !step.getResultVariable().isEmpty()) {
                    context.put(step.getResultVariable(), result);
                }

                // 记录步骤结果摘要
                Map<String, Object> stepSummary = new HashMap<>();
                stepSummary.put("rowCount", result != null ? result.size() : 0);
                stepSummary.put("status", ExecutionStatus.SUCCESS.getCode());
                stepResults.put(step.getName(), stepSummary);

                lastResult = result;
            }

            // 6. 将最终结果写入结果表
            if (lastResult != null && !lastResult.isEmpty()) {
                String outputTable = getOutputTableName(workflow);
                tableService.saveWorkflowResult(outputTable, lastResult, execution.getId());
                logger.info("已保存 {} 行数据到结果表: {}", lastResult.size(), outputTable);

                // 7. 创建索引（手动模式优先，自动模式备选）
                createResultTableIndexes(workflow, steps, lastResult, outputTable);
            }

            // 7. 更新执行状态为成功
            execution.setStatus(ExecutionStatus.SUCCESS.getCode());
            execution.setEndTime(LocalDateTime.now());
            execution.setStepResults(objectMapper.writeValueAsString(stepResults));
            executionMapper.update(execution);

            logger.info("Workflow {} executed successfully, execution id: {}", workflowId, execution.getId());

        } catch (Exception e) {
            logger.error("Workflow execution failed: " + workflowId, e);

            execution.setStatus(ExecutionStatus.FAILED.getCode());
            execution.setEndTime(LocalDateTime.now());
            execution.setErrorMessage(e.getMessage());
            try {
                execution.setStepResults(objectMapper.writeValueAsString(stepResults));
            } catch (Exception ignored) {
            }
            executionMapper.update(execution);
        }

        return execution;
    }

    /**
     * 异步执行工作流
     */
    @Async
    public void executeAsync(Long workflowId) {
        execute(workflowId);
    }

    /**
     * 执行单个步骤
     * 支持内存JOIN：当SQL中引用上游变量时，自动解析并执行内存JOIN
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> executeStep(WorkflowStep step, Map<String, Object> context) {
        String stepType = step.getStepType();

        // 处理 CONSTANT 步骤：解析常量配置并放入上下文
        if (WorkflowStepType.CONSTANT.matches(stepType)) {
            return executeConstantStep(step, context);
        }

        // 处理 LOOP 步骤：循环执行子SQL
        if (WorkflowStepType.LOOP.matches(stepType)) {
            return executeLoopStep(step, context);
        }

        if (!WorkflowStepType.SQL.matches(stepType)) {
            if (WorkflowStepType.TASK_REF.matches(stepType)) {
                throw new UnsupportedOperationException("TASK_REF step type not implemented yet");
            }
            throw new RuntimeException("Unknown step type: " + stepType);
        }

        String sql = step.getSqlScript();

        // 1. 解析SQL，识别变量表
        Set<String> contextVariables = context.keySet();
        ParseResult parseResult = sqlParser.parse(sql, contextVariables);

        List<TableRef> variableTables = parseResult.getVariableTables();
        List<TableRef> realTables = parseResult.getRealTables();

        // 2. 如果没有变量表，直接执行SQL
        if (variableTables.isEmpty()) {
            return sqlExecutor.executeQueryWithContext(
                    step.getDatasourceId(),
                    sql,
                    context);
        }

        logger.info("Detected memory JOIN: variables={}, realTables={}",
                variableTables, realTables);

        // 调试：打印上下文中的变量
        logger.debug("Context keys: {}", context.keySet());

        // 3. 有变量表，执行内存JOIN
        // 获取主表数据（变量或真实表）
        List<Map<String, Object>> leftData;
        String leftAlias;

        if (parseResult.getMainTable().isVariable()) {
            // 主表是变量，从上下文获取
            String varName = parseResult.getMainTable().getTableName();
            leftData = (List<Map<String, Object>>) context.get(varName);
            leftAlias = parseResult.getMainTable().getAlias();

            if (leftData == null) {
                logger.error("Variable '{}' not found in context! Available: {}", varName, context.keySet());
                throw new RuntimeException("变量 '" + varName + "' 在上下文中不存在，请确保上游节点已正确执行");
            }
            logger.debug("Got {} rows from variable '{}'", leftData.size(), varName);
        } else {
            // 主表是真实表，执行查询
            String mainTableSql = "SELECT * FROM " + parseResult.getMainTable().getTableName();
            leftData = sqlExecutor.executeQuery(step.getDatasourceId(), mainTableSql);
            leftAlias = parseResult.getMainTable().getAlias();
        }

        // 4. 对每个JOIN子句执行
        List<Map<String, Object>> result = leftData;
        logger.debug("Number of JOINs to process: {}", parseResult.getJoins().size());

        for (JoinClause join : parseResult.getJoins()) {
            logger.debug("Processing JOIN: {} {} {}", join.getJoinType(), join.getTable(), join.getConditions());

            List<Map<String, Object>> rightData;
            String rightAlias = join.getTable().getAlias();

            if (join.getTable().isVariable()) {
                // JOIN的是变量表
                rightData = (List<Map<String, Object>>) context.get(join.getTable().getTableName());
            } else {
                // JOIN的是真实表，执行查询
                String joinTableSql = "SELECT * FROM " + join.getTable().getTableName();
                logger.debug("Executing query for right table: {}", joinTableSql);
                rightData = sqlExecutor.executeQuery(step.getDatasourceId(), joinTableSql);
            }

            if (rightData == null) {
                logger.warn("No data for join table: {}", join.getTable().getTableName());
                continue;
            }
            logger.debug("Right table has {} rows", rightData.size());

            // 获取关联字段
            if (!join.getConditions().isEmpty()) {
                JoinCondition cond = join.getConditions().get(0);
                String leftKey = cond.getLeftField();
                String rightKey = cond.getRightField();

                logger.debug("JOIN on: {}.{} = {}.{}", leftAlias, leftKey, rightAlias, rightKey);

                // 执行内存JOIN
                result = joinExecutor.join(
                        result, rightData,
                        leftAlias, rightAlias,
                        leftKey, rightKey,
                        join.getJoinType(),
                        parseResult.getSelectFields(),
                        parseResult.getFieldAliasMap());
                logger.debug("JOIN result: {} rows", result.size());
            } else {
                logger.warn("No join conditions found for table: {}", join.getTable().getTableName());
            }
        }

        return result;
    }

    /**
     * 获取工作流输出表名
     */
    private String getOutputTableName(Workflow workflow) {
        if (workflow.getOutputTable() != null && !workflow.getOutputTable().isEmpty()) {
            return workflow.getOutputTable();
        }
        return DynamicTableService.getWorkflowResultTableName(workflow.getId());
    }

    /**
     * 测试执行步骤（不保存结果）
     */
    public List<Map<String, Object>> testExecuteStep(WorkflowStep step, Map<String, Object> context) {
        return executeStep(step, context);
    }

    /**
     * 获取工作流最新结果
     */
    public List<Map<String, Object>> getLatestResult(Long workflowId) {
        Workflow workflow = workflowMapper.findById(workflowId);
        if (workflow == null) {
            return List.of();
        }
        String tableName = getOutputTableName(workflow);
        return tableService.queryLatestWorkflowResult(tableName);
    }

    /**
     * 解析索引字段配置
     * <p>
     * 支持两种格式：
     * <ul>
     * <li>JSON数组格式：["field1", "field2"]</li>
     * <li>逗号分隔格式：field1,field2,field3</li>
     * </ul>
     * </p>
     * 
     * @param indexFieldsConfig 索引字段配置字符串
     * @return 字段名列表
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
        String[] parts = trimmed.split(",");
        List<String> fields = new ArrayList<>();
        for (String part : parts) {
            String field = part.trim();
            if (!field.isEmpty()) {
                fields.add(field);
            }
        }
        return fields;
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
     * @param workflow    工作流配置
     * @param steps       工作流步骤列表
     * @param lastResult  最终执行结果
     * @param outputTable 输出表名
     */
    private void createResultTableIndexes(Workflow workflow, List<WorkflowStep> steps,
            List<Map<String, Object>> lastResult, String outputTable) {
        // 固定字段索引（始终创建）
        List<String> fixedIndexFields = List.of("execution_id", "execution_time");

        // 确定业务字段索引
        List<String> businessIndexFields;
        List<String> manualIndexFields = parseIndexFields(workflow.getIndexFields());
        boolean wasAutoDetected = false;

        if (!manualIndexFields.isEmpty()) {
            // 手动模式：使用用户指定的字段
            businessIndexFields = manualIndexFields;
            logger.info("使用手动配置的索引字段: {}", businessIndexFields);
        } else {
            // 自动模式：通过SQL分析检测
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
        logger.info("已创建索引: {} (共{})", allIndexFields, allIndexFields.size());

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
     * 执行常量步骤：解析 config 中的常量定义并放入上下文
     * config 格式: {"constants": {"key1": "value1", "key2": "value2"}}
     * 
     * 常量命名空间：使用步骤的 resultVariable 作为前缀
     * 例如：resultVariable="orderConstant", key="productIds"
     * 则存储为 "orderConstant.productIds"，SQL中使用 ${orderConstant.productIds}
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> executeConstantStep(WorkflowStep step, Map<String, Object> context) {
        String config = step.getConfig();
        if (config == null || config.isBlank()) {
            logger.warn("CONSTANT 步骤 {} 配置为空", step.getName());
            return List.of();
        }

        // 获取结果变量名作为常量命名空间，默认使用 "const"
        String namespace = step.getResultVariable();
        if (namespace == null || namespace.isBlank()) {
            namespace = "const";
        }

        try {
            Map<String, Object> configMap = objectMapper.readValue(config, Map.class);
            Map<String, Object> constants = (Map<String, Object>) configMap.get("constants");

            if (constants != null) {
                // 将常量放入上下文，使用 namespace.xxx 格式
                for (Map.Entry<String, Object> entry : constants.entrySet()) {
                    String key = namespace + "." + entry.getKey();
                    context.put(key, entry.getValue());
                    logger.debug("设置常量: {} = {}", key, entry.getValue());
                }
                logger.info("CONSTANT 步骤 {} 设置了 {} 个常量 (命名空间: {})", 
                        step.getName(), constants.size(), namespace);
            }
        } catch (Exception e) {
            logger.error("解析 CONSTANT 配置失败: {}", e.getMessage());
            throw new RuntimeException("解析常量配置失败: " + e.getMessage(), e);
        }

        // 常量步骤不产生结果数据
        return List.of();
    }

    /**
     * 执行循环步骤：根据配置循环执行 SQL
     * 两种模式:
     * 1. VARIABLE 模式: {"loopSource": "VARIABLE", "loopVariable": "stepName",
     * "loopField": "fieldName", "loopSql": "..."}
     * 2. CONSTANT 模式: {"loopSource": "CONSTANT", "constantValue": "a,b,c",
     * "separator": ",", "loopSql": "..."}
     * 
     * 批处理支持:
     * 如果 LOOP 步骤启用了批处理 (batchEnabled=1)，每次迭代的 SQL 将使用 Spring Batch 执行
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> executeLoopStep(WorkflowStep step, Map<String, Object> context) {
        String config = step.getConfig();
        if (config == null || config.isBlank()) {
            throw new RuntimeException("LOOP 步骤 " + step.getName() + " 配置为空");
        }

        try {
            Map<String, Object> configMap = objectMapper.readValue(config, Map.class);
            String loopSource = (String) configMap.getOrDefault("loopSource", "VARIABLE");
            String loopSql = (String) configMap.get("loopSql");

            if (loopSql == null || loopSql.isBlank()) {
                throw new RuntimeException("LOOP 步骤必须配置 loopSql");
            }

            // 检查是否启用批处理
            boolean useBatch = step.getBatchEnabled() != null && step.getBatchEnabled() == 1;
            if (useBatch) {
                logger.info("LOOP 步骤 {} 启用批处理模式", step.getName());
            }

            // 获取循环值列表
            List<Object> loopValues = new ArrayList<>();

            if ("CONSTANT".equals(loopSource)) {
                // 常量分割模式
                String constantValue = (String) configMap.get("constantValue");
                String separator = (String) configMap.getOrDefault("separator", ",");
                if (constantValue != null && !constantValue.isBlank()) {
                    for (String val : constantValue.split(separator)) {
                        loopValues.add(val.trim());
                    }
                }
            } else {
                // 变量遍历模式
                String loopVariable = (String) configMap.get("loopVariable");
                String loopField = (String) configMap.get("loopField");

                Object varData = context.get(loopVariable);
                if (varData instanceof List) {
                    List<Map<String, Object>> listData = (List<Map<String, Object>>) varData;
                    for (Map<String, Object> row : listData) {
                        Object fieldValue = row.get(loopField);
                        if (fieldValue != null) {
                            loopValues.add(fieldValue);
                        }
                    }
                }
            }

            logger.info("LOOP 步骤 {} 将执行 {} 次迭代{}", step.getName(), loopValues.size(), 
                    useBatch ? " (批处理模式)" : "");

            // 执行循环
            List<Map<String, Object>> allResults = new ArrayList<>();
            int total = loopValues.size();

            for (int i = 0; i < loopValues.size(); i++) {
                Object loopValue = loopValues.get(i);

                // 设置循环上下文变量
                context.put("loop.index", i);
                context.put("loop.value", loopValue);
                context.put("loop.total", total);

                // 替换 SQL 中的循环占位符
                String sql = loopSql
                        .replace("${loop.index}", String.valueOf(i))
                        .replace("${loop.value}", String.valueOf(loopValue))
                        .replace("${loop.total}", String.valueOf(total));

                logger.debug("LOOP 迭代 {}/{}: value={}, sql={}", i + 1, total, loopValue, sql);

                List<Map<String, Object>> iterResult;
                
                if (useBatch && asyncJobLauncher != null && dataExtractionJob != null) {
                    // 批处理模式：使用 Spring Batch 执行
                    iterResult = executeLoopIterationBatch(step, sql, i, loopValue);
                } else {
                    // 普通模式：直接执行 SQL
                    iterResult = sqlExecutor.executeQueryWithContext(
                            step.getDatasourceId(), sql, context);
                }

                allResults.addAll(iterResult);
            }

            // 清理循环变量
            context.remove("loop.index");
            context.remove("loop.value");
            context.remove("loop.total");

            logger.info("LOOP 步骤 {} 完成，共产生 {} 条结果", step.getName(), allResults.size());
            return allResults;

        } catch (Exception e) {
            logger.error("执行 LOOP 步骤失败: {}", e.getMessage());
            throw new RuntimeException("执行循环步骤失败: " + e.getMessage(), e);
        }
    }

    /**
     * 批处理模式执行单次循环迭代
     * 使用 LOOP 步骤的批处理配置参数
     */
    private List<Map<String, Object>> executeLoopIterationBatch(WorkflowStep step, String sql, int iteration, Object loopValue) {
        try {
            // 使用 BatchStepResolver 解析 SQL，获取表名和 ID 列
            // 注意：这里直接从 SQL 提取信息，使用 LOOP 步骤的批处理配置
            String tableName = extractTableNameFromSql(sql);
            String idColumn = step.getIdColumn() != null && !step.getIdColumn().isEmpty() 
                    ? step.getIdColumn() : "id";
            int partitionCount = step.getPartitionCount() != null ? step.getPartitionCount() : 10;
            int chunkSize = step.getChunkSize() != null ? step.getChunkSize() : 1000;
            String cacheStrategy = step.getCacheStrategy() != null ? step.getCacheStrategy() : "FILE";
            
            String cacheKey = String.format("loop_%d_step%d_iter%d", 
                    step.getWorkflowId(), step.getStepOrder(), iteration);

            logger.info("LOOP批处理迭代 {}: table={}, idColumn={}, partitions={}", 
                    iteration, tableName, idColumn, partitionCount);

            // 构建 Job 参数
            JobParameters params = new JobParametersBuilder()
                    .addLong("workflowId", step.getWorkflowId())
                    .addLong("stepId", step.getId())
                    .addLong("datasourceId", step.getDatasourceId() != null ? step.getDatasourceId() : 0L)
                    .addString("tableName", tableName)
                    .addString("idColumn", idColumn)
                    .addString("sql", sql)
                    .addString("cacheKey", cacheKey)
                    .addString("cacheStrategy", cacheStrategy)
                    .addLong("partitionCount", (long) partitionCount)
                    .addLong("chunkSize", (long) chunkSize)
                    .addString("loopValue", String.valueOf(loopValue))
                    .addLong("loopIteration", (long) iteration)
                    .addLocalDateTime("startTime", LocalDateTime.now())
                    .toJobParameters();

            // 同步执行（等待完成）- LOOP 迭代需要顺序完成
            org.springframework.batch.core.JobExecution jobExecution = asyncJobLauncher.run(dataExtractionJob, params);
            
            // 等待完成
            while (jobExecution.isRunning()) {
                Thread.sleep(100);
            }

            if (jobExecution.getStatus() == org.springframework.batch.core.BatchStatus.COMPLETED) {
                // 从缓存读取结果
                if (intermediateStorage != null) {
                    return intermediateStorage.read(cacheKey);
                }
            } else {
                logger.warn("LOOP批处理迭代 {} 未成功完成: {}", iteration, jobExecution.getStatus());
            }

            return List.of();
        } catch (Exception e) {
            logger.error("LOOP批处理迭代执行失败: {}", e.getMessage());
            // 降级为普通执行
            return sqlExecutor.executeQuery(step.getDatasourceId(), sql);
        }
    }

    /**
     * 从 SQL 中提取表名（简单实现）
     */
    private String extractTableNameFromSql(String sql) {
        String upperSql = sql.toUpperCase();
        int fromIndex = upperSql.indexOf("FROM ");
        if (fromIndex > 0) {
            String afterFrom = sql.substring(fromIndex + 5).trim();
            String[] parts = afterFrom.split("\\s+");
            if (parts.length > 0) {
                return parts[0].replace("`", "");
            }
        }
        return "unknown_table";
    }
}

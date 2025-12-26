package com.monitor.backend.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.monitor.backend.batch.SqlBatchAnalyzer;

import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.monitor.backend.entity.Workflow;
import com.monitor.backend.entity.WorkflowExecution;
import com.monitor.backend.entity.WorkflowStep;
import com.monitor.backend.mapper.WorkflowExecutionMapper;
import com.monitor.backend.mapper.WorkflowMapper;
import com.monitor.backend.mapper.WorkflowStepMapper;
import com.monitor.backend.service.WorkflowExecutorService;

/**
 * 工作流管理控制器
 */
@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final WorkflowMapper workflowMapper;
    private final WorkflowStepMapper stepMapper;
    private final WorkflowExecutionMapper executionMapper;
    private final WorkflowExecutorService executorService;
    private final com.monitor.backend.batch.SqlBatchAnalyzer sqlBatchAnalyzer;

    public WorkflowController(WorkflowMapper workflowMapper, WorkflowStepMapper stepMapper,
                              WorkflowExecutionMapper executionMapper, WorkflowExecutorService executorService,
                              com.monitor.backend.batch.SqlBatchAnalyzer sqlBatchAnalyzer) {
        this.workflowMapper = workflowMapper;
        this.stepMapper = stepMapper;
        this.executionMapper = executionMapper;
        this.executorService = executorService;
        this.sqlBatchAnalyzer = sqlBatchAnalyzer;
    }

    /**
     * 获取工作流列表
     */
    @GetMapping
    public ResponseEntity<List<Workflow>> list() {
        List<Workflow> workflows = workflowMapper.findAll();
        return ResponseEntity.ok(workflows);
    }

    /**
     * 获取工作流详情（含步骤）
     */
    @GetMapping("/{id}")
    public ResponseEntity<Workflow> getById(@PathVariable Long id) {
        Workflow workflow = workflowMapper.findById(id);
        if (workflow == null) {
            return ResponseEntity.notFound().build();
        }
        // 加载步骤
        workflow.setSteps(stepMapper.findByWorkflowId(id));
        return ResponseEntity.ok(workflow);
    }

    /**
     * 创建工作流
     */
    @PostMapping
    @Transactional
    public ResponseEntity<?> create(@RequestBody Workflow workflow) {
        // 校验批处理SQL支持性
        List<String> batchErrors = validateBatchSteps(workflow.getSteps());
        if (!batchErrors.isEmpty()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("batchErrors", batchErrors);
            errorResponse.put("message", "以下步骤的SQL不支持批处理，请关闭批处理或修改SQL");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        workflow.setCreateTime(LocalDateTime.now());
        workflow.setUpdateTime(LocalDateTime.now());
        if (workflow.getIsActive() == null) {
            workflow.setIsActive(1);
        }
        if (workflow.getTimeoutSeconds() == null) {
            workflow.setTimeoutSeconds(3600);
        }
        workflowMapper.insert(workflow);

        // 保存步骤
        if (workflow.getSteps() != null && !workflow.getSteps().isEmpty()) {
            for (WorkflowStep step : workflow.getSteps()) {
                step.setWorkflowId(workflow.getId());
                stepMapper.insert(step);
            }
        }

        return ResponseEntity.ok(workflow);
    }

    /**
     * 更新工作流
     */
    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Workflow workflow) {
        Workflow existing = workflowMapper.findById(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }

        // 校验批处理SQL支持性
        List<String> batchErrors = validateBatchSteps(workflow.getSteps());
        if (!batchErrors.isEmpty()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("batchErrors", batchErrors);
            errorResponse.put("message", "以下步骤的SQL不支持批处理，请关闭批处理或修改SQL");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        workflow.setId(id);
        workflow.setUpdateTime(LocalDateTime.now());
        // 如果前端没有传递 indexFields，保留数据库中已有的值（后端自动检测的结果）
        if (workflow.getIndexFields() == null || workflow.getIndexFields().isEmpty()) {
            workflow.setIndexFields(existing.getIndexFields());
        }
        workflowMapper.update(workflow);

        // 删除旧步骤，插入新步骤
        stepMapper.deleteByWorkflowId(id);
        if (workflow.getSteps() != null) {
            for (WorkflowStep step : workflow.getSteps()) {
                step.setWorkflowId(id);
                stepMapper.insert(step);
            }
        }

        return ResponseEntity.ok(workflow);
    }

    /**
     * 删除工作流
     */
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        stepMapper.deleteByWorkflowId(id);
        executionMapper.deleteByWorkflowId(id);
        workflowMapper.deleteById(id);
        return ResponseEntity.ok().build();
    }

    /**
     * 切换启用状态
     */
    @PatchMapping("/{id}/active")
    public ResponseEntity<Void> toggleActive(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        Integer isActive = body.get("isActive");
        workflowMapper.updateActiveStatus(id, isActive);
        return ResponseEntity.ok().build();
    }

    /**
     * 手动执行工作流
     */
    @PostMapping("/{id}/execute")
    public ResponseEntity<WorkflowExecution> execute(@PathVariable Long id) {
        WorkflowExecution execution = executorService.execute(id);
        return ResponseEntity.ok(execution);
    }

    /**
     * 获取执行历史
     */
    @GetMapping("/{id}/executions")
    public ResponseEntity<List<WorkflowExecution>> getExecutions(@PathVariable Long id,
                                                                 @RequestParam(defaultValue = "20") int limit) {
        List<WorkflowExecution> executions = executionMapper.findByWorkflowId(id, limit);
        return ResponseEntity.ok(executions);
    }

    /**
     * 获取工作流最新结果数据
     */
    @GetMapping("/{id}/result")
    public ResponseEntity<List<Map<String, Object>>> getResult(@PathVariable Long id) {
        List<Map<String, Object>> result = executorService.getLatestResult(id);
        return ResponseEntity.ok(result);
    }

    /**
     * 测试步骤 SQL
     * <p>
     * 功能增强：
     * 1. 支持传入 workflowId 和 stepOrder，会先执行前置步骤构建上下文
     * 2. 返回数据量信息，超过阈值时添加警告标识
     *
     * @param request 请求参数，包含
     *                workflowId、stepOrder、name、stepType、datasourceId、sqlScript、resultVariable
     * @return 响应Map，包含 data（结果数据）、rowCount（行数）、warning（警告标识）、message（警告消息）
     */
    @PostMapping("/test-step")
    public ResponseEntity<Map<String, Object>> testStep(@RequestBody Map<String, Object> request) {
        // 解析请求参数
        Long workflowId = request.get("workflowId") != null ? Long.valueOf(request.get("workflowId").toString()) : null;
        Integer stepOrder = request.get("stepOrder") != null ? Integer.valueOf(request.get("stepOrder").toString())
                : null;

        // 构建 WorkflowStep
        WorkflowStep step = new WorkflowStep();
        step.setName((String) request.get("name"));
        step.setStepType((String) request.getOrDefault("stepType", "SQL"));
        step.setDatasourceId(
                request.get("datasourceId") != null ? Long.valueOf(request.get("datasourceId").toString()) : null);
        step.setSqlScript((String) request.get("sqlScript"));
        step.setResultVariable((String) request.get("resultVariable"));
        step.setStepOrder(stepOrder != null ? stepOrder : 0);

        // 如果有 workflowId 和 stepOrder，先执行前置步骤构建上下文
        Map<String, Object> context = new java.util.HashMap<>();
        if (workflowId != null && stepOrder != null && stepOrder > 0) {
            List<WorkflowStep> previousSteps = stepMapper.findByWorkflowId(workflowId);
            for (WorkflowStep prevStep : previousSteps) {
                if (prevStep.getStepOrder() < stepOrder) {
                    try {
                        List<Map<String, Object>> result = executorService.testExecuteStep(prevStep, context);
                        if (prevStep.getResultVariable() != null && !prevStep.getResultVariable().isEmpty()) {
                            context.put(prevStep.getResultVariable(), result);
                        }
                    } catch (Exception e) {
                        // 返回错误信息
                        Map<String, Object> errorResponse = new java.util.HashMap<>();
                        errorResponse.put("data",
                                List.of(Map.of("error", "执行前置节点 " + prevStep.getName() + " 失败: " + e.getMessage())));
                        errorResponse.put("rowCount", 1);
                        return ResponseEntity.badRequest().body(errorResponse);
                    }
                }
            }
        }

        // 执行当前步骤
        List<Map<String, Object>> result = executorService.testExecuteStep(step, context);

        // 构建响应：包含数据、行数和警告信息
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("data", result);
        response.put("rowCount", result.size());

        // 数据量阈值检测：超过10万条时添加警告
        final int DATA_VOLUME_THRESHOLD = 100000;
        if (result.size() > DATA_VOLUME_THRESHOLD) {
            response.put("warning", "DATA_VOLUME_HIGH");
            response.put("message", "数据量超过 " + DATA_VOLUME_THRESHOLD + " 条，建议使用批处理模式");
        }

        return ResponseEntity.ok(response);
    }

    /**
     * 校验批处理步骤的SQL兼容性
     */
    private List<String> validateBatchSteps(List<WorkflowStep> steps) {
        List<String> errors = new ArrayList<>();
        if (steps == null || steps.isEmpty()) {
            return errors;
        }

        for (WorkflowStep step : steps) {
            // 只校验启用批处理的SQL步骤
            if (step.getBatchEnabled() != null && step.getBatchEnabled() == 1
                    && ("SQL".equals(step.getStepType()) || "LOOP".equals(step.getStepType()))
                    && step.getSqlScript() != null) {

                SqlBatchAnalyzer.AnalysisResult result = sqlBatchAnalyzer.analyze(step.getSqlScript());
                if (!result.isSupported()) {
                    errors.add(String.format("步骤[%s]: %s", step.getName(), result.getReason()));
                }
            }
        }
        return errors;
    }
}

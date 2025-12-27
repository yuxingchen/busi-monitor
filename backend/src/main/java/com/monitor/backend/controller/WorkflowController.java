package com.monitor.backend.controller;

import com.monitor.backend.batch.SqlBatchAnalyzer;
import com.monitor.backend.common.ApiResponse;
import com.monitor.backend.constant.WorkflowStepType;
import com.monitor.backend.entity.Workflow;
import com.monitor.backend.entity.WorkflowExecution;
import com.monitor.backend.entity.WorkflowStep;
import com.monitor.backend.exception.BusinessException;
import com.monitor.backend.exception.ErrorCode;
import com.monitor.backend.mapper.WorkflowExecutionMapper;
import com.monitor.backend.mapper.WorkflowMapper;
import com.monitor.backend.mapper.WorkflowStepMapper;
import com.monitor.backend.service.WorkflowExecutorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流管理控制器
 *
 * @author monitor-system
 */
@Tag(name = "工作流管理", description = "工作流的增删改查、执行和测试")
@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private static final Logger log = LoggerFactory.getLogger(WorkflowController.class);

    private final WorkflowMapper workflowMapper;
    private final WorkflowStepMapper stepMapper;
    private final WorkflowExecutionMapper executionMapper;
    private final WorkflowExecutorService executorService;
    private final SqlBatchAnalyzer sqlBatchAnalyzer;

    public WorkflowController(WorkflowMapper workflowMapper, WorkflowStepMapper stepMapper,
                              WorkflowExecutionMapper executionMapper, WorkflowExecutorService executorService,
                              SqlBatchAnalyzer sqlBatchAnalyzer) {
        this.workflowMapper = workflowMapper;
        this.stepMapper = stepMapper;
        this.executionMapper = executionMapper;
        this.executorService = executorService;
        this.sqlBatchAnalyzer = sqlBatchAnalyzer;
    }

    @Operation(summary = "获取工作流列表")
    @GetMapping
    public ApiResponse<List<Workflow>> list() {
        return ApiResponse.ok(workflowMapper.findAll());
    }

    @Operation(summary = "获取工作流详情（含步骤）")
    @GetMapping("/{id}")
    public ApiResponse<Workflow> getById(
            @Parameter(description = "工作流ID") @PathVariable Long id) {
        Workflow workflow = workflowMapper.findById(id);
        if (workflow == null) {
            throw new BusinessException(ErrorCode.WORKFLOW_NOT_FOUND);
        }
        workflow.setSteps(stepMapper.findByWorkflowId(id));
        return ApiResponse.ok(workflow);
    }

    @Operation(summary = "创建工作流")
    @PostMapping
    @Transactional
    public ApiResponse<Workflow> create(@RequestBody Workflow workflow) {
        // 校验批处理SQL支持性
        List<String> batchErrors = validateBatchSteps(workflow.getSteps());
        if (!batchErrors.isEmpty()) {
            return ApiResponse.fail(ErrorCode.WORKFLOW_STEP_INVALID,
                    "以下步骤的SQL不支持批处理: " + String.join("; ", batchErrors));
        }

        log.info("创建工作流: name={}", workflow.getName());
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

        return ApiResponse.ok("创建成功", workflow);
    }

    @Operation(summary = "更新工作流")
    @PutMapping("/{id}")
    @Transactional
    public ApiResponse<Workflow> update(
            @Parameter(description = "工作流ID") @PathVariable Long id,
            @RequestBody Workflow workflow) {
        Workflow existing = workflowMapper.findById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.WORKFLOW_NOT_FOUND);
        }

        // 校验批处理SQL支持性
        List<String> batchErrors = validateBatchSteps(workflow.getSteps());
        if (!batchErrors.isEmpty()) {
            return ApiResponse.fail(ErrorCode.WORKFLOW_STEP_INVALID,
                    "以下步骤的SQL不支持批处理: " + String.join("; ", batchErrors));
        }

        log.info("更新工作流: id={}", id);
        workflow.setId(id);
        workflow.setUpdateTime(LocalDateTime.now());
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

        return ApiResponse.ok("更新成功", workflow);
    }

    @Operation(summary = "删除工作流")
    @DeleteMapping("/{id}")
    @Transactional
    public ApiResponse<Void> delete(
            @Parameter(description = "工作流ID") @PathVariable Long id) {
        log.info("删除工作流: id={}", id);
        stepMapper.deleteByWorkflowId(id);
        executionMapper.deleteByWorkflowId(id);
        workflowMapper.deleteById(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "切换启用状态")
    @PatchMapping("/{id}/active")
    public ApiResponse<Void> toggleActive(
            @Parameter(description = "工作流ID") @PathVariable Long id,
            @RequestBody Map<String, Integer> body) {
        Integer isActive = body.get("isActive");
        workflowMapper.updateActiveStatus(id, isActive);
        return ApiResponse.ok();
    }

    @Operation(summary = "手动执行工作流")
    @PostMapping("/{id}/execute")
    public ApiResponse<WorkflowExecution> execute(
            @Parameter(description = "工作流ID") @PathVariable Long id) {
        log.info("手动执行工作流: id={}", id);
        WorkflowExecution execution = executorService.execute(id);
        return ApiResponse.ok(execution);
    }

    @Operation(summary = "获取执行历史")
    @GetMapping("/{id}/executions")
    public ApiResponse<List<WorkflowExecution>> getExecutions(
            @Parameter(description = "工作流ID") @PathVariable Long id,
            @Parameter(description = "条数限制") @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(executionMapper.findByWorkflowId(id, limit));
    }

    @Operation(summary = "获取工作流最新结果")
    @GetMapping("/{id}/result")
    public ApiResponse<List<Map<String, Object>>> getResult(
            @Parameter(description = "工作流ID") @PathVariable Long id) {
        return ApiResponse.ok(executorService.getLatestResult(id));
    }

    @Operation(summary = "测试步骤SQL", description = "支持前置步骤上下文构建，返回数据量警告")
    @PostMapping("/test-step")
    public ApiResponse<Map<String, Object>> testStep(@RequestBody Map<String, Object> request) {
        Long workflowId = request.get("workflowId") != null ? Long.valueOf(request.get("workflowId").toString()) : null;
        Integer stepOrder = request.get("stepOrder") != null ? Integer.valueOf(request.get("stepOrder").toString()) : null;

        // 构建 WorkflowStep
        WorkflowStep step = new WorkflowStep();
        step.setName((String) request.get("name"));
        step.setStepType((String) request.getOrDefault("stepType", WorkflowStepType.SQL.getCode()));
        step.setDatasourceId(request.get("datasourceId") != null ? Long.valueOf(request.get("datasourceId").toString()) : null);
        step.setSqlScript((String) request.get("sqlScript"));
        step.setResultVariable((String) request.get("resultVariable"));
        step.setStepOrder(stepOrder != null ? stepOrder : 0);

        // 如果有 workflowId 和 stepOrder，先执行前置步骤构建上下文
        Map<String, Object> context = new HashMap<>();
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
                        return ApiResponse.fail(ErrorCode.WORKFLOW_EXECUTE_FAILED,
                                "执行前置节点 " + prevStep.getName() + " 失败: " + e.getMessage());
                    }
                }
            }
        }

        // 执行当前步骤
        List<Map<String, Object>> result = executorService.testExecuteStep(step, context);

        // 构建响应
        Map<String, Object> response = new HashMap<>();
        response.put("data", result);
        response.put("rowCount", result.size());

        // 数据量阈值检测
        final int DATA_VOLUME_THRESHOLD = 100000;
        if (result.size() > DATA_VOLUME_THRESHOLD) {
            response.put("warning", "DATA_VOLUME_HIGH");
            response.put("message", "数据量超过 " + DATA_VOLUME_THRESHOLD + " 条，建议使用批处理模式");
        }

        return ApiResponse.ok(response);
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
            if (step.getBatchEnabled() != null && step.getBatchEnabled() == 1
                    && (WorkflowStepType.SQL.matches(step.getStepType()) || WorkflowStepType.LOOP.matches(step.getStepType()))
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

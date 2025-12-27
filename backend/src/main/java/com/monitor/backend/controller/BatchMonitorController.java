package com.monitor.backend.controller;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import com.monitor.backend.common.ApiResponse;
import com.monitor.backend.service.PerformanceMetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 批处理任务监控控制器
 *
 * @author monitor-system
 */
@Tag(name = "批处理管理", description = "Spring Batch作业的启动、停止、监控")
@RestController
@RequestMapping("/api/batch")
public class BatchMonitorController {

    private static final Logger log = LoggerFactory.getLogger(BatchMonitorController.class);

    @Autowired
    @Qualifier("asyncJobLauncher")
    private JobLauncher asyncJobLauncher;

    @Autowired
    private JobExplorer jobExplorer;

    @Autowired(required = false)
    private JobOperator jobOperator;

    @Autowired
    private PerformanceMetricsService metricsService;

    @Autowired
    @Qualifier("workflowDataExtractionJob")
    private Job dataExtractionJob;

    @Autowired
    @Qualifier("workflowEnrichmentJob")
    private Job enrichmentJob;

    @Operation(summary = "启动数据抽取Job")
    @PostMapping("/extraction/start")
    public ApiResponse<Map<String, Object>> startDataExtraction(
            @Parameter(description = "缓存Key") @RequestParam String cacheKey,
            @Parameter(description = "数据源ID") @RequestParam String dataSourceId,
            @Parameter(description = "SQL语句") @RequestParam(required = false) String sql) {

        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("cacheKey", cacheKey)
                    .addString("dataSourceId", dataSourceId)
                    .addString("sql", sql)
                    .addLocalDateTime("startTime", LocalDateTime.now())
                    .toJobParameters();

            JobExecution execution = asyncJobLauncher.run(dataExtractionJob, params);

            Map<String, Object> data = new HashMap<>();
            data.put("executionId", execution.getId());
            data.put("status", execution.getStatus().toString());

            log.info("数据抽取Job已启动: executionId={}, cacheKey={}", execution.getId(), cacheKey);
            return ApiResponse.ok("数据抽取Job已启动", data);
        } catch (Exception e) {
            log.error("启动数据抽取Job失败", e);
            return ApiResponse.error("启动失败: " + e.getMessage());
        }
    }

    @Operation(summary = "启动数据富化Job")
    @PostMapping("/enrichment/start")
    public ApiResponse<Map<String, Object>> startEnrichment(
            @Parameter(description = "缓存Key") @RequestParam String cacheKey,
            @Parameter(description = "富化缓存Key") @RequestParam String enrichmentCacheKey,
            @Parameter(description = "关联字段") @RequestParam String joinField,
            @Parameter(description = "富化字段") @RequestParam(required = false) String enrichFields,
            @Parameter(description = "目标表") @RequestParam String targetTable) {

        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("cacheKey", cacheKey)
                    .addString("enrichmentCacheKey", enrichmentCacheKey)
                    .addString("joinField", joinField)
                    .addString("enrichFields", enrichFields != null ? enrichFields : "")
                    .addString("targetTable", targetTable)
                    .addLocalDateTime("startTime", LocalDateTime.now())
                    .toJobParameters();

            JobExecution execution = asyncJobLauncher.run(enrichmentJob, params);

            Map<String, Object> data = new HashMap<>();
            data.put("executionId", execution.getId());
            data.put("status", execution.getStatus().toString());

            log.info("数据富化Job已启动: executionId={}, targetTable={}", execution.getId(), targetTable);
            return ApiResponse.ok("数据富化Job已启动", data);
        } catch (Exception e) {
            log.error("启动数据富化Job失败", e);
            return ApiResponse.error("启动失败: " + e.getMessage());
        }
    }

    @Operation(summary = "获取Job执行状态")
    @GetMapping("/status/{executionId}")
    public ApiResponse<Map<String, Object>> getJobStatus(
            @Parameter(description = "执行ID") @PathVariable Long executionId) {
        
        JobExecution execution = jobExplorer.getJobExecution(executionId);
        if (execution == null) {
            return ApiResponse.fail(null, "未找到执行记录: " + executionId);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("executionId", execution.getId());
        data.put("jobName", execution.getJobInstance().getJobName());
        data.put("status", execution.getStatus().toString());
        data.put("startTime", execution.getStartTime());
        data.put("endTime", execution.getEndTime());
        data.put("exitStatus", execution.getExitStatus().getExitCode());

        List<Map<String, Object>> steps = execution.getStepExecutions().stream()
                .map(step -> {
                    Map<String, Object> stepInfo = new HashMap<>();
                    stepInfo.put("stepName", step.getStepName());
                    stepInfo.put("status", step.getStatus().toString());
                    stepInfo.put("readCount", step.getReadCount());
                    stepInfo.put("writeCount", step.getWriteCount());
                    stepInfo.put("skipCount", step.getSkipCount());
                    stepInfo.put("startTime", step.getStartTime());
                    stepInfo.put("endTime", step.getEndTime());
                    return stepInfo;
                })
                .toList();
        data.put("steps", steps);

        return ApiResponse.ok(data);
    }

    @Operation(summary = "停止Job执行")
    @PostMapping("/stop/{executionId}")
    public ApiResponse<Void> stopJob(
            @Parameter(description = "执行ID") @PathVariable Long executionId) {
        try {
            if (jobOperator != null) {
                jobOperator.stop(executionId);
                log.info("Job停止请求已发送: executionId={}", executionId);
                return ApiResponse.ok("停止请求已发送", null);
            } else {
                return ApiResponse.fail(null, "JobOperator未配置");
            }
        } catch (Exception e) {
            log.error("停止Job失败", e);
            return ApiResponse.error("停止失败: " + e.getMessage());
        }
    }

    @Operation(summary = "获取所有Job名称")
    @GetMapping("/jobs")
    public ApiResponse<List<String>> getAllJobs() {
        return ApiResponse.ok(jobExplorer.getJobNames());
    }

    @Operation(summary = "获取指定Job的运行历史")
    @GetMapping("/history/{jobName}")
    public ApiResponse<Map<String, Object>> getJobHistory(
            @Parameter(description = "Job名称") @PathVariable String jobName,
            @Parameter(description = "条数限制") @RequestParam(defaultValue = "10") int limit) {

        Set<JobExecution> executions = jobExplorer.findRunningJobExecutions(jobName);
        List<Map<String, Object>> historyList = executions.stream()
                .limit(limit)
                .map(exec -> {
                    Map<String, Object> info = new HashMap<>();
                    info.put("executionId", exec.getId());
                    info.put("status", exec.getStatus().toString());
                    info.put("startTime", exec.getStartTime());
                    info.put("endTime", exec.getEndTime());
                    return info;
                })
                .toList();

        Map<String, Object> data = new HashMap<>();
        data.put("jobName", jobName);
        data.put("executions", historyList);
        return ApiResponse.ok(data);
    }

    @Operation(summary = "获取运行中的Job")
    @GetMapping("/running")
    public ApiResponse<Map<String, Object>> getRunningJobs() {
        List<String> jobNames = jobExplorer.getJobNames();
        List<Map<String, Object>> runningJobs = jobNames.stream()
                .flatMap(name -> jobExplorer.findRunningJobExecutions(name).stream())
                .filter(exec -> exec.getStatus() == BatchStatus.STARTED || exec.getStatus() == BatchStatus.STARTING)
                .map(exec -> {
                    Map<String, Object> info = new HashMap<>();
                    info.put("executionId", exec.getId());
                    info.put("jobName", exec.getJobInstance().getJobName());
                    info.put("status", exec.getStatus().toString());
                    info.put("startTime", exec.getStartTime());
                    return info;
                })
                .toList();

        Map<String, Object> data = new HashMap<>();
        data.put("runningJobs", runningJobs);
        data.put("count", runningJobs.size());
        return ApiResponse.ok(data);
    }

    @Operation(summary = "获取性能指标")
    @GetMapping("/metrics")
    public ApiResponse<Map<String, Object>> getMetrics() {
        return ApiResponse.ok(metricsService.getMetrics());
    }

    @Operation(summary = "获取指定Job的性能统计")
    @GetMapping("/metrics/{jobName}")
    public ApiResponse<Map<String, Object>> getJobMetrics(
            @Parameter(description = "Job名称") @PathVariable String jobName) {
        Map<String, Object> data = new HashMap<>();
        data.put("jobName", jobName);
        data.put("metrics", metricsService.getJobMetrics(jobName));
        return ApiResponse.ok(data);
    }
}

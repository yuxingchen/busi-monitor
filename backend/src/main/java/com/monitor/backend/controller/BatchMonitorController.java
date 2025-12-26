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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.monitor.backend.service.PerformanceMetricsService;

/**
 * 批处理任务监控控制器
 * <p>
 * 提供Spring Batch作业的启动、停止、监控等REST API。
 * </p>
 */
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

    /**
     * 启动数据抽取Job
     */
    @PostMapping("/extraction/start")
    public ResponseEntity<Map<String, Object>> startDataExtraction(
            @RequestParam String cacheKey,
            @RequestParam String dataSourceId,
            @RequestParam(required = false) String sql) {

        Map<String, Object> response = new HashMap<>();

        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("cacheKey", cacheKey)
                    .addString("dataSourceId", dataSourceId)
                    .addString("sql", sql)
                    .addLocalDateTime("startTime", LocalDateTime.now())
                    .toJobParameters();

            JobExecution execution = asyncJobLauncher.run(dataExtractionJob, params);

            response.put("success", true);
            response.put("executionId", execution.getId());
            response.put("status", execution.getStatus().toString());
            response.put("message", "数据抽取Job已启动");

            log.info("数据抽取Job已启动: executionId={}, cacheKey={}", execution.getId(), cacheKey);
        } catch (Exception e) {
            log.error("启动数据抽取Job失败", e);
            response.put("success", false);
            response.put("error", e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * 启动数据富化Job
     */
    @PostMapping("/enrichment/start")
    public ResponseEntity<Map<String, Object>> startEnrichment(
            @RequestParam String cacheKey,
            @RequestParam String enrichmentCacheKey,
            @RequestParam String joinField,
            @RequestParam(required = false) String enrichFields,
            @RequestParam String targetTable) {

        Map<String, Object> response = new HashMap<>();

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

            response.put("success", true);
            response.put("executionId", execution.getId());
            response.put("status", execution.getStatus().toString());
            response.put("message", "数据富化Job已启动");

            log.info("数据富化Job已启动: executionId={}, targetTable={}", execution.getId(), targetTable);
        } catch (Exception e) {
            log.error("启动数据富化Job失败", e);
            response.put("success", false);
            response.put("error", e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * 获取Job执行状态
     */
    @GetMapping("/status/{executionId}")
    public ResponseEntity<Map<String, Object>> getJobStatus(@PathVariable Long executionId) {
        Map<String, Object> response = new HashMap<>();

        JobExecution execution = jobExplorer.getJobExecution(executionId);
        if (execution == null) {
            response.put("success", false);
            response.put("error", "未找到执行记录: " + executionId);
            return ResponseEntity.ok(response);
        }

        response.put("success", true);
        response.put("executionId", execution.getId());
        response.put("jobName", execution.getJobInstance().getJobName());
        response.put("status", execution.getStatus().toString());
        response.put("startTime", execution.getStartTime());
        response.put("endTime", execution.getEndTime());
        response.put("exitStatus", execution.getExitStatus().getExitCode());

        // 步骤执行信息
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
        response.put("steps", steps);

        return ResponseEntity.ok(response);
    }

    /**
     * 停止Job执行
     */
    @PostMapping("/stop/{executionId}")
    public ResponseEntity<Map<String, Object>> stopJob(@PathVariable Long executionId) {
        Map<String, Object> response = new HashMap<>();

        try {
            if (jobOperator != null) {
                jobOperator.stop(executionId);
                response.put("success", true);
                response.put("message", "停止请求已发送");
                log.info("Job停止请求已发送: executionId={}", executionId);
            } else {
                response.put("success", false);
                response.put("error", "JobOperator未配置");
            }
        } catch (Exception e) {
            log.error("停止Job失败", e);
            response.put("success", false);
            response.put("error", e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * 获取所有Job名称
     */
    @GetMapping("/jobs")
    public ResponseEntity<Map<String, Object>> getAllJobs() {
        Map<String, Object> response = new HashMap<>();

        List<String> jobNames = jobExplorer.getJobNames();
        response.put("success", true);
        response.put("jobs", jobNames);

        return ResponseEntity.ok(response);
    }

    /**
     * 获取指定Job的运行历史
     */
    @GetMapping("/history/{jobName}")
    public ResponseEntity<Map<String, Object>> getJobHistory(
            @PathVariable String jobName,
            @RequestParam(defaultValue = "10") int limit) {

        Map<String, Object> response = new HashMap<>();

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

        response.put("success", true);
        response.put("jobName", jobName);
        response.put("executions", historyList);

        return ResponseEntity.ok(response);
    }

    /**
     * 获取当前运行中的Job
     */
    @GetMapping("/running")
    public ResponseEntity<Map<String, Object>> getRunningJobs() {
        Map<String, Object> response = new HashMap<>();

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

        response.put("success", true);
        response.put("runningJobs", runningJobs);
        response.put("count", runningJobs.size());

        return ResponseEntity.ok(response);
    }

    /**
     * 获取性能指标
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        Map<String, Object> response = new HashMap<>();

        response.put("success", true);
        response.put("metrics", metricsService.getMetrics());

        return ResponseEntity.ok(response);
    }

    /**
     * 获取指定Job的性能统计
     */
    @GetMapping("/metrics/{jobName}")
    public ResponseEntity<Map<String, Object>> getJobMetrics(@PathVariable String jobName) {
        Map<String, Object> response = new HashMap<>();

        response.put("success", true);
        response.put("jobName", jobName);
        response.put("metrics", metricsService.getJobMetrics(jobName));

        return ResponseEntity.ok(response);
    }
}

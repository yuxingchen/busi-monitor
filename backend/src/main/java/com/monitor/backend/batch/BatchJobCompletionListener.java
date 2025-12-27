package com.monitor.backend.batch;

import com.monitor.backend.constant.ExecutionStatus;
import com.monitor.backend.entity.BatchPerformanceLog;
import com.monitor.backend.entity.WorkflowExecution;
import com.monitor.backend.mapper.BatchPerformanceLogMapper;
import com.monitor.backend.mapper.WorkflowExecutionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Spring Batch Job 完成监听器
 * <p>
 * 在 Spring Batch Job 完成后：
 * 1. 更新 monitor_workflow_execution 表的状态
 * 2. 记录性能日志到 batch_performance_log 表
 * </p>
 */
@Component
public class BatchJobCompletionListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(BatchJobCompletionListener.class);

    @Autowired
    private WorkflowExecutionMapper executionMapper;

    @Autowired(required = false)
    private BatchPerformanceLogMapper performanceLogMapper;

    @Override
    public void beforeJob(JobExecution jobExecution) {
        Long executionId = jobExecution.getJobParameters().getLong("executionId");
        log.info("Spring Batch Job 开始: jobId={}, executionId={}",
                jobExecution.getId(), executionId);
    }

    /**
     * Job 完成后回调
     */
    @Override
    public void afterJob(JobExecution jobExecution) {
        Long executionId = jobExecution.getJobParameters().getLong("executionId");
        Long workflowId = jobExecution.getJobParameters().getLong("workflowId");

        if (executionId == null) {
            log.warn("JobParameters 中缺少 executionId，无法更新工作流执行状态");
            return;
        }

        // 1. 更新工作流执行状态
        updateWorkflowExecution(jobExecution, executionId, workflowId);

        // 2. 记录性能日志
        recordPerformanceLogs(jobExecution, executionId);
    }

    /**
     * 更新工作流执行记录
     */
    private void updateWorkflowExecution(JobExecution jobExecution, Long executionId, Long workflowId) {
        try {
            WorkflowExecution execution = executionMapper.findById(executionId);
            if (execution == null) {
                log.warn("未找到工作流执行记录: executionId={}", executionId);
                return;
            }

            String batchStatus = jobExecution.getStatus().toString();
            String workflowStatus;
            String errorMessage = null;

            switch (jobExecution.getStatus()) {
                case COMPLETED:
                    workflowStatus = ExecutionStatus.SUCCESS.getCode();
                    break;
                case FAILED:
                    workflowStatus = ExecutionStatus.FAILED.getCode();
                    errorMessage = extractErrorMessage(jobExecution);
                    break;
                case STOPPED:
                    workflowStatus = ExecutionStatus.STOPPED.getCode();
                    break;
                default:
                    workflowStatus = "BATCH_" + batchStatus;
            }

            execution.setStatus(workflowStatus);
            execution.setEndTime(LocalDateTime.now());
            if (errorMessage != null) {
                execution.setErrorMessage(errorMessage);
            }
            executionMapper.update(execution);

            log.info("工作流执行状态已更新: executionId={}, workflowId={}, status={}",
                    executionId, workflowId, workflowStatus);

        } catch (Exception e) {
            log.error("更新工作流执行状态失败: executionId={}, error={}", executionId, e.getMessage(), e);
        }
    }

    /**
     * 记录性能日志
     */
    private void recordPerformanceLogs(JobExecution jobExecution, Long executionId) {
        if (performanceLogMapper == null) {
            log.debug("BatchPerformanceLogMapper 未配置，跳过性能日志记录");
            return;
        }

        try {
            String jobName = jobExecution.getJobInstance().getJobName();
            String cacheStrategy = jobExecution.getJobParameters().getString("cacheStrategy");
            String cacheKey = jobExecution.getJobParameters().getString("cacheKey");
            Long datasourceId = jobExecution.getJobParameters().getLong("datasourceId");

            List<BatchPerformanceLog> logs = new ArrayList<>();

            // 遍历所有步骤执行
            for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
                BatchPerformanceLog perfLog = new BatchPerformanceLog();
                perfLog.setJobName(jobName);
                perfLog.setExecutionId(executionId);
                perfLog.setStepName(stepExecution.getStepName());
                perfLog.setPartitionId(extractPartitionId(stepExecution.getStepName()));
                perfLog.setStatus(stepExecution.getStatus().toString());

                // 时间
                if (stepExecution.getStartTime() != null) {
                    perfLog.setStartTime(stepExecution.getStartTime()
                            .atZone(ZoneId.systemDefault()).toLocalDateTime());
                }
                if (stepExecution.getEndTime() != null) {
                    perfLog.setEndTime(stepExecution.getEndTime()
                            .atZone(ZoneId.systemDefault()).toLocalDateTime());
                }

                // 时长
                if (stepExecution.getStartTime() != null && stepExecution.getEndTime() != null) {
                    long durationMs = Duration.between(
                            stepExecution.getStartTime(), stepExecution.getEndTime()).toMillis();
                    perfLog.setDurationMs(durationMs);

                    // 计算处理速率
                    if (durationMs > 0 && stepExecution.getReadCount() > 0) {
                        double recordsPerSecond = stepExecution.getReadCount() * 1000.0 / durationMs;
                        perfLog.setRecordsPerSecond(recordsPerSecond);
                    }
                }

                // 统计数据
                perfLog.setReadCount((long) stepExecution.getReadCount());
                perfLog.setWriteCount((long) stepExecution.getWriteCount());
                perfLog.setSkipCount((long) stepExecution.getSkipCount());
                perfLog.setCacheStrategy(cacheStrategy);
                perfLog.setCacheKey(cacheKey);
                perfLog.setDataSourceIds(datasourceId != null ? datasourceId.toString() : null);

                // 错误信息
                if (!stepExecution.getFailureExceptions().isEmpty()) {
                    StringBuilder errors = new StringBuilder();
                    stepExecution.getFailureExceptions().forEach(e -> {
                        if (errors.length() > 0)
                            errors.append("; ");
                        errors.append(e.getMessage());
                    });
                    perfLog.setErrorMessage(errors.toString());
                }

                logs.add(perfLog);
            }

            // 批量插入
            if (!logs.isEmpty()) {
                performanceLogMapper.batchInsert(logs);
                log.info("记录性能日志: executionId={}, stepCount={}", executionId, logs.size());
            }

        } catch (Exception e) {
            log.error("记录性能日志失败: executionId={}, error={}", executionId, e.getMessage(), e);
        }
    }

    /**
     * 从步骤名称中提取分区ID
     */
    private String extractPartitionId(String stepName) {
        // 格式如 "dataExtractionSlaveStep:partition0"
        if (stepName != null && stepName.contains(":partition")) {
            return stepName.substring(stepName.lastIndexOf(":partition") + 1);
        }
        return null;
    }

    /**
     * 从 JobExecution 中提取错误信息
     */
    private String extractErrorMessage(JobExecution jobExecution) {
        StringBuilder sb = new StringBuilder();

        jobExecution.getStepExecutions().forEach(step -> {
            if (step.getFailureExceptions() != null && !step.getFailureExceptions().isEmpty()) {
                step.getFailureExceptions().forEach(e -> {
                    if (sb.length() > 0)
                        sb.append("; ");
                    sb.append(step.getStepName()).append(": ").append(e.getMessage());
                });
            }
        });

        if (jobExecution.getFailureExceptions() != null) {
            jobExecution.getFailureExceptions().forEach(e -> {
                if (sb.length() > 0)
                    sb.append("; ");
                sb.append("Job: ").append(e.getMessage());
            });
        }

        return sb.length() > 0 ? sb.toString() : "未知错误";
    }
}

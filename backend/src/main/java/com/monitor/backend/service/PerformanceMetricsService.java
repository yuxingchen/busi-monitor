package com.monitor.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 批处理性能指标服务
 * <p>
 * 收集和统计Spring Batch作业的执行性能指标，包括：
 * - 执行时间统计
 * - 吞吐量统计
 * - 成功/失败率
 * - 资源使用情况
 * </p>
 */
@Service
public class PerformanceMetricsService {

    private static final Logger log = LoggerFactory.getLogger(PerformanceMetricsService.class);

    @Autowired
    private JobExplorer jobExplorer;

    // 全局统计
    private final AtomicLong totalJobCount = new AtomicLong(0);
    private final AtomicLong successJobCount = new AtomicLong(0);
    private final AtomicLong failedJobCount = new AtomicLong(0);
    private final AtomicLong totalProcessedRecords = new AtomicLong(0);

    // 每Job统计
    private final ConcurrentHashMap<String, JobMetrics> jobMetricsMap = new ConcurrentHashMap<>();

    // 最近执行记录
    private final LinkedList<ExecutionRecord> recentExecutions = new LinkedList<>();
    private static final int MAX_RECENT_RECORDS = 100;

    /**
     * 记录Job执行完成
     */
    public void recordJobCompletion(JobExecution execution) {
        String jobName = execution.getJobInstance().getJobName();
        boolean success = execution.getStatus().isUnsuccessful() == false;

        totalJobCount.incrementAndGet();
        if (success) {
            successJobCount.incrementAndGet();
        } else {
            failedJobCount.incrementAndGet();
        }

        // 计算处理记录数
        long recordsProcessed = execution.getStepExecutions().stream()
                .mapToLong(StepExecution::getWriteCount)
                .sum();
        totalProcessedRecords.addAndGet(recordsProcessed);

        // 更新Job级别统计
        JobMetrics metrics = jobMetricsMap.computeIfAbsent(jobName, k -> new JobMetrics());
        metrics.recordExecution(execution, recordsProcessed, success);

        // 添加到最近执行记录
        addRecentExecution(execution, recordsProcessed);

        log.info("记录Job执行完成: job={}, success={}, records={}", jobName, success, recordsProcessed);
    }

    /**
     * 获取全局性能指标
     */
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        metrics.put("totalJobs", totalJobCount.get());
        metrics.put("successJobs", successJobCount.get());
        metrics.put("failedJobs", failedJobCount.get());
        metrics.put("totalRecords", totalProcessedRecords.get());

        double successRate = totalJobCount.get() > 0
                ? (double) successJobCount.get() / totalJobCount.get() * 100
                : 0;
        metrics.put("successRate", String.format("%.2f%%", successRate));

        // 每个Job的汇总
        List<Map<String, Object>> jobSummaries = new ArrayList<>();
        jobMetricsMap.forEach((name, m) -> {
            Map<String, Object> summary = new HashMap<>();
            summary.put("jobName", name);
            summary.put("totalRuns", m.getTotalRuns());
            summary.put("avgDurationMs", m.getAvgDurationMs());
            summary.put("avgRecordsPerSecond", m.getAvgRecordsPerSecond());
            jobSummaries.add(summary);
        });
        metrics.put("jobSummaries", jobSummaries);

        // 最近执行
        synchronized (recentExecutions) {
            metrics.put("recentExecutions", new ArrayList<>(recentExecutions));
        }

        return metrics;
    }

    /**
     * 获取指定Job的性能指标
     */
    public Map<String, Object> getJobMetrics(String jobName) {
        Map<String, Object> metrics = new HashMap<>();

        JobMetrics jobMetrics = jobMetricsMap.get(jobName);
        if (jobMetrics == null) {
            metrics.put("message", "暂无该Job的执行记录");
            return metrics;
        }

        metrics.put("jobName", jobName);
        metrics.put("totalRuns", jobMetrics.getTotalRuns());
        metrics.put("successRuns", jobMetrics.getSuccessRuns());
        metrics.put("failedRuns", jobMetrics.getFailedRuns());
        metrics.put("avgDurationMs", jobMetrics.getAvgDurationMs());
        metrics.put("minDurationMs", jobMetrics.getMinDurationMs());
        metrics.put("maxDurationMs", jobMetrics.getMaxDurationMs());
        metrics.put("totalRecordsProcessed", jobMetrics.getTotalRecordsProcessed());
        metrics.put("avgRecordsPerSecond", jobMetrics.getAvgRecordsPerSecond());
        metrics.put("lastExecutionTime", jobMetrics.getLastExecutionTime());

        return metrics;
    }

    /**
     * 定时同步Job执行状态
     */
    @Scheduled(fixedRate = 60000)
    public void syncJobExecutions() {
        List<String> jobNames = jobExplorer.getJobNames();
        for (String jobName : jobNames) {
            Set<JobExecution> runningJobs = jobExplorer.findRunningJobExecutions(jobName);
            log.debug("Job {} 当前运行数: {}", jobName, runningJobs.size());
        }
    }

    private void addRecentExecution(JobExecution execution, long recordsProcessed) {
        ExecutionRecord record = new ExecutionRecord();
        record.executionId = execution.getId();
        record.jobName = execution.getJobInstance().getJobName();
        record.status = execution.getStatus().toString();
        record.startTime = execution.getStartTime();
        record.endTime = execution.getEndTime();
        record.recordsProcessed = recordsProcessed;

        if (execution.getStartTime() != null && execution.getEndTime() != null) {
            record.durationMs = Duration.between(execution.getStartTime(), execution.getEndTime()).toMillis();
        }

        synchronized (recentExecutions) {
            recentExecutions.addFirst(record);
            while (recentExecutions.size() > MAX_RECENT_RECORDS) {
                recentExecutions.removeLast();
            }
        }
    }

    /**
     * 清理指标数据
     */
    public void clearMetrics() {
        totalJobCount.set(0);
        successJobCount.set(0);
        failedJobCount.set(0);
        totalProcessedRecords.set(0);
        jobMetricsMap.clear();
        synchronized (recentExecutions) {
            recentExecutions.clear();
        }
        log.info("性能指标数据已清理");
    }

    /**
     * Job级别性能统计
     */
    private static class JobMetrics {
        private long totalRuns = 0;
        private long successRuns = 0;
        private long failedRuns = 0;
        private long totalDurationMs = 0;
        private long minDurationMs = Long.MAX_VALUE;
        private long maxDurationMs = 0;
        private long totalRecordsProcessed = 0;
        private LocalDateTime lastExecutionTime;

        public synchronized void recordExecution(JobExecution execution, long records, boolean success) {
            totalRuns++;
            if (success) {
                successRuns++;
            } else {
                failedRuns++;
            }

            if (execution.getStartTime() != null && execution.getEndTime() != null) {
                long duration = Duration.between(execution.getStartTime(), execution.getEndTime()).toMillis();
                totalDurationMs += duration;
                minDurationMs = Math.min(minDurationMs, duration);
                maxDurationMs = Math.max(maxDurationMs, duration);
            }

            totalRecordsProcessed += records;
            lastExecutionTime = execution.getEndTime();
        }

        public long getTotalRuns() {
            return totalRuns;
        }

        public long getSuccessRuns() {
            return successRuns;
        }

        public long getFailedRuns() {
            return failedRuns;
        }

        public long getAvgDurationMs() {
            return totalRuns > 0 ? totalDurationMs / totalRuns : 0;
        }

        public long getMinDurationMs() {
            return minDurationMs == Long.MAX_VALUE ? 0 : minDurationMs;
        }

        public long getMaxDurationMs() {
            return maxDurationMs;
        }

        public long getTotalRecordsProcessed() {
            return totalRecordsProcessed;
        }

        public double getAvgRecordsPerSecond() {
            if (totalDurationMs == 0)
                return 0;
            return (double) totalRecordsProcessed / (totalDurationMs / 1000.0);
        }

        public LocalDateTime getLastExecutionTime() {
            return lastExecutionTime;
        }
    }

    /**
     * 执行记录
     */
    public static class ExecutionRecord {
        public Long executionId;
        public String jobName;
        public String status;
        public LocalDateTime startTime;
        public LocalDateTime endTime;
        public long durationMs;
        public long recordsProcessed;
    }
}

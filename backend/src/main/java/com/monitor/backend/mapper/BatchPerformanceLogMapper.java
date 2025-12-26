package com.monitor.backend.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.monitor.backend.entity.BatchPerformanceLog;

/**
 * 批处理性能日志 Mapper
 */
@Mapper
public interface BatchPerformanceLogMapper {

    /**
     * 根据执行ID查询
     */
    List<BatchPerformanceLog> findByExecutionId(@Param("executionId") Long executionId);

    /**
     * 根据Job名称和时间范围查询
     */
    List<BatchPerformanceLog> findByJobNameAndTimeRange(
            @Param("jobName") String jobName,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 插入记录
     */
    int insert(BatchPerformanceLog log);

    /**
     * 批量插入
     */
    int batchInsert(@Param("logs") List<BatchPerformanceLog> logs);

    /**
     * 根据执行ID统计
     */
    BatchPerformanceLog getStatsByExecutionId(@Param("executionId") Long executionId);
}

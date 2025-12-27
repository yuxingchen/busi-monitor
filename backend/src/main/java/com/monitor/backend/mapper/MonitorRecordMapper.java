package com.monitor.backend.mapper;

import com.monitor.backend.entity.MonitorRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 监控执行记录 Mapper 接口
 * <p>
 * 提供监控任务执行记录的数据库访问操作。
 * </p>
 *
 * @author monitor-system
 */
@Mapper
public interface MonitorRecordMapper {

    /**
     * 新增监控执行记录
     *
     * @param record 执行记录实体
     * @return 影响行数
     */
    int insert(MonitorRecord record);

    /**
     * 根据任务ID查询最近的执行记录
     *
     * @param taskId 任务ID
     * @param limit  返回记录数限制
     * @return 执行记录列表
     */
    List<MonitorRecord> findRecentByTaskId(@Param("taskId") Long taskId, @Param("limit") int limit);

    /**
     * 查询任务最近一次成功执行的时间
     *
     * @param taskId 任务ID
     * @return 最近成功执行时间，无记录则返回null
     */
    LocalDateTime findLastSuccessTime(@Param("taskId") Long taskId);
}

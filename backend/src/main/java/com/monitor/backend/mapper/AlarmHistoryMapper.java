package com.monitor.backend.mapper;

import com.monitor.backend.entity.AlarmHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 告警历史记录 Mapper 接口
 * <p>
 * 提供告警历史记录的数据库访问操作。
 * </p>
 *
 * @author monitor-system
 */
@Mapper
public interface AlarmHistoryMapper {

    /**
     * 根据任务ID查询告警历史
     *
     * @param taskId 任务ID
     * @param limit  返回记录数限制
     * @return 告警历史列表
     */
    List<AlarmHistory> findByTaskId(@Param("taskId") Long taskId, @Param("limit") int limit);

    /**
     * 查询最近的告警历史记录
     *
     * @param limit 返回记录数限制
     * @return 告警历史列表
     */
    List<AlarmHistory> findRecent(@Param("limit") int limit);

    /**
     * 查询所有告警历史记录（用于PageHelper分页）
     *
     * @return 告警历史列表
     */
    List<AlarmHistory> findAll();

    /**
     * 新增告警历史记录
     *
     * @param history 告警历史实体
     * @return 影响行数
     */
    int insert(AlarmHistory history);
}

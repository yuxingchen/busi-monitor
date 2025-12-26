package com.monitor.backend.mapper;

import com.monitor.backend.entity.MonitorTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 监控任务 Mapper 接口
 * <p>
 * 提供SQL类型监控任务的数据库访问操作。
 * </p>
 *
 * @author monitor-system
 */
@Mapper
public interface MonitorTaskMapper {

    /**
     * 查询所有监控任务
     *
     * @return 监控任务列表
     */
    List<MonitorTask> findAll();

    /**
     * 查询所有启用的监控任务
     *
     * @return 启用状态的监控任务列表
     */
    List<MonitorTask> findAllActive();

    /**
     * 根据ID查询监控任务
     *
     * @param id 任务ID
     * @return 监控任务实体
     */
    MonitorTask findById(@Param("id") Long id);

    /**
     * 新增监控任务
     *
     * @param task 监控任务实体
     * @return 影响行数
     */
    int insert(MonitorTask task);

    /**
     * 更新监控任务
     *
     * @param task 监控任务实体
     * @return 影响行数
     */
    int update(MonitorTask task);

    /**
     * 根据ID删除监控任务
     *
     * @param id 任务ID
     * @return 影响行数
     */
    int deleteById(@Param("id") Long id);
}

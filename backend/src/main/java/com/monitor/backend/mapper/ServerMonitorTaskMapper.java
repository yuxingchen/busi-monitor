package com.monitor.backend.mapper;

import com.monitor.backend.entity.ServerMonitorTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 服务器监控任务 Mapper 接口
 * <p>
 * 提供服务器监控任务的数据库访问操作。
 * </p>
 *
 * @author monitor-system
 */
@Mapper
public interface ServerMonitorTaskMapper {

    /**
     * 查询所有监控任务（关联服务器和模板信息）
     *
     * @return 监控任务列表
     */
    List<ServerMonitorTask> findAll();

    /**
     * 根据服务器ID查询监控任务
     *
     * @param serverId 服务器ID
     * @return 监控任务列表
     */
    List<ServerMonitorTask> findByServerId(@Param("serverId") Long serverId);

    /**
     * 根据ID查询监控任务
     *
     * @param id 任务ID
     * @return 监控任务实体
     */
    ServerMonitorTask findById(@Param("id") Long id);

    /**
     * 查询所有启用的监控任务
     *
     * @return 启用状态的监控任务列表
     */
    List<ServerMonitorTask> findAllActive();

    /**
     * 新增监控任务
     *
     * @param task 监控任务实体
     * @return 影响行数
     */
    int insert(ServerMonitorTask task);

    /**
     * 更新监控任务
     *
     * @param task 监控任务实体
     * @return 影响行数
     */
    int update(ServerMonitorTask task);

    /**
     * 更新任务执行状态
     *
     * @param task 监控任务实体（包含lastRunTime、lastRunStatus、lastRunValue）
     * @return 影响行数
     */
    int updateRunStatus(ServerMonitorTask task);

    /**
     * 根据ID删除监控任务
     *
     * @param id 任务ID
     * @return 影响行数
     */
    int deleteById(@Param("id") Long id);

    /**
     * 删除服务器关联的所有监控任务
     *
     * @param serverId 服务器ID
     * @return 影响行数
     */
    int deleteByServerId(@Param("serverId") Long serverId);
}

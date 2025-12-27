package com.monitor.backend.mapper;

import com.monitor.backend.entity.WorkflowExecution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 工作流执行记录 Mapper 接口
 * <p>
 * 提供工作流执行记录的数据库访问操作。
 * </p>
 *
 * @author monitor-system
 */
@Mapper
public interface WorkflowExecutionMapper {

    /**
     * 根据工作流ID查询执行记录
     *
     * @param workflowId 工作流ID
     * @param limit      返回记录数限制
     * @return 执行记录列表（按开始时间倒序）
     */
    List<WorkflowExecution> findByWorkflowId(@Param("workflowId") Long workflowId, @Param("limit") int limit);

    /**
     * 根据ID查询执行记录
     *
     * @param id 执行记录ID
     * @return 执行记录实体
     */
    WorkflowExecution findById(@Param("id") Long id);

    /**
     * 查询最近的执行记录
     *
     * @param limit 返回记录数限制
     * @return 执行记录列表
     */
    List<WorkflowExecution> findRecent(@Param("limit") int limit);

    /**
     * 新增执行记录
     *
     * @param execution 执行记录实体
     * @return 影响行数
     */
    int insert(WorkflowExecution execution);

    /**
     * 更新执行记录
     *
     * @param execution 执行记录实体
     * @return 影响行数
     */
    int update(WorkflowExecution execution);

    /**
     * 删除工作流的所有执行记录
     *
     * @param workflowId 工作流ID
     * @return 影响行数
     */
    int deleteByWorkflowId(@Param("workflowId") Long workflowId);

    /**
     * 获取工作流最近一次成功执行记录
     *
     * @param workflowId 工作流ID
     * @return 最近成功的执行记录
     */
    WorkflowExecution findLastSuccess(@Param("workflowId") Long workflowId);
}

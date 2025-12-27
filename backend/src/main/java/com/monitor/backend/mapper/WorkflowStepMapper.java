package com.monitor.backend.mapper;

import com.monitor.backend.entity.WorkflowStep;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 工作流步骤 Mapper 接口
 * <p>
 * 提供工作流步骤的数据库访问操作。
 * </p>
 *
 * @author monitor-system
 */
@Mapper
public interface WorkflowStepMapper {

    /**
     * 根据工作流ID查询步骤列表
     *
     * @param workflowId 工作流ID
     * @return 步骤列表（按stepOrder排序）
     */
    List<WorkflowStep> findByWorkflowId(@Param("workflowId") Long workflowId);

    /**
     * 根据ID查询步骤
     *
     * @param id 步骤ID
     * @return 步骤实体
     */
    WorkflowStep findById(@Param("id") Long id);

    /**
     * 新增步骤
     *
     * @param step 步骤实体
     * @return 影响行数
     */
    int insert(WorkflowStep step);

    /**
     * 更新步骤
     *
     * @param step 步骤实体
     * @return 影响行数
     */
    int update(WorkflowStep step);

    /**
     * 根据ID删除步骤
     *
     * @param id 步骤ID
     * @return 影响行数
     */
    int deleteById(@Param("id") Long id);

    /**
     * 删除工作流的所有步骤
     *
     * @param workflowId 工作流ID
     * @return 影响行数
     */
    int deleteByWorkflowId(@Param("workflowId") Long workflowId);

    /**
     * 批量插入步骤
     *
     * @param steps 步骤列表
     * @return 影响行数
     */
    int batchInsert(@Param("steps") List<WorkflowStep> steps);
}

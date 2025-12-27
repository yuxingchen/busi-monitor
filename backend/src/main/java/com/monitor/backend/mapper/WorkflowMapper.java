package com.monitor.backend.mapper;

import com.monitor.backend.entity.Workflow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 工作流 Mapper 接口
 * <p>
 * 提供工作流配置的数据库访问操作。
 * </p>
 *
 * @author monitor-system
 */
@Mapper
public interface WorkflowMapper {

    /**
     * 查询所有工作流
     *
     * @return 工作流列表（按创建时间倒序）
     */
    List<Workflow> findAll();

    /**
     * 根据ID查询工作流
     *
     * @param id 工作流ID
     * @return 工作流实体
     */
    Workflow findById(@Param("id") Long id);

    /**
     * 查询所有启用的工作流
     *
     * @return 启用状态的工作流列表
     */
    List<Workflow> findActive();

    /**
     * 新增工作流
     *
     * @param workflow 工作流实体
     * @return 影响行数
     */
    int insert(Workflow workflow);

    /**
     * 更新工作流
     *
     * @param workflow 工作流实体
     * @return 影响行数
     */
    int update(Workflow workflow);

    /**
     * 根据ID删除工作流
     *
     * @param id 工作流ID
     * @return 影响行数
     */
    int deleteById(@Param("id") Long id);

    /**
     * 更新工作流启用状态
     *
     * @param id       工作流ID
     * @param isActive 启用状态
     * @return 影响行数
     */
    int updateActiveStatus(@Param("id") Long id, @Param("isActive") Integer isActive);

    /**
     * 更新工作流的索引字段配置（自动检测后回写）
     *
     * @param id          工作流ID
     * @param indexFields 索引字段配置（JSON格式）
     * @return 影响行数
     */
    int updateIndexFields(@Param("id") Long id, @Param("indexFields") String indexFields);
}

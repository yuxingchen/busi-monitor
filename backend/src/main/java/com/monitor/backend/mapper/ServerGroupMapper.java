package com.monitor.backend.mapper;

import com.monitor.backend.entity.ServerGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 服务器分组 Mapper 接口
 * <p>
 * 提供服务器分组的数据库访问操作。
 * </p>
 *
 * @author monitor-system
 */
@Mapper
public interface ServerGroupMapper {

    /**
     * 查询所有启用的分组
     *
     * @return 分组列表
     */
    List<ServerGroup> findAll();

    /**
     * 根据ID查询分组
     *
     * @param id 分组ID
     * @return 分组实体
     */
    ServerGroup findById(@Param("id") Long id);

    /**
     * 新增分组
     *
     * @param group 分组实体
     * @return 影响行数
     */
    int insert(ServerGroup group);

    /**
     * 更新分组
     *
     * @param group 分组实体
     * @return 影响行数
     */
    int update(ServerGroup group);

    /**
     * 根据ID删除分组
     *
     * @param id 分组ID
     * @return 影响行数
     */
    int deleteById(@Param("id") Long id);
}

package com.monitor.backend.mapper;

import com.monitor.backend.entity.Dashboard;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 仪表盘 Mapper 接口
 * <p>
 * 提供仪表盘配置的数据库访问操作。
 * </p>
 *
 * @author monitor-system
 */
@Mapper
public interface DashboardMapper {

    /**
     * 查询所有启用的仪表盘
     *
     * @return 仪表盘列表（按默认状态和ID排序）
     */
    List<Dashboard> findAll();

    /**
     * 根据ID查询仪表盘
     *
     * @param id 仪表盘ID
     * @return 仪表盘实体
     */
    Dashboard findById(@Param("id") Long id);

    /**
     * 查询默认仪表盘
     *
     * @return 默认仪表盘实体
     */
    Dashboard findDefault();

    /**
     * 新增仪表盘
     *
     * @param dashboard 仪表盘实体
     * @return 影响行数
     */
    int insert(Dashboard dashboard);

    /**
     * 更新仪表盘
     *
     * @param dashboard 仪表盘实体
     * @return 影响行数
     */
    int update(Dashboard dashboard);

    /**
     * 根据ID删除仪表盘
     *
     * @param id 仪表盘ID
     * @return 影响行数
     */
    int deleteById(@Param("id") Long id);

    /**
     * 清除其他仪表盘的默认状态
     *
     * @param id 当前默认仪表盘ID
     * @return 影响行数
     */
    int clearOtherDefaults(@Param("id") Long id);
}

package com.monitor.backend.mapper;

import com.monitor.backend.entity.DashboardWidget;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 仪表盘组件 Mapper 接口
 * <p>
 * 提供仪表盘内组件的数据库访问操作。
 * </p>
 *
 * @author monitor-system
 */
@Mapper
public interface DashboardWidgetMapper {

    /**
     * 根据仪表盘ID查询所有组件
     *
     * @param dashboardId 仪表盘ID
     * @return 组件列表（按z轴层级和ID排序）
     */
    List<DashboardWidget> findByDashboardId(@Param("dashboardId") Long dashboardId);

    /**
     * 根据ID查询组件
     *
     * @param id 组件ID
     * @return 组件实体
     */
    DashboardWidget findById(@Param("id") Long id);

    /**
     * 新增组件
     *
     * @param widget 组件实体
     * @return 影响行数
     */
    int insert(DashboardWidget widget);

    /**
     * 更新组件
     *
     * @param widget 组件实体
     * @return 影响行数
     */
    int update(DashboardWidget widget);

    /**
     * 根据ID删除组件
     *
     * @param id 组件ID
     * @return 影响行数
     */
    int deleteById(@Param("id") Long id);

    /**
     * 删除仪表盘内的所有组件
     *
     * @param dashboardId 仪表盘ID
     * @return 影响行数
     */
    int deleteByDashboardId(@Param("dashboardId") Long dashboardId);

    /**
     * 批量插入组件
     *
     * @param widgets 组件列表
     * @return 影响行数
     */
    int batchInsert(@Param("widgets") List<DashboardWidget> widgets);
}

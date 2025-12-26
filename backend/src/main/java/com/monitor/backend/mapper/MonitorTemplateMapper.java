package com.monitor.backend.mapper;

import com.monitor.backend.entity.MonitorTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 监控模板 Mapper 接口
 * <p>
 * 提供监控模板的数据库访问操作。
 * </p>
 *
 * @author monitor-system
 */
@Mapper
public interface MonitorTemplateMapper {

    /**
     * 查询所有启用的模板
     *
     * @return 模板列表（按排序顺序和ID排序）
     */
    List<MonitorTemplate> findAll();

    /**
     * 根据ID查询模板
     *
     * @param id 模板ID
     * @return 模板实体
     */
    MonitorTemplate findById(@Param("id") Long id);

    /**
     * 根据分类查询模板
     *
     * @param category 分类名称
     * @return 模板列表
     */
    List<MonitorTemplate> findByCategory(@Param("category") String category);

    /**
     * 查询系统内置模板
     *
     * @return 系统模板列表
     */
    List<MonitorTemplate> findSystemTemplates();

    /**
     * 新增模板
     *
     * @param template 模板实体
     * @return 影响行数
     */
    int insert(MonitorTemplate template);

    /**
     * 更新模板
     *
     * @param template 模板实体
     * @return 影响行数
     */
    int update(MonitorTemplate template);

    /**
     * 根据ID删除模板
     *
     * @param id 模板ID
     * @return 影响行数
     */
    int deleteById(@Param("id") Long id);
}

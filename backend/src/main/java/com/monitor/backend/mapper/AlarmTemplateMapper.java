package com.monitor.backend.mapper;

import com.monitor.backend.entity.AlarmTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 告警模板 Mapper 接口
 * <p>
 * 提供告警消息模板的数据库访问操作。
 * </p>
 *
 * @author monitor-system
 */
@Mapper
public interface AlarmTemplateMapper {

    /**
     * 查询所有告警模板
     *
     * @return 告警模板列表
     */
    List<AlarmTemplate> findAll();

    /**
     * 根据ID查询告警模板
     *
     * @param id 模板ID
     * @return 告警模板实体
     */
    AlarmTemplate findById(@Param("id") Long id);

    /**
     * 查询默认模板
     *
     * @return 默认告警模板
     */
    AlarmTemplate findDefault();

    /**
     * 新增告警模板
     *
     * @param template 告警模板实体
     * @return 影响行数
     */
    int insert(AlarmTemplate template);

    /**
     * 更新告警模板
     *
     * @param template 告警模板实体
     * @return 影响行数
     */
    int update(AlarmTemplate template);

    /**
     * 根据ID删除告警模板
     *
     * @param id 模板ID
     * @return 影响行数
     */
    int deleteById(@Param("id") Long id);
}

package com.monitor.backend.mapper;

import com.monitor.backend.entity.MonitorDataSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 监控数据源 Mapper 接口
 * <p>
 * 提供外部数据库连接配置的数据库访问操作。
 * </p>
 *
 * @author monitor-system
 */
@Mapper
public interface MonitorDataSourceMapper {

    /**
     * 查询所有数据源
     *
     * @return 数据源列表
     */
    List<MonitorDataSource> findAll();

    /**
     * 根据ID查询数据源
     *
     * @param id 数据源ID
     * @return 数据源实体
     */
    MonitorDataSource findById(@Param("id") Long id);

    /**
     * 新增数据源
     *
     * @param dataSource 数据源实体
     * @return 影响行数
     */
    int insert(MonitorDataSource dataSource);

    /**
     * 更新数据源
     *
     * @param dataSource 数据源实体
     * @return 影响行数
     */
    int update(MonitorDataSource dataSource);

    /**
     * 根据ID删除数据源
     *
     * @param id 数据源ID
     * @return 影响行数
     */
    int deleteById(@Param("id") Long id);
}

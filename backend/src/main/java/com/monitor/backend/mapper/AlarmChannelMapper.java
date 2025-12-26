package com.monitor.backend.mapper;

import com.monitor.backend.entity.AlarmChannel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 告警渠道 Mapper 接口
 * <p>
 * 提供告警渠道的数据库访问操作，包括查询、新增、修改、删除等。
 * </p>
 *
 * @author monitor-system
 */
@Mapper
public interface AlarmChannelMapper {

    /**
     * 查询所有告警渠道
     *
     * @return 告警渠道列表
     */
    List<AlarmChannel> findAll();

    /**
     * 查询所有启用的告警渠道
     *
     * @return 启用状态的告警渠道列表
     */
    List<AlarmChannel> findAllActive();

    /**
     * 根据ID查询告警渠道
     *
     * @param id 渠道ID
     * @return 告警渠道实体
     */
    AlarmChannel findById(@Param("id") Long id);

    /**
     * 根据类型查询启用的告警渠道
     *
     * @param type 渠道类型
     * @return 告警渠道列表
     */
    List<AlarmChannel> findByType(@Param("type") String type);

    /**
     * 新增告警渠道
     *
     * @param channel 告警渠道实体
     * @return 影响行数
     */
    int insert(AlarmChannel channel);

    /**
     * 更新告警渠道
     *
     * @param channel 告警渠道实体
     * @return 影响行数
     */
    int update(AlarmChannel channel);

    /**
     * 根据ID删除告警渠道
     *
     * @param id 渠道ID
     * @return 影响行数
     */
    int deleteById(@Param("id") Long id);
}

package com.monitor.backend.mapper;

import com.monitor.backend.entity.ServerAsset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 服务器资产 Mapper 接口
 * <p>
 * 提供服务器资产信息的数据库访问操作。
 * </p>
 *
 * @author monitor-system
 */
@Mapper
public interface ServerAssetMapper {

    /**
     * 查询所有启用的服务器
     *
     * @return 服务器列表
     */
    List<ServerAsset> findAll();

    /**
     * 根据ID查询服务器
     *
     * @param id 服务器ID
     * @return 服务器实体
     */
    ServerAsset findById(@Param("id") Long id);

    /**
     * 根据分组ID查询服务器
     *
     * @param groupId 分组ID
     * @return 服务器列表
     */
    List<ServerAsset> findByGroupId(@Param("groupId") Long groupId);

    /**
     * 根据IP地址查询服务器
     *
     * @param ip IP地址
     * @return 服务器实体
     */
    ServerAsset findByIp(@Param("ip") String ip);

    /**
     * 新增服务器
     *
     * @param asset 服务器实体
     * @return 影响行数
     */
    int insert(ServerAsset asset);

    /**
     * 更新服务器信息
     *
     * @param asset 服务器实体
     * @return 影响行数
     */
    int update(ServerAsset asset);

    /**
     * 更新服务器健康检查状态
     *
     * @param asset 服务器实体（包含lastCheckTime和lastCheckStatus）
     * @return 影响行数
     */
    int updateStatus(ServerAsset asset);

    /**
     * 根据ID删除服务器
     *
     * @param id 服务器ID
     * @return 影响行数
     */
    int deleteById(@Param("id") Long id);
}

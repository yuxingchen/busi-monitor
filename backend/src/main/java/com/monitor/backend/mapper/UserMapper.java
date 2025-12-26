package com.monitor.backend.mapper;

import com.monitor.backend.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户Mapper接口
 */
@Mapper
public interface UserMapper {
    
    /**
     * 根据用户名查询用户
     */
    User findByUsername(@Param("username") String username);
    
    /**
     * 根据ID查询用户
     */
    User findById(@Param("id") Long id);
    
    /**
     * 新增用户
     */
    int insert(User user);
    
    /**
     * 更新用户信息
     */
    int update(User user);
    
    /**
     * 更新密码
     */
    int updatePassword(@Param("id") Long id, @Param("passwordHash") String passwordHash);
    
    /**
     * 分页查询用户
     */
    List<User> findByPage(@Param("username") String username, @Param("offset") int offset, @Param("size") int size);
    
    /**
     * 统计用户数量
     */
    int countByUsername(@Param("username") String username);
    
    /**
     * 删除用户
     */
    int deleteById(@Param("id") Long id);
    
    /**
     * 更新启用状态
     */
    int updateEnabled(@Param("id") Long id, @Param("enabled") int enabled);
}

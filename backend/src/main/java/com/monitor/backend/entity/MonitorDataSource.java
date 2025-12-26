package com.monitor.backend.entity;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 监控数据源实体类
 * <p>
 * 定义外部数据库连接配置，用于执行监控任务的SQL查询。
 * </p>
 *
 * @author monitor-system
 */
@Data
public class MonitorDataSource {
    
    /** 主键ID */
    private Long id;
    
    /** 数据源名称 */
    private String name;
    
    /** JDBC连接URL */
    private String url;
    
    /** 数据库用户名 */
    private String username;
    
    /** 加密后的数据库密码 */
    private String passwordEncrypted;
    
    /** JDBC驱动类名 */
    private String driverClassName;
    
    /** 创建时间 */
    private LocalDateTime createTime;
    
    /** 更新时间 */
    private LocalDateTime updateTime;
    
    /** 明文密码（非持久化字段，用于前端传递） */
    private transient String password;
}


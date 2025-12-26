package com.monitor.backend.entity;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 服务器资产实体类
 * <p>
 * 定义被监控的服务器信息，包括SSH连接配置、认证方式等。
 * 支持密码和密钥两种认证方式。
 * </p>
 *
 * @author monitor-system
 */
@Data
public class ServerAsset {
    
    /** 主键ID */
    private Long id;
    
    /** 服务器名称 */
    private String name;
    
    /** 服务器IP地址 */
    private String ip;
    
    /** SSH端口号 */
    private Integer port;
    
    /** SSH用户名 */
    private String username;
    
    /** 认证类型（PASSWORD/KEY） */
    private String authType;
    
    /** 加密后的密码 */
    private String passwordEncrypted;
    
    /** 加密后的私钥 */
    private String privateKeyEncrypted;
    
    /** 所属分组ID */
    private Long groupId;
    
    /** 标签（逗号分隔） */
    private String tags;
    
    /** 是否启用（1=启用，0=禁用） */
    private Integer isActive;
    
    /** 最后健康检查时间 */
    private LocalDateTime lastCheckTime;
    
    /** 最后健康检查状态（ONLINE/OFFLINE） */
    private String lastCheckStatus;
    
    /** 创建时间 */
    private LocalDateTime createTime;
    
    /** 更新时间 */
    private LocalDateTime updateTime;
    
    /** 明文密码（非持久化字段，用于前端传递） */
    private transient String password;
    
    /** 明文私钥（非持久化字段，用于前端传递） */
    private transient String privateKey;

}

package com.monitor.backend.entity;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 系统用户实体类
 */
@Data
public class User {
    
    /** 主键ID */
    private Long id;
    
    /** 用户名 */
    private String username;
    
    /** 密码哈希值(BCrypt) */
    private String passwordHash;
    
    /** 角色 (ADMIN/USER) */
    private String role;
    
    /** 是否启用 */
    private Integer enabled;
    
    /** 创建时间 */
    private LocalDateTime createTime;
    
    /** 更新时间 */
    private LocalDateTime updateTime;
}

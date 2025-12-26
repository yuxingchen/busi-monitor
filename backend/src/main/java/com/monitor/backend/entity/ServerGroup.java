package com.monitor.backend.entity;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 服务器分组实体类
 * <p>
 * 用于对服务器资产进行分组管理，支持层级结构（通过parentId）。
 * </p>
 *
 * @author monitor-system
 */
@Data
public class ServerGroup {
    
    /** 主键ID */
    private Long id;
    
    /** 分组名称 */
    private String name;
    
    /** 分组描述 */
    private String description;
    
    /** 父分组ID（用于层级结构，顶级分组为null） */
    private Long parentId;
    
    /** 是否启用（1=启用，0=禁用） */
    private Integer isActive;
    
    /** 创建时间 */
    private LocalDateTime createTime;
    
    /** 更新时间 */
    private LocalDateTime updateTime;

}

package com.monitor.backend.entity;


import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警渠道实体类
 * <p>
 * 定义告警通知的渠道配置，支持多种通知方式如企业微信、邮件、短信等。
 * </p>
 *
 * @author monitor-system
 */
@Data
public class AlarmChannel {
    
    /** 主键ID */
    private Long id;
    
    /** 渠道名称 */
    private String name;
    
    /** 渠道类型（WECHAT_WORK/EMAIL/SMS/WEBHOOK） */
    private String type;
    
    /** 渠道配置（JSON格式，包含具体的通知配置） */
    private String config;
    
    /** 是否启用（1=启用，0=禁用） */
    private Integer isActive;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

}

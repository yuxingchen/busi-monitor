package com.monitor.backend.entity;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 监控模板实体类
 * <p>
 * 定义预设的监控配置模板，包含采集脚本、默认阈值、告警模板等。
 * 支持多种分类如基础监控（CPU/内存）、组件监控（MySQL/Redis）等。
 * </p>
 *
 * @author monitor-system
 */
@Data
public class MonitorTemplate {

    /** 主键ID */
    private Long id;

    /** 模板名称 */
    private String name;

    /** 分类（BASIC/COMPONENT/APPLICATION） */
    private String category;

    /** 子分类（CPU/MySQL/Redis等） */
    private String subCategory;

    /** 采集类型（SSH_SCRIPT/API/SQL） */
    private String collectType;

    /** 采集脚本 */
    private String collectScript;

    /** 参数定义（JSON格式，定义脚本所需的参数） */
    private String paramSchema;

    /** 默认告警阈值（JSON格式） */
    private String defaultThreshold;

    /** 默认Cron表达式 */
    private String defaultCron;

    /** 关联的告警模板ID */
    private Long alarmTemplateId;

    /** 告警模板名称（非数据库字段，用于前端显示） */
    private transient String alarmTemplateName;

    /** 模板描述 */
    private String description;

    /** 是否为系统内置模板（1=是，0=否） */
    private Integer isSystem;

    /** 是否启用（1=启用，0=禁用） */
    private Integer isActive;

    /** 排序顺序 */
    private Integer sortOrder;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

}

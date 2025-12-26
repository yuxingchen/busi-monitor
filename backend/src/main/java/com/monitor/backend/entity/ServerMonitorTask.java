package com.monitor.backend.entity;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 服务器监控任务实体类
 * <p>
 * 定义针对服务器的监控任务，通过SSH执行脚本收集指标。
 * 可关联监控模板，支持自定义告警阈值。
 * </p>
 *
 * @author monitor-system
 */
@Data
public class ServerMonitorTask {

    /** 主键ID */
    private Long id;

    /** 任务名称 */
    private String name;

    /** 关联的服务器ID */
    private Long serverId;

    /** 关联的模板ID */
    private Long templateId;

    /** 采集脚本（Shell命令） */
    private String collectScript;

    /** 脚本参数（JSON格式） */
    private String params;

    /** 告警阈值规则（JSON格式） */
    private String thresholdRule;

    /** Cron定时表达式 */
    private String cronExpression;

    /** 告警渠道ID列表（逗号分隔） */
    private String alarmChannels;

    /** 告警模板ID */
    private Long alarmTemplateId;

    /** 是否启用（1=启用，0=禁用） */
    private Integer isActive;

    /** 最后执行时间 */
    private LocalDateTime lastRunTime;

    /** 最后执行状态（SUCCESS/FAILED） */
    private String lastRunStatus;

    /** 最后执行结果值 */
    private String lastRunValue;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 服务器名称（非持久化字段，用于前端显示） */
    private transient String serverName;

    /** 服务器IP（非持久化字段，用于前端显示） */
    private transient String serverIp;

    /** 模板名称（非持久化字段，用于前端显示） */
    private transient String templateName;

}

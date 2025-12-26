package com.monitor.backend.entity;


import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警模板实体类
 * <p>
 * 定义告警通知的消息模板，支持不同渠道类型的自定义模板内容。
 * </p>
 *
 * @author monitor-system
 */
@Data
public class AlarmTemplate {

    /** 主键ID */
    private Long id;

    /** 模板名称 */
    private String name;

    /** 邮件主题（仅邮件类型使用） */
    private String subject;

    /** 模板内容（支持变量替换如 ${taskName}） */
    private String content;

    /** 内容格式（TEXT=纯文本，MARKDOWN=Markdown格式） */
    private String contentType;

    /** 是否为默认模板（1=默认，0=非默认） */
    private Integer isDefault;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

}

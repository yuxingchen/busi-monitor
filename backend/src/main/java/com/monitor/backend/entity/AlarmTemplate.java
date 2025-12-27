package com.monitor.backend.entity;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 告警模板实体类
 */
@Data
@Schema(description = "告警模板")
public class AlarmTemplate {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "模板名称")
    private String name;

    @Schema(description = "邮件主题")
    private String subject;

    @Schema(description = "模板内容（支持变量替换）")
    private String content;

    @Schema(description = "内容格式（TEXT/MARKDOWN）")
    private String contentType;

    @Schema(description = "是否为默认模板（1=默认，0=非默认）")
    private Integer isDefault;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}

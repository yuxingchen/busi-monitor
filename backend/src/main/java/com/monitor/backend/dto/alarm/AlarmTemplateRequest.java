package com.monitor.backend.dto.alarm;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 告警模板请求DTO
 */
@Data
@Schema(description = "告警模板请求")
public class AlarmTemplateRequest {

    @Schema(description = "主键ID，更新时必填")
    private Long id;

    @Schema(description = "标题")
    private String subject;

    @Schema(description = "模板名称")
    private String name;

    @Schema(description = "内容类型（TEXT/MARKDOWN）")
    private String contentType;

    @Schema(description = "模板内容")
    private String content;

    @Schema(description = "是否默认模板（1=是，0=否）")
    private Integer isDefault;
}

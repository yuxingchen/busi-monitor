package com.monitor.backend.dto.alarm;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 告警渠道请求DTO
 */
@Data
@Schema(description = "告警渠道请求")
public class AlarmChannelRequest {

    @Schema(description = "主键ID，更新时必填")
    private Long id;

    @Schema(description = "渠道名称")
    private String name;

    @Schema(description = "渠道类型（EMAIL/DINGTALK/WECHAT/SMS/ANNOUNCEMENT）")
    private String type;

    @Schema(description = "渠道配置（JSON格式）")
    private String config;

    @Schema(description = "是否启用（1=启用，0=禁用）")
    private Integer isActive;
}

package com.monitor.backend.entity;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 告警渠道实体类
 */
@Data
@Schema(description = "告警渠道")
public class AlarmChannel {
    
    @Schema(description = "主键ID")
    private Long id;
    
    @Schema(description = "渠道名称")
    private String name;
    
    @Schema(description = "渠道类型（WECHAT_WORK/EMAIL/SMS/WEBHOOK）")
    private String type;
    
    @Schema(description = "渠道配置（JSON格式）")
    private String config;
    
    @Schema(description = "是否启用（1=启用，0=禁用）")
    private Integer isActive;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}

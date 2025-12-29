package com.monitor.backend.enums;

import org.apache.commons.lang3.StringUtils;

/**
 * AlarmStatus
 */
public enum AlarmStatus {
    // 告警触发状态
    FIRING,
    // 告警已确认状态
    ACKNOWLEDGED,
    // 告警已抑制状态
    SUPPRESSED,
    // 告警已解决状态
    RESOLVED;

    /**
     * 根据字符串值获取对应的告警状态枚举
     *
     * @param value 状态字符串值
     * @return 对应的告警状态枚举，如果未找到匹配项则默认返回FIRING
     */
    public static AlarmStatus fromString(String value) {
        if (StringUtils.isEmpty(value)) {
            return FIRING;
        }
        return switch (value) {
            case "ACKNOWLEDGED" -> ACKNOWLEDGED;
            case "SUPPRESSED" -> SUPPRESSED;
            case "RESOLVED" -> RESOLVED;
            default -> FIRING;
        };
    }
}
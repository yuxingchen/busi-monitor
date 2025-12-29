package com.monitor.backend.enums;

import org.apache.commons.lang3.StringUtils;

public enum AlarmLevel {
    /**
     * 信息级别告警
     */
    INFO,
    /**
     * 警告级别告警
     */
    WARNING,
    /**
     * 严重级别告警
     */
    CRITICAL;

    /**
     * 根据字符串获取对应的告警级别枚举值
     *
     * @param level 告警级别字符串
     * @return 对应的告警级别枚举值，如果字符串不匹配则返回WARNING
     */
    public static AlarmLevel fromString(String level) {
        if (StringUtils.isEmpty(level)) {
            return WARNING;
        }
        return switch (level) {
            case "INFO" -> INFO;
            case "CRITICAL" -> CRITICAL;
            default -> WARNING;
        };
    }
}
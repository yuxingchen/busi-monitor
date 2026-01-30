package com.monitor.backend.enums;

import lombok.Getter;

/**
 * 告警触发类型枚举
 */
@Getter
public enum AlarmTriggerType {
    ROW_COUNT("ROW_COUNT", "行数统计"),
    FIELD_VALUE("FIELD_VALUE", "字段值"),
    FIELD_AGG("FIELD_AGG", "字段聚合"),
    THRESHOLD("THRESHOLD", "阈值触发"),
    YOY("YOY", "同比"),
    MOM("MOM", "环比"),
    COMPARE_PERIOD("COMPARE_PERIOD", "同比环比");

    private final String code;
    private final String description;

    AlarmTriggerType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据code获取枚举值（忽略大小写）
     */
    public static AlarmTriggerType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (AlarmTriggerType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }
}

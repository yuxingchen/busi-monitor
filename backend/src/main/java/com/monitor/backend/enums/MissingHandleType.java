package com.monitor.backend.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 历史数据缺失/新增处理方式
 */
@Getter
@RequiredArgsConstructor
public enum MissingHandleType {
    SKIP("SKIP", "跳过"),
    ALERT("ALERT", "触发告警");
    
    private final String code;
    private final String label;
    
    public static MissingHandleType fromCode(String code) {
        if (code == null) return SKIP;
        for (MissingHandleType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return SKIP;
    }
    
    public boolean shouldAlert() {
        return this == ALERT;
    }
}

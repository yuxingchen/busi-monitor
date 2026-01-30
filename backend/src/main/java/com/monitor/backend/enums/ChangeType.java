package com.monitor.backend.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 变化类型
 */
@Getter
@RequiredArgsConstructor
public enum ChangeType {
    RATE("RATE", "变化率(%)"),
    VALUE("VALUE", "变化值");
    
    private final String code;
    private final String label;
    
    public static ChangeType fromCode(String code) {
        if (code == null) return RATE;
        for (ChangeType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return RATE;
    }
    
    public boolean isRate() {
        return this == RATE;
    }
}

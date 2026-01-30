package com.monitor.backend.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 分组告警模式
 */
@Getter
@RequiredArgsConstructor
public enum AlertMode {
    ANY("ANY", "任一触发"),
    ALL("ALL", "全部触发");
    
    private final String code;
    private final String label;
    
    public static AlertMode fromCode(String code) {
        if (code == null) return ANY;
        for (AlertMode mode : values()) {
            if (mode.code.equalsIgnoreCase(code)) {
                return mode;
            }
        }
        return ANY;
    }
    
    public boolean isAll() {
        return this == ALL;
    }
}

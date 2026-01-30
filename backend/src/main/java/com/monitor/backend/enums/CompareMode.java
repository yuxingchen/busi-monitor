package com.monitor.backend.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 对比模式
 */
@Getter
@RequiredArgsConstructor
public enum CompareMode {
    AUTO("AUTO", "自动识别"),
    SIMPLE("SIMPLE", "简单对比"),
    GROUPED("GROUPED", "分组对比");
    
    private final String code;
    private final String label;
    
    public static CompareMode fromCode(String code) {
        if (code == null) return AUTO;
        for (CompareMode mode : values()) {
            if (mode.code.equalsIgnoreCase(code)) {
                return mode;
            }
        }
        return AUTO;
    }
}

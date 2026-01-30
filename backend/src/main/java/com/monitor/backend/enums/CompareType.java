package com.monitor.backend.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 同比环比对比类型
 */
@Getter
@RequiredArgsConstructor
public enum CompareType {
    MOM("MOM", "环比"),
    YOY("YOY", "同比");
    
    private final String code;
    private final String label;
    
    public static CompareType fromCode(String code) {
        if (code == null) return MOM;
        for (CompareType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return MOM;
    }
    
    public boolean isYearOverYear() {
        return this == YOY;
    }
}

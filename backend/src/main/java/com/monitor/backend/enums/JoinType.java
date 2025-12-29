package com.monitor.backend.enums;

import lombok.Getter;

/**
 * SQL JOIN类型枚举
 */
@Getter
public enum JoinType {
    INNER("INNER", "内连接"),
    LEFT("LEFT", "左连接"),
    RIGHT("RIGHT", "右连接"),
    FULL("FULL", "全连接");
    
    private final String code;
    private final String description;
    
    JoinType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public boolean matches(String type) {
        return this.code.equalsIgnoreCase(type);
    }
    
    public static JoinType fromCode(String code) {
        for (JoinType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return INNER; // 默认内连接
    }
}

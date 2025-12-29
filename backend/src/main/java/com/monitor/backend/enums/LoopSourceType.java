package com.monitor.backend.enums;

import lombok.Getter;

/**
 * 循环源类型枚举
 */
@Getter
public enum LoopSourceType {
    VARIABLE("VARIABLE", "变量遍历"),
    CONSTANT("CONSTANT", "常量分割");
    
    private final String code;
    private final String description;
    
    LoopSourceType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public boolean matches(String source) {
        return this.code.equals(source);
    }
}

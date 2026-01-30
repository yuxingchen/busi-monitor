package com.monitor.backend.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 分组对比状态
 */
@Getter
@RequiredArgsConstructor
public enum GroupCompareStatus {
    NORMAL("NORMAL", "正常"),
    ALERT("ALERT", "告警"),
    NEW("NEW", "新增"),
    MISSING("MISSING", "消失");
    
    private final String code;
    private final String label;
    
    public boolean isAlert() {
        return this == ALERT;
    }
}

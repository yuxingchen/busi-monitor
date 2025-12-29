package com.monitor.backend.enums;

import lombok.Getter;

/**
 * 工作流执行状态枚举
 */
@Getter
public enum ExecutionStatus {
    PENDING("PENDING", "待执行"),
    RUNNING("RUNNING", "执行中"),
    STOPPED("STOPPED", "已停止"),
    BATCH_STARTING("BATCH_STARTING", "批处理启动中"),
    SUCCESS("SUCCESS", "成功"),
    FAILED("FAILED", "失败"),
    CANCELLED("CANCELLED", "已取消");
    
    private final String code;
    private final String description;
    
    ExecutionStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public boolean matches(String status) {
        return this.code.equals(status);
    }
}

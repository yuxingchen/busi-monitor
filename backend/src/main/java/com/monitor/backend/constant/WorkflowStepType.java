package com.monitor.backend.constant;

import lombok.Getter;

/**
 * 工作流步骤类型枚举
 */
@Getter
public enum WorkflowStepType {
    SQL("SQL", "SQL查询"),
    CONSTANT("CONSTANT", "常量定义"),
    LOOP("LOOP", "循环执行"),
    TASK_REF("TASK_REF", "任务引用"),
    MEMORY_JOIN("MEMORY_JOIN", "内存连接"),
    OUTPUT("OUTPUT", "输出");
    
    private final String code;
    private final String description;
    
    WorkflowStepType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public boolean matches(String type) {
        return this.code.equals(type);
    }
    
    public static WorkflowStepType fromCode(String code) {
        for (WorkflowStepType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}

package com.monitor.backend.constant;

import java.util.List;

/**
 * 系统保留字段名常量
 */
public final class SystemFields {
    private SystemFields() {
    }

    // 主键
    public static final String ID = "id";

    // 执行相关
    public static final String EXECUTION_ID = "execution_id";
    public static final String EXECUTION_TIME = "execution_time";
    public static final String BATCH_ID = "batch_id";

    // 固定索引字段
    public static final List<String> FIXED_INDEX_FIELDS = List.of(EXECUTION_ID, EXECUTION_TIME);

    /**
     * 判断是否为系统保留字段
     */
    public static boolean isSystemField(String fieldName) {
        return ID.equals(fieldName)
                || EXECUTION_TIME.equals(fieldName)
                || EXECUTION_ID.equals(fieldName)
                || BATCH_ID.equals(fieldName);
    }
}

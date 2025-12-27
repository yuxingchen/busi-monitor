package com.monitor.backend.constant;

import java.util.Set;

/**
 * 占位符前缀常量
 */
public final class PlaceholderPrefix {
    private PlaceholderPrefix() {}
    
    // 常量和变量前缀
    public static final String CONST = "const";
    public static final String LOOP = "loop";
    public static final String ENV = "env";
    
    // 时间相关前缀
    public static final String NOW = "now";
    public static final String TODAY = "today";
    public static final String YESTERDAY = "yesterday";
    public static final String LAST_RUN_TIME = "lastRunTime";
    public static final String TODAY_START = "todayStart";
    public static final String TODAY_END = "todayEnd";
    
    // 所有内置变量名集合
    public static final Set<String> BUILTIN_VARS = Set.of(
        CONST, LOOP, ENV, NOW, TODAY, YESTERDAY, LAST_RUN_TIME, TODAY_START, TODAY_END
    );
    
    /**
     * 判断是否为内置变量
     */
    public static boolean isBuiltinVar(String varName) {
        return BUILTIN_VARS.contains(varName);
    }
}

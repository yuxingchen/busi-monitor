package com.monitor.backend.constant;

/**
 * 同比环比对比相关常量
 */
public final class CompareConstants {
    private CompareConstants() {}
    
    /** 历史数据查询时间窗口（小时） */
    public static final int HISTORY_SEARCH_WINDOW_HOURS = 24;
    
    /** 分组键分隔符 */
    public static final String GROUP_KEY_SEPARATOR = "|";
    
    /** 变化率计算基数 */
    public static final double RATE_MULTIPLIER = 100.0;
}

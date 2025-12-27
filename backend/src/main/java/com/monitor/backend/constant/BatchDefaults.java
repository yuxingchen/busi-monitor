package com.monitor.backend.constant;

/**
 * 批处理默认值常量
 */
public final class BatchDefaults {
    private BatchDefaults() {}
    
    // 默认ID列名
    public static final String DEFAULT_ID_COLUMN = "id";
    
    // 默认分区数
    public static final int DEFAULT_PARTITION_COUNT = 10;
    
    // 默认块大小
    public static final int DEFAULT_CHUNK_SIZE = 1000;
    
    // 默认命名空间
    public static final String DEFAULT_NAMESPACE = "const";
    
    // 默认分隔符
    public static final String DEFAULT_SEPARATOR = ",";
    
    // 未知表名
    public static final String UNKNOWN_TABLE = "unknown_table";
    
    // 通配符
    public static final String WILDCARD = "*";
    
    /**
     * 获取分区数，如果为空则使用默认值
     */
    public static int getPartitionCount(Integer value) {
        return value != null ? value : DEFAULT_PARTITION_COUNT;
    }
    
    /**
     * 获取块大小，如果为空则使用默认值
     */
    public static int getChunkSize(Integer value) {
        return value != null ? value : DEFAULT_CHUNK_SIZE;
    }
    
    /**
     * 获取ID列名，如果为空则使用默认值
     */
    public static String getIdColumn(String value) {
        return (value != null && !value.isEmpty()) ? value : DEFAULT_ID_COLUMN;
    }
}

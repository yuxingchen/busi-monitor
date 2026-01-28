package com.monitor.backend.constant;

/**
 * 配置键名常量
 */
public final class ConfigKeys {
    private ConfigKeys() {}
    
    // CONSTANT 步骤配置键
    public static final String CONSTANTS = "constants";
    
    // LOOP 步骤配置键
    public static final String LOOP_SOURCE = "loopSource";
    public static final String LOOP_SQL = "loopSql";
    public static final String LOOP_VARIABLE = "loopVariable";
    public static final String LOOP_FIELD = "loopField";
    public static final String CONSTANT_VALUE = "constantValue";
    public static final String SEPARATOR = "separator";
    
    // LOOP 聚合配置键
    public static final String AGGREGATE = "aggregate";
    public static final String AGGREGATE_ENABLED = "enabled";
    public static final String GROUP_BY_FIELDS = "groupByFields";
    public static final String AGGREGATE_FIELDS = "aggregateFields";
    public static final String AGGREGATE_FIELD = "field";
    public static final String AGGREGATE_METHOD = "method";
    public static final String AGGREGATE_ALIAS = "alias";
    
    // 通用配置键
    public static final String TABLE_NAME = "tableName";
    public static final String ID_COLUMN = "idColumn";
    public static final String SQL = "sql";
    public static final String CACHE_KEY = "cacheKey";
    public static final String CACHE_STRATEGY = "cacheStrategy";
    public static final String PARTITION_COUNT = "partitionCount";
    public static final String CHUNK_SIZE = "chunkSize";
}

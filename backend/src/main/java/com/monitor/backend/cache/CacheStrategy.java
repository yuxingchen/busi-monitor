package com.monitor.backend.cache;

import java.util.List;
import java.util.Map;

/**
 * 缓存策略接口
 * <p>
 * 支持多种缓存后端：本地文件、Redis、Elasticsearch、临时数据库表
 * </p>
 */
public interface CacheStrategy {

    /**
     * 缓存策略类型枚举
     */
    enum Type {
        FILE,       // 本地文件
        REDIS,      // Redis
        ES,         // Elasticsearch
        TEMP_TABLE;  // 临时数据库表

        /**
         * 从字符串转换成缓存策略类型
         */
        public static Type fromCode(String code) {
            if (code == null) {
                return Type.FILE;
            }
            for (Type s : Type.values()) {
                if (s.name().equalsIgnoreCase(code)) {
                    return s;
                }
            }
            return Type.FILE; // 默认文件缓存
        }
    }


    /**
     * 获取策略类型
     */
    Type getType();

    /**
     * 写入缓存数据
     *
     * @param cacheKey 缓存键（用于区分不同的缓存数据集）
     * @param data     数据列表
     */
    void write(String cacheKey, List<Map<String, Object>> data);

    /**
     * 读取缓存数据
     *
     * @param cacheKey 缓存键
     * @return 数据列表
     */
    List<Map<String, Object>> read(String cacheKey);

    /**
     * 分批读取缓存数据
     *
     * @param cacheKey 缓存键
     * @param offset   偏移量
     * @param limit    每批数量
     * @return 数据列表
     */
    List<Map<String, Object>> readBatch(String cacheKey, int offset, int limit);

    /**
     * 获取缓存数据条数
     *
     * @param cacheKey 缓存键
     * @return 数据条数
     */
    long count(String cacheKey);

    /**
     * 清理缓存
     *
     * @param cacheKey 缓存键
     */
    void clear(String cacheKey);

    /**
     * 检查缓存是否存在
     *
     * @param cacheKey 缓存键
     * @return 是否存在
     */
    boolean exists(String cacheKey);
}

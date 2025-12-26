package com.monitor.backend.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 通用分区策略
 * <p>
 * 支持基于ID范围的分区，将大数据量拆分为多个并行处理的分区。
 * </p>
 */
public class BatchPartitioner implements Partitioner {

    private static final Logger log = LoggerFactory.getLogger(BatchPartitioner.class);

    private final JdbcTemplate jdbcTemplate;
    private final String tableName;
    private final String idColumnName;

    /**
     * 构造分区器
     * 
     * @param jdbcTemplate  JDBC模板
     * @param tableName     表名
     * @param idColumnName  ID列名
     */
    public BatchPartitioner(JdbcTemplate jdbcTemplate, String tableName, String idColumnName) {
        this.jdbcTemplate = jdbcTemplate;
        this.tableName = tableName;
        this.idColumnName = idColumnName;
    }

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        Map<String, ExecutionContext> partitions = new HashMap<>();

        // 获取ID范围
        Long minId = getMinId();
        Long maxId = getMaxId();

        if (minId == null || maxId == null) {
            log.warn("无法获取ID范围，创建单个分区");
            ExecutionContext context = new ExecutionContext();
            context.putLong("minId", 0);
            context.putLong("maxId", Long.MAX_VALUE);
            context.putInt("partitionNo", 0);
            partitions.put("partition0", context);
            return partitions;
        }

        long range = maxId - minId + 1;
        long partitionSize = Math.max(1, range / gridSize);

        log.info("分区计算: 表={}, ID范围=[{}, {}], 总数={}, 分区数={}, 每分区约{}条",
                tableName, minId, maxId, range, gridSize, partitionSize);

        for (int i = 0; i < gridSize; i++) {
            ExecutionContext context = new ExecutionContext();
            long start = minId + (i * partitionSize);
            long end = (i == gridSize - 1) ? maxId : start + partitionSize - 1;

            context.putLong("minId", start);
            context.putLong("maxId", end);
            context.putInt("partitionNo", i);

            String partitionName = "partition" + i;
            partitions.put(partitionName, context);

            log.debug("分区{}: ID范围=[{}, {}]", i, start, end);
        }

        return partitions;
    }

    private Long getMinId() {
        try {
            String sql = "SELECT MIN(" + idColumnName + ") FROM " + tableName;
            return jdbcTemplate.queryForObject(sql, Long.class);
        } catch (Exception e) {
            log.error("获取最小ID失败: {}", e.getMessage());
            return null;
        }
    }

    private Long getMaxId() {
        try {
            String sql = "SELECT MAX(" + idColumnName + ") FROM " + tableName;
            return jdbcTemplate.queryForObject(sql, Long.class);
        } catch (Exception e) {
            log.error("获取最大ID失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 估算指定ID范围的记录数
     */
    public long estimateCount(long minId, long maxId) {
        try {
            String sql = "SELECT COUNT(*) FROM " + tableName + 
                        " WHERE " + idColumnName + " >= ? AND " + idColumnName + " <= ?";
            Long count = jdbcTemplate.queryForObject(sql, Long.class, minId, maxId);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.error("估算记录数失败: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * 创建基于时间范围的分区器
     */
    public static BatchPartitioner createTimeBasedPartitioner(
            JdbcTemplate jdbcTemplate, String tableName, String timeColumnName) {
        return new BatchPartitioner(jdbcTemplate, tableName, timeColumnName);
    }
}

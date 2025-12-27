package com.monitor.backend.batch;

import com.monitor.backend.service.IntermediateStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Map;

/**
 * 工作流批处理Job配置
 * <p>
 * 提供大数据量工作流执行的批处理能力。
 * 支持分区并行处理、中间结果缓存、数据富化等功能。
 * </p>
 */
@Configuration
public class WorkflowBatchJobConfig {

    private static final Logger log = LoggerFactory.getLogger(WorkflowBatchJobConfig.class);

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    @Qualifier("partitionTaskExecutor")
    private TaskExecutor partitionTaskExecutor;

    @Autowired
    private IntermediateStorageService intermediateStorageService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 动态数据源管理器
    @Autowired
    private com.monitor.backend.service.DataSourceManager dataSourceManager;

    // 动态表管理服务（用于写入结果表）
    @Autowired
    private com.monitor.backend.service.DynamicTableService dynamicTableService;

    /**
     * 数据分区器 - 基于ID范围分区
     */
    @Bean("batchPartitioner")
    @StepScope
    public Partitioner batchPartitioner(
            @Value("#{jobParameters['tableName'] ?: 'default_table'}") String tableName,
            @Value("#{jobParameters['idColumn'] ?: 'id'}") String idColumn) {
        return new BatchPartitioner(jdbcTemplate, tableName, idColumn);
    }

    /**
     * 数据富化处理器
     */
    @Bean("enrichmentProcessor")
    @StepScope
    public ItemProcessor<Map<String, Object>, Map<String, Object>> enrichmentProcessor() {
        return new BatchEnrichmentProcessor();
    }

    /**
     * 工作流数据抽取Job
     * <p>
     * 负责从源库按分区并行抽取数据到中间缓存。
     * 适用于第一阶段数据获取。
     * </p>
     */
    @Bean
    public Job workflowDataExtractionJob(
            @Qualifier("dataExtractionMasterStep") Step masterStep,
            BatchJobCompletionListener jobCompletionListener) {
        return new JobBuilder("workflowDataExtractionJob", jobRepository)
                .listener(jobCompletionListener) // 添加完成监听器
                .start(masterStep)
                .build();
    }

    /**
     * 数据抽取主步骤 - 分区处理
     */
    @Bean("dataExtractionMasterStep")
    public Step dataExtractionMasterStep(
            @Qualifier("dataExtractionSlaveStep") Step slaveStep,
            @Qualifier("batchPartitioner") Partitioner partitioner) {
        return new StepBuilder("dataExtractionMasterStep", jobRepository)
                .partitioner("dataExtractionSlaveStep", partitioner)
                .step(slaveStep)
                .gridSize(10) // 分区数
                .taskExecutor(partitionTaskExecutor)
                .build();
    }

    /**
     * 数据抽取从步骤 - 处理单个分区
     */
    @Bean("dataExtractionSlaveStep")
    public Step dataExtractionSlaveStep(
            @Qualifier("jdbcItemReader") ItemReader<Map<String, Object>> reader,
            @Qualifier("cacheItemWriter") ItemWriter<Map<String, Object>> writer) {
        return new StepBuilder("dataExtractionSlaveStep", jobRepository)
                .<Map<String, Object>, Map<String, Object>>chunk(1000, transactionManager)
                .reader(reader)
                .writer(writer)
                .build();
    }

    /**
     * 数据富化/关联Job
     * <p>
     * 读取中间缓存数据，进行关联查询富化处理。
     * 适用于第二阶段数据处理。
     * </p>
     */
    @Bean
    public Job workflowEnrichmentJob(
            @Qualifier("enrichmentStep") Step enrichmentStep) {
        return new JobBuilder("workflowEnrichmentJob", jobRepository)
                .start(enrichmentStep)
                .build();
    }

    /**
     * 数据富化步骤
     */
    @Bean("enrichmentStep")
    public Step enrichmentStep(
            @Qualifier("cacheItemReader") ItemReader<Map<String, Object>> reader,
            @Qualifier("enrichmentProcessor") ItemProcessor<Map<String, Object>, Map<String, Object>> processor,
            @Qualifier("resultWriter") ItemWriter<Map<String, Object>> writer) {
        return new StepBuilder("enrichmentStep", jobRepository)
                .<Map<String, Object>, Map<String, Object>>chunk(500, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }

    /**
     * JDBC数据读取器 - 从源数据库读取分区数据
     * <p>
     * 功能说明：
     * 1. 根据 JobParameters 中的 datasourceId 获取动态数据源
     * 2. 使用 stepExecutionContext 中的 minId/maxId 替换分区 SQL
     * 3. 执行查询并逐条返回数据
     * </p>
     */
    @Bean("jdbcItemReader")
    @StepScope
    public ItemReader<Map<String, Object>> jdbcItemReader(
            @Value("#{jobParameters['sql']}") String sql,
            @Value("#{jobParameters['datasourceId']}") Long datasourceId,
            @Value("#{stepExecutionContext['minId']}") Long minId,
            @Value("#{stepExecutionContext['maxId']}") Long maxId) {

        return new ItemReader<Map<String, Object>>() {
            // 数据迭代器
            private java.util.Iterator<Map<String, Object>> iterator;
            private boolean initialized = false;

            @Override
            public Map<String, Object> read() {
                // 首次调用时初始化并执行查询
                if (!initialized) {
                    initialized = true;
                    java.util.List<Map<String, Object>> results = executeQuery(sql, datasourceId, minId, maxId);
                    iterator = results.iterator();
                    log.info("分区数据读取完成: datasourceId={}, minId={}, maxId={}, 记录数={}",
                            datasourceId, minId, maxId, results.size());
                }

                // 逐条返回数据，返回 null 表示结束
                if (iterator != null && iterator.hasNext()) {
                    return iterator.next();
                }
                return null;
            }

            /**
             * 执行分区查询
             */
            private java.util.List<Map<String, Object>> executeQuery(
                    String sql, Long datasourceId, Long minId, Long maxId) {
                try {
                    // 获取数据源：datasourceId=0 或 null 使用本地监控库
                    org.springframework.jdbc.core.JdbcTemplate jdbcTpl;
                    if (datasourceId == null || datasourceId == 0) {
                        jdbcTpl = jdbcTemplate;
                    } else {
                        javax.sql.DataSource ds = dataSourceManager.getDataSource(datasourceId);
                        jdbcTpl = new org.springframework.jdbc.core.JdbcTemplate(ds);
                    }

                    // 替换分区参数
                    String partitionedSql = sql;
                    if (minId != null && maxId != null) {
                        partitionedSql = sql
                                .replace(":minId", String.valueOf(minId))
                                .replace(":maxId", String.valueOf(maxId));
                    }

                    log.debug("执行分区SQL: {}", partitionedSql);
                    return jdbcTpl.queryForList(partitionedSql);
                } catch (Exception e) {
                    log.error("分区查询失败: sql={}, error={}", sql, e.getMessage(), e);
                    return java.util.Collections.emptyList();
                }
            }
        };
    }

    /**
     * 缓存写入器 - 将数据写入中间缓存
     */
    @Bean("cacheItemWriter")
    @StepScope
    public ItemWriter<Map<String, Object>> cacheItemWriter(
            @Value("#{jobParameters['cacheKey']}") String cacheKey) {

        return items -> {
            log.info("写入缓存，数据条数: {}", items.size());
            java.util.List<Map<String, Object>> dataList = new java.util.ArrayList<>();
            items.forEach(dataList::add);
            intermediateStorageService.write(cacheKey, dataList);
        };
    }

    /**
     * 缓存读取器 - 从中间缓存读取数据
     */
    @Bean("cacheItemReader")
    @StepScope
    public ItemReader<Map<String, Object>> cacheItemReader(
            @Value("#{jobParameters['cacheKey']}") String cacheKey) {

        return new ItemReader<>() {
            private java.util.Iterator<Map<String, Object>> iterator;
            private int offset = 0;
            private final int batchSize = 1000;

            @Override
            public Map<String, Object> read() {
                if (iterator == null || !iterator.hasNext()) {
                    var batch = intermediateStorageService.readBatch(cacheKey, offset, batchSize);
                    if (batch.isEmpty()) {
                        return null;
                    }
                    iterator = batch.iterator();
                    offset += batch.size();
                }
                return iterator.hasNext() ? iterator.next() : null;
            }
        };
    }

    /**
     * 结果写入器 - 将处理结果写入目标表
     * <p>
     * 功能说明：
     * 1. 调用 DynamicTableService.saveWorkflowResult() 写入结果
     * 2. 自动创建结果表（如不存在）
     * 3. 添加 execution_id 和 execution_time 列
     * </p>
     */
    @Bean("resultWriter")
    @StepScope
    public ItemWriter<Map<String, Object>> resultWriter(
            @Value("#{jobParameters['outputTable']}") String outputTable,
            @Value("#{jobParameters['executionId']}") Long executionId) {

        return items -> {
            if (items.isEmpty()) {
                log.info("没有数据需要写入: table={}", outputTable);
                return;
            }

            // 将 Chunk 转换为 List
            java.util.List<Map<String, Object>> dataList = new java.util.ArrayList<>();
            items.forEach(dataList::add);

            try {
                // 调用 DynamicTableService 写入结果表
                dynamicTableService.saveWorkflowResult(outputTable, dataList, executionId);
                log.info("成功写入结果表: table={}, count={}, executionId={}",
                        outputTable, dataList.size(), executionId);
            } catch (Exception e) {
                log.error("写入结果表失败: table={}, error={}", outputTable, e.getMessage(), e);
                throw new RuntimeException("写入结果失败: " + e.getMessage(), e);
            }
        };
    }
}

# 工作流批处理集成 - 完整设计方案

## 一、数据表关系

### 工作流表与 Spring Batch 表关联说明

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           数据表关系图                                        │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────┐                                                    │
│  │   monitor_workflow   │──────────────┬──────────────┐                     │
│  │                     │               │              │                     │
│  │ • id               │               │              │                     │
│  │ • name             │               │              │                     │
│  │ • batch_mode (0/1) │               │              │                     │
│  │ • cache_strategy   │               │              │                     │
│  │ • partition_count  │               │              │                     │
│  │ • chunk_size       │               │              │                     │
│  │ • output_table     │               │              │                     │
│  └─────────────────────┘               │              │                     │
│            │                           │              │                     │
│            │ 1:N                       │ 1:N         │                     │
│            ▼                           ▼              │                     │
│  ┌─────────────────────┐    ┌─────────────────────┐  │                     │
│  │ monitor_workflow_   │    │ monitor_workflow_   │  │                     │
│  │ step               │    │ execution           │  │                     │
│  │                     │    │                     │  │                     │
│  │ • id               │    │ • id               │  │                     │
│  │ • workflow_id  FK  │    │ • workflow_id  FK  │  │                     │
│  │ • step_order       │    │ • status           │──┼─────────┐           │
│  │ • step_type        │    │ • step_results*    │  │         │           │
│  │ • datasource_id    │    │ • start_time       │  │         │           │
│  │ • sql_script       │    │ • end_time         │  │         │           │
│  │ • result_variable  │    └─────────────────────┘  │         │           │
│  └─────────────────────┘              │              │         │           │
│                                       │              │         │           │
│  * step_results 存储:                 │              │         │           │
│    {"batchExecutionId": 201}          │ 关联         │         │           │
│                                       ▼              │         │           │
│  ┌────────────────────────────────────────────────────────────┐│           │
│  │              Spring Batch 表 (框架自动维护)                  ││           │
│  ├────────────────────────────────────────────────────────────┤│           │
│  │                                                            ││           │
│  │  ┌─────────────────────┐      ┌─────────────────────┐     ││           │
│  │  │ BATCH_JOB_INSTANCE  │──1:N─│ BATCH_JOB_EXECUTION │     ││           │
│  │  │                     │      │                     │     ││           │
│  │  │ • job_instance_id   │      │ • job_execution_id │◄────┼┼───────────┘
│  │  │ • job_name          │      │ • job_instance_id  │     ││
│  │  │ • job_key           │      │ • status           │     ││
│  │  └─────────────────────┘      │ • start_time       │     ││
│  │                               │ • end_time         │     ││
│  │                               └──────────┬──────────┘     ││
│  │                                          │ 1:N            ││
│  │                                          ▼                ││
│  │  ┌─────────────────────┐      ┌─────────────────────┐     ││
│  │  │ BATCH_JOB_EXECUTION │      │ BATCH_STEP_         │     ││
│  │  │ _PARAMS             │      │ EXECUTION           │     ││
│  │  │                     │      │                     │     ││
│  │  │ • job_execution_id  │      │ • step_execution_id│     ││
│  │  │ • parameter_name    │      │ • job_execution_id │     ││
│  │  │ • parameter_value   │      │ • step_name        │     ││
│  │  │                     │      │ • read_count       │     ││
│  │  │ 存储:               │      │ • write_count      │     ││
│  │  │ - workflowId        │      │ • status           │     ││
│  │  │ - sql               │      └─────────────────────┘     ││
│  │  │ - datasourceId      │                                  ││
│  │  │ - cacheKey          │                                  ││
│  │  │ - outputTable       │                                  ││
│  │  └─────────────────────┘                                  ││
│  └────────────────────────────────────────────────────────────┘│
│                                                                │
│  ┌────────────────────────────────────────────────────────────┐│
│  │                    辅助表                                   ││
│  ├────────────────────────────────────────────────────────────┤│
│  │  ┌─────────────────────┐      ┌─────────────────────┐     ││
│  │  │ batch_cache_        │      │ batch_performance_  │     ││
│  │  │ metadata            │      │ log                 │     ││
│  │  │                     │      │                     │     ││
│  │  │ • cache_key         │      │ • job_name          │     ││
│  │  │ • cache_type        │      │ • execution_id      │     ││
│  │  │ • record_count      │      │ • step_name         │     ││
│  │  │ • execution_id      │      │ • read_count        │     ││
│  │  └─────────────────────┘      │ • write_count       │     ││
│  │                               │ • duration_ms       │     ││
│  │                               └─────────────────────┘     ││
│  └────────────────────────────────────────────────────────────┘│
│                                                                │
│  ┌────────────────────────────────────────────────────────────┐│
│  │              结果表 (动态创建)                               ││
│  ├────────────────────────────────────────────────────────────┤│
│  │  monitor_wf_result_{workflow_id}                           ││
│  │  • execution_id       -- 关联执行记录                       ││
│  │  • [业务字段...]       -- 来自SQL查询结果                    ││
│  │  • execution_time     -- 执行时间戳                         ││
│  └────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
```

---

## 二、前后端交互时序

### Phase 1: 创建批处理工作流

```
用户浏览器          Vue前端           WorkflowController        数据库
    │                  │                     │                    │
    │ 点击"新建工作流"  │                     │                    │
    ├─────────────────>│                     │                    │
    │                  │ 显示对话框           │                    │
    │                  │ 选择"批处理模式"     │                    │
    │                  │<────────────────────│                    │
    │                  │                     │                    │
    │ 填写配置并点击创建 │                     │                    │
    ├─────────────────>│                     │                    │
    │                  │ POST /api/workflows │                    │
    │                  │ {                   │                    │
    │                  │   name: "订单抽取",  │                    │
    │                  │   batchMode: 1,     │                    │
    │                  │   partitionCount:10,│                    │
    │                  │   chunkSize: 1000   │                    │
    │                  │ }                   │                    │
    │                  ├────────────────────>│                    │
    │                  │                     │ INSERT             │
    │                  │                     │ monitor_workflow   │
    │                  │                     ├───────────────────>│
    │                  │                     │                    │
    │                  │                     │     id=101         │
    │                  │                     │<───────────────────│
    │                  │  {id:101, ...}      │                    │
    │                  │<────────────────────│                    │
    │ 显示工作流画布    │                     │                    │
    │<─────────────────│                     │                    │
```

### Phase 2: 配置工作流步骤

```
用户浏览器          Vue前端           WorkflowController        数据库
    │                  │                     │                    │
    │ 拖拽添加SQL步骤   │                     │                    │
    ├─────────────────>│                     │                    │
    │                  │                     │                    │
    │ 配置SQL并保存     │                     │                    │
    ├─────────────────>│                     │                    │
    │                  │ PUT /api/workflows/101                   │
    │                  │ {                   │                    │
    │                  │   steps: [{        │                    │
    │                  │     stepType:"SQL", │                    │
    │                  │     datasourceId:5, │                    │
    │                  │     sqlScript:"..." │                    │
    │                  │   }]               │                    │
    │                  │ }                   │                    │
    │                  ├────────────────────>│                    │
    │                  │                     │ DELETE old steps   │
    │                  │                     │ INSERT new steps   │
    │                  │                     ├───────────────────>│
    │                  │    保存成功         │                    │
    │                  │<────────────────────│                    │
    │<─────────────────│                     │                    │
```

### Phase 3: 执行批处理工作流

```
用户浏览器      Vue前端     WorkflowController  ExecutorService   Spring Batch   数据库
    │              │              │                  │                │           │
    │ 点击"执行"   │              │                  │                │           │
    ├─────────────>│              │                  │                │           │
    │              │ POST /execute│                  │                │           │
    │              ├─────────────>│                  │                │           │
    │              │              │ execute(101)     │                │           │
    │              │              ├─────────────────>│                │           │
    │              │              │                  │                │           │
    │              │              │                  │ SELECT workflow│           │
    │              │              │                  ├────────────────┼──────────>│
    │              │              │                  │  batchMode=1   │           │
    │              │              │                  │<───────────────┼───────────│
    │              │              │                  │                │           │
    │              │              │                  │ executeBatchMode()         │
    │              │              │                  │                │           │
    │              │              │                  │ INSERT execution           │
    │              │              │                  ├────────────────┼──────────>│
    │              │              │                  │ id=5001        │           │
    │              │              │                  │<───────────────┼───────────│
    │              │              │                  │                │           │
    │              │              │                  │ launch(job)    │           │
    │              │              │                  ├───────────────>│           │
    │              │              │                  │                │           │
    │              │              │                  │                │ INSERT    │
    │              │              │                  │                │ BATCH_JOB_│
    │              │              │                  │                │ INSTANCE/ │
    │              │              │                  │                │ EXECUTION │
    │              │              │                  │                ├──────────>│
    │              │              │                  │                │           │
    │              │              │                  │ jobExecution   │           │
    │              │              │                  │ .id=201        │           │
    │              │              │                  │<───────────────│           │
    │              │              │                  │                │           │
    │              │              │                  │ UPDATE execution           │
    │              │              │                  │ status=BATCH_STARTED       │
    │              │              │                  │ stepResults=               │
    │              │              │                  │ {"batchExecutionId":201}   │
    │              │              │                  ├────────────────┼──────────>│
    │              │              │                  │                │           │
    │              │              │  execution       │                │           │
    │              │              │<─────────────────│                │           │
    │              │ {            │                  │                │           │
    │              │  id:5001,    │                  │                │           │
    │              │  status:     │                  │                │           │
    │              │  "BATCH_     │                  │                │           │
    │              │   STARTED"   │                  │                │           │
    │              │ }            │                  │                │           │
    │              │<─────────────│                  │                │           │
    │              │              │                  │                │           │
    │ "批处理已启动"│              │                  │                │           │
    │<─────────────│              │                  │                │           │
```

### Phase 4: Spring Batch 内部执行 (异步)

```
Spring Batch                     业务数据库                 缓存服务              监控数据库
    │                                 │                        │                    │
    │ SELECT MIN(id), MAX(id)         │                        │                    │
    │ FROM orders                     │                        │                    │
    │ WHERE amount > 10000            │                        │                    │
    ├────────────────────────────────>│                        │                    │
    │ min=1000, max=100000            │                        │                    │
    │<────────────────────────────────│                        │                    │
    │                                 │                        │                    │
    │ Partitioner 生成10个分区:       │                        │                    │
    │ partition0: [1000, 10900]       │                        │                    │
    │ partition1: [10901, 20800]      │                        │                    │
    │ ...                             │                        │                    │
    │ partition9: [91001, 100000]     │                        │                    │
    │                                 │                        │                    │
    ├─────────────────────────────────┼────────────────────────┼────────────────────┤
    │              并行执行10个分区                                                  │
    ├─────────────────────────────────┼────────────────────────┼────────────────────┤
    │                                 │                        │                    │
    │ [Thread-1] SELECT ... WHERE     │                        │                    │
    │   id BETWEEN 1000 AND 10900     │                        │                    │
    ├────────────────────────────────>│                        │                    │
    │                 990 rows        │                        │                    │
    │<────────────────────────────────│                        │                    │
    │                                 │                        │                    │
    │ [Thread-1] write("wf_101_p0")   │                        │                    │
    ├─────────────────────────────────┼───────────────────────>│                    │
    │                                 │                        │                    │
    │ [Thread-2] SELECT ... WHERE     │                        │                    │
    │   id BETWEEN 10901 AND 20800    │                        │                    │
    ├────────────────────────────────>│                        │                    │
    │                1005 rows        │                        │                    │
    │<────────────────────────────────│                        │                    │
    │                                 │                        │                    │
    │ [Thread-2] write("wf_101_p1")   │                        │                    │
    ├─────────────────────────────────┼───────────────────────>│                    │
    │                                 │                        │                    │
    │ ... (其他分区并行执行)          │                        │                    │
    │                                 │                        │                    │
    ├─────────────────────────────────┼────────────────────────┼────────────────────┤
    │              所有分区完成                                                      │
    ├─────────────────────────────────┼────────────────────────┼────────────────────┤
    │                                 │                        │                    │
    │ INSERT batch_cache_metadata     │                        │                    │
    │ INSERT batch_performance_log    │                        │                    │
    │ UPDATE BATCH_STEP_EXECUTION     │                        │                    │
    │ UPDATE BATCH_JOB_EXECUTION      │                        │                    │
    │   status=COMPLETED              │                        │                    │
    ├─────────────────────────────────┼────────────────────────┼───────────────────>│
    │                                 │                        │                    │
```

### Phase 5: 查询执行状态 (前端轮询)

```
用户浏览器          Vue前端       BatchMonitorController       数据库
    │                  │                     │                    │
    │                  │                     │                    │
    │                  │ 每2秒轮询           │                    │
    │                  │ GET /batch/status/201                    │
    │                  ├────────────────────>│                    │
    │                  │                     │ SELECT * FROM      │
    │                  │                     │ BATCH_JOB_EXECUTION│
    │                  │                     │ WHERE id=201       │
    │                  │                     ├───────────────────>│
    │                  │                     │                    │
    │                  │                     │ SELECT * FROM      │
    │                  │                     │ BATCH_STEP_EXECUTION│
    │                  │                     │ WHERE job_exec=201 │
    │                  │                     ├───────────────────>│
    │                  │                     │                    │
    │                  │ {                   │                    │
    │                  │   status:"RUNNING", │                    │
    │                  │   steps: [          │                    │
    │                  │     {name:"p0",     │                    │
    │                  │      readCount:990} │                    │
    │                  │   ]                 │                    │
    │                  │ }                   │                    │
    │                  │<────────────────────│                    │
    │                  │                     │                    │
    │ 显示进度:        │                     │                    │
    │ 已处理 990 条    │                     │                    │
    │<─────────────────│                     │                    │
    │                  │                     │                    │
    │                  │ ... 继续轮询直到    │                    │
    │                  │ status=COMPLETED    │                    │
    │                  │                     │                    │
    │ "批处理完成,     │                     │                    │
    │  共处理9900条"   │                     │                    │
    │<─────────────────│                     │                    │
```

---

## 三、全链路数据存放位置

| 阶段 | 数据类型 | 存放表/位置 | 关键字段 |
|------|----------|-------------|----------|
| **创建** | 工作流配置 | `monitor_workflow` | batch_mode, cache_strategy, partition_count |
| **创建** | 步骤配置 | `monitor_workflow_step` | sql_script, datasource_id, result_variable |
| **执行启动** | 执行记录 | `monitor_workflow_execution` | status, step_results(含batchExecutionId) |
| **Batch执行** | Job实例 | `BATCH_JOB_INSTANCE` | job_name, job_key |
| **Batch执行** | Job执行 | `BATCH_JOB_EXECUTION` | status, start_time, end_time |
| **Batch执行** | Job参数 | `BATCH_JOB_EXECUTION_PARAMS` | workflowId, sql, cacheKey 等 |
| **Batch执行** | Step执行 | `BATCH_STEP_EXECUTION` | step_name, read_count, write_count |
| **中间结果** | 缓存数据 | FILE/REDIS/ES/TEMP_TABLE | 由 cacheStrategy 决定 |
| **中间结果** | 缓存元数据 | `batch_cache_metadata` | cache_key, record_count |
| **性能监控** | 执行日志 | `batch_performance_log` | duration_ms, records_per_second |
| **最终结果** | 业务数据 | `monitor_wf_result_{id}` | execution_id, 业务字段 |

---

## 四、实例：单 SQL 批处理全链路数据

### 工作流配置

```sql
-- 1. 工作流配置 (monitor_workflow)
INSERT INTO monitor_workflow 
  (id, name, batch_mode, cache_strategy, partition_count, chunk_size, output_table)
VALUES 
  (101, '大订单抽取', 1, 'FILE', 10, 1000, 'monitor_wf_result_101');

-- 2. 步骤配置 (monitor_workflow_step)
INSERT INTO monitor_workflow_step 
  (id, workflow_id, step_order, step_type, datasource_id, sql_script, result_variable)
VALUES 
  (1, 101, 1, 'SQL', 5, 
   'SELECT order_id, user_id, amount FROM orders WHERE amount > 10000', 
   'bigOrders');
```

### 执行后各表数据

```sql
-- 3. 工作流执行记录 (monitor_workflow_execution)
SELECT * FROM monitor_workflow_execution WHERE id = 5001;
+------+-------------+----------------+-------------------------------------+---------------------+---------------------+
| id   | workflow_id | status         | step_results                        | start_time          | end_time            |
+------+-------------+----------------+-------------------------------------+---------------------+---------------------+
| 5001 | 101         | SUCCESS        | {"batchExecutionId":201,            | 2024-12-20 15:00:00 | 2024-12-20 15:02:30 |
|      |             |                |  "batchJobName":"workflowData..."}  |                     |                     |
+------+-------------+----------------+-------------------------------------+---------------------+---------------------+

-- 4. Spring Batch Job 执行 (BATCH_JOB_EXECUTION)
SELECT * FROM BATCH_JOB_EXECUTION WHERE JOB_EXECUTION_ID = 201;
+------------------+---------+---------------------+---------------------+-----------+
| JOB_EXECUTION_ID | VERSION | START_TIME          | END_TIME            | STATUS    |
+------------------+---------+---------------------+---------------------+-----------+
| 201              | 2       | 2024-12-20 15:00:01 | 2024-12-20 15:02:28 | COMPLETED |
+------------------+---------+---------------------+---------------------+-----------+

-- 5. Job 参数 (BATCH_JOB_EXECUTION_PARAMS)
SELECT * FROM BATCH_JOB_EXECUTION_PARAMS WHERE JOB_EXECUTION_ID = 201;
+------------------+------------------+---------------------------------------------+
| JOB_EXECUTION_ID | PARAMETER_NAME   | PARAMETER_VALUE                             |
+------------------+------------------+---------------------------------------------+
| 201              | workflowId       | 101                                         |
| 201              | sql              | SELECT order_id, user_id, amount FROM or... |
| 201              | datasourceId     | 5                                           |
| 201              | cacheKey         | wf_101_step1                                |
| 201              | outputTable      | monitor_wf_result_101                       |
| 201              | partitionCount   | 10                                          |
+------------------+------------------+---------------------------------------------+

-- 6. Step 执行记录 (BATCH_STEP_EXECUTION) - 每个分区一条
SELECT STEP_NAME, READ_COUNT, WRITE_COUNT, STATUS FROM BATCH_STEP_EXECUTION 
WHERE JOB_EXECUTION_ID = 201;
+---------------------------------------------+------------+-------------+-----------+
| STEP_NAME                                   | READ_COUNT | WRITE_COUNT | STATUS    |
+---------------------------------------------+------------+-------------+-----------+
| dataExtractionSlaveStep:partition0          | 990        | 990         | COMPLETED |
| dataExtractionSlaveStep:partition1          | 1005       | 1005        | COMPLETED |
| dataExtractionSlaveStep:partition2          | 978        | 978         | COMPLETED |
| ...                                         | ...        | ...         | ...       |
| dataExtractionSlaveStep:partition9          | 1020       | 1020        | COMPLETED |
+---------------------------------------------+------------+-------------+-----------+

-- 7. 缓存元数据 (batch_cache_metadata)
SELECT * FROM batch_cache_metadata WHERE cache_key LIKE 'wf_101%';
+------+---------------------------+------------+--------------+
| id   | cache_key                 | cache_type | record_count |
+------+---------------------------+------------+--------------+
| 1    | wf_101_step1_partition0   | FILE       | 990          |
| 2    | wf_101_step1_partition1   | FILE       | 1005         |
| ...  | ...                       | ...        | ...          |
| 10   | wf_101_step1_partition9   | FILE       | 1020         |
+------+---------------------------+------------+--------------+

-- 8. 最终结果表 (monitor_wf_result_101)
SELECT * FROM monitor_wf_result_101 LIMIT 5;
+--------------+----------+---------+--------+---------------------+
| execution_id | order_id | user_id | amount | execution_time      |
+--------------+----------+---------+--------+---------------------+
| 5001         | 1001     | 123     | 15000  | 2024-12-20 15:02:28 |
| 5001         | 1002     | 456     | 22000  | 2024-12-20 15:02:28 |
| 5001         | 1003     | 789     | 18500  | 2024-12-20 15:02:28 |
| 5001         | 1004     | 234     | 31000  | 2024-12-20 15:02:28 |
| 5001         | 1005     | 567     | 12500  | 2024-12-20 15:02:28 |
+--------------+----------+---------+--------+---------------------+
-- 共 9900 条记录
```

---

## 五、前端轮询代码示例

```javascript
// WorkflowDesigner.vue

/**
 * 执行工作流
 * 批处理模式启动后，自动开始轮询执行状态
 */
const executeWorkflow = async () => {
  executing.value = true
  try {
    const res = await axios.post(`/api/workflows/${workflow.value.id}/execute`)
    const execution = res.data
    
    // 检查是否为批处理模式
    if (execution.status === 'BATCH_STARTED') {
      // 解析 batchExecutionId
      const stepResults = JSON.parse(execution.stepResults)
      const batchExecutionId = stepResults.batchExecutionId
      
      // 开始轮询批处理状态
      ElMessage.info('批处理已启动，正在执行中...')
      pollBatchStatus(batchExecutionId)
    } else {
      // 普通模式直接返回结果
      ElMessage.success(`执行${execution.status === 'SUCCESS' ? '成功' : '失败'}`)
    }
  } catch (e) {
    ElMessage.error('执行失败: ' + (e.response?.data?.message || e.message))
  } finally {
    executing.value = false
  }
}

/**
 * 轮询批处理执行状态
 * 每2秒查询一次，直到状态变为 COMPLETED 或 FAILED
 */
const pollBatchStatus = async (batchExecutionId) => {
  const pollInterval = 2000  // 2秒
  
  const poll = async () => {
    try {
      const res = await axios.get(`/api/batch/status/${batchExecutionId}`)
      const { status, steps } = res.data
      
      // 计算总进度
      const totalRead = steps.reduce((sum, s) => sum + (s.readCount || 0), 0)
      const totalWrite = steps.reduce((sum, s) => sum + (s.writeCount || 0), 0)
      
      // 更新进度显示 (需要添加 batchProgress 响应式变量)
      batchProgress.value = {
        status,
        readCount: totalRead,
        writeCount: totalWrite,
        completedSteps: steps.filter(s => s.status === 'COMPLETED').length,
        totalSteps: steps.length
      }
      
      // 检查是否完成
      if (status === 'COMPLETED') {
        ElMessage.success(`批处理完成，共处理 ${totalWrite.toLocaleString()} 条数据`)
        batchProgress.value = null
      } else if (status === 'FAILED') {
        ElMessage.error('批处理执行失败，请检查日志')
        batchProgress.value = null
      } else {
        // 继续轮询
        setTimeout(poll, pollInterval)
      }
    } catch (e) {
      console.error('查询批处理状态失败', e)
      setTimeout(poll, pollInterval)
    }
  }
  
  poll()
}
```

---

## 六、后续实现优先级

| 优先级 | 任务 | 涉及文件 |
|--------|------|---------|
| P0 | `executeBatchMode` 传入工作流步骤SQL | `WorkflowExecutorService.java` |
| P0 | `jdbcItemReader` 使用真实SQL和数据源 | `WorkflowBatchJobConfig.java` |
| P1 | 前端执行后轮询批处理状态 | `WorkflowDesigner.vue` |
| P1 | 结果写入调用 `DynamicTableService` | `WorkflowBatchJobConfig.java` |
| P2 | 任务完成后更新 `monitor_workflow_execution` | 新增 `BatchJobListener.java` |
| P2 | 缓存元数据写入 | `batch_cache_metadata` 表操作 |

<template>
  <div class="workflow-designer">
    <!-- 工具栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <el-button type="primary" :icon="Plus" @click="createWorkflow">新建工作流</el-button>
        <el-select v-model="selectedWorkflowId" placeholder="选择工作流" style="width: 200px" @change="loadWorkflow">
          <el-option v-for="wf in workflows" :key="wf.id" :label="wf.name" :value="wf.id" />
        </el-select>
      </div>
      <div class="toolbar-right" v-if="workflow.id">
        <!-- 批处理进度显示 -->
        <div v-if="batchPolling && batchProgress" class="batch-progress">
          <el-tag type="info" size="small">
            <el-icon class="is-loading">
              <Loading />
            </el-icon>
            {{ batchProgress.status }}
          </el-tag>
          <span class="progress-stats">
            读取: {{ batchProgress.readCount?.toLocaleString() || 0 }} |
            写入: {{ batchProgress.writeCount?.toLocaleString() || 0 }}
          </span>
          <el-button size="small" type="danger" @click="stopBatchPolling">取消</el-button>
        </div>
        <el-button :icon="VideoPlay" @click="executeWorkflow" :loading="executing"
          :disabled="batchPolling">执行</el-button>
        <el-button type="primary" :icon="Check" @click="saveWorkflow" :loading="saving">保存</el-button>
        <el-button type="danger" :icon="Delete" @click="deleteWorkflow">删除</el-button>
      </div>
    </div>

    <!-- 主内容区 -->
    <div class="main-content">
      <!-- 左侧步骤面板 -->
      <div class="step-panel">
        <h4>拖拽添加步骤</h4>
        <div class="step-item" draggable="true" @dragstart="onDragStart('SQL')">
          <el-icon>
            <Coin />
          </el-icon>
          <span>SQL 步骤</span>
        </div>
        <div class="step-item" draggable="true" @dragstart="onDragStart('CONSTANT')">
          <el-icon>
            <Document />
          </el-icon>
          <span>常量定义</span>
        </div>
        <div class="step-item" draggable="true" @dragstart="onDragStart('LOOP')">
          <el-icon>
            <Refresh />
          </el-icon>
          <span>循环步骤</span>
        </div>

        <div class="divider"></div>

        <h4>工作流配置</h4>
        <el-form v-if="workflow.id" label-position="top" size="small">
          <el-form-item label="名称">
            <el-input v-model="workflow.name" />
          </el-form-item>
          <el-form-item label="Cron 表达式">
            <el-input v-model="workflow.cronExpression" placeholder="0 0 * * * ?" />
          </el-form-item>
          <el-form-item label="超时(秒)">
            <el-input-number v-model="workflow.timeoutSeconds" :min="60" :max="86400" />
          </el-form-item>
          <el-form-item label="结果表名">
            <el-input v-model="workflow.outputTable" :placeholder="`monitor_wf_result_${workflow.id}`" />
          </el-form-item>
          <el-form-item>
            <template #label>
              <span>索引字段</span>
              <el-tooltip placement="right" effect="dark">
                <template #content>
                  <div class="placeholder-tooltip">
                    <div class="tooltip-title">索引字段说明</div>
                    <div class="tooltip-item">多个字段用逗号分隔</div>
                    <div class="tooltip-item">如：<code>order_id,product_name</code></div>
                    <div class="tooltip-item">留空则自动分析SQL</div>
                  </div>
                </template>
                <el-icon class="label-tip-icon">
                  <QuestionFilled />
                </el-icon>
              </el-tooltip>
            </template>
            <el-input v-model="workflow.indexFields" placeholder="留空则自动检测" />
          </el-form-item>

          <el-form-item label="启用">
            <el-switch v-model="workflowActive" />
          </el-form-item>
        </el-form>
      </div>

      <!-- 中间画布 -->
      <div class="canvas-container" @drop="onDrop" @dragover.prevent>
        <VueFlow v-model:nodes="nodes" v-model:edges="edges" @node-click="onNodeClick" @connect="onConnect"
          :default-edge-options="{ type: 'smoothstep', animated: true }" fit-view-on-init>
          <template #node-sql="nodeProps">
            <div class="sql-node" :class="{ selected: nodeProps.selected }">
              <div class="node-header">
                <el-icon>
                  <Coin />
                </el-icon>
                <span>{{ nodeProps.data.label }}</span>
              </div>
              <div class="node-body">
                <div class="node-info">变量: {{ nodeProps.data.resultVariable || '-' }}</div>
              </div>
              <Handle type="target" :position="Position.Top" />
              <Handle type="source" :position="Position.Bottom" />
            </div>
          </template>
          <template #node-constant="nodeProps">
            <div class="constant-node" :class="{ selected: nodeProps.selected }">
              <div class="node-header">
                <el-icon>
                  <Document />
                </el-icon>
                <span>{{ nodeProps.data.label }}</span>
              </div>
              <div class="node-body">
                <div class="node-info">常量: {{ getConstantCount(nodeProps.data.config) }}</div>
              </div>
              <Handle type="target" :position="Position.Top" />
              <Handle type="source" :position="Position.Bottom" />
            </div>
          </template>
          <template #node-loop="nodeProps">
            <div class="loop-node" :class="{ selected: nodeProps.selected }">
              <div class="node-header">
                <el-icon>
                  <Refresh />
                </el-icon>
                <span>{{ nodeProps.data.label }}</span>
              </div>
              <div class="node-body">
                <div class="node-info">模式: {{ getLoopMode(nodeProps.data.config) }}</div>
              </div>
              <Handle type="target" :position="Position.Top" />
              <Handle type="source" :position="Position.Bottom" />
            </div>
          </template>
          <Background />
          <Controls />
        </VueFlow>

        <div v-if="!workflow.id" class="empty-state">
          <el-icon class="empty-icon">
            <Share />
          </el-icon>
          <p>请选择或创建一个工作流</p>
        </div>
      </div>

      <!-- 右侧配置面板 -->
      <div class="config-panel" v-if="selectedNode">
        <h4>步骤配置</h4>
        <el-form label-position="top" size="small">
          <el-form-item label="步骤名称">
            <el-input v-model="selectedNode.data.label" @change="updateNode" />
          </el-form-item>
          <el-form-item label="结果变量名">
            <el-input v-model="selectedNode.data.resultVariable" placeholder="用于后续步骤引用" @change="updateNode" />
          </el-form-item>

          <!-- SQL 类型配置 -->
          <template v-if="selectedNode.data.stepType === 'SQL'">
            <el-form-item label="数据源">
              <el-select v-model="selectedNode.data.datasourceId" placeholder="选择数据源" style="width: 100%"
                @change="updateNode">
                <el-option :value="0" label="本地监控库" />
                <el-option v-for="ds in datasources" :key="ds.id" :label="ds.name" :value="ds.id" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <template #label>
                <span>SQL 脚本</span>
                <el-tooltip placement="right" :show-arrow="true" effect="dark">
                  <template #content>
                    <div class="placeholder-tooltip">
                      <div class="tooltip-title">支持的动态占位符：</div>
                      <div class="tooltip-item"><code>${now}</code> 当前时间</div>
                      <div class="tooltip-item"><code>${today}</code> 今天日期</div>
                      <div class="tooltip-item"><code>${yesterday}</code> 昨天日期</div>
                      <div class="tooltip-item"><code>${yesterdayStart}</code> 昨天 00:00:00</div>
                      <div class="tooltip-item"><code>${yesterdayEnd}</code> 昨天 23:59:59</div>
                      <div class="tooltip-item"><code>${lastRunTime}</code> 上次执行时间</div>
                      <div class="tooltip-item"><code>${todayStart}</code> 今天 00:00:00</div>
                      <div class="tooltip-item"><code>${todayEnd}</code> 今天 23:59:59</div>
                      <div class="tooltip-item"><code>${stepName.field}</code> 引用前序步骤结果</div>
                      <div class="tooltip-item"><code>${const.xxx}</code> 引用常量</div>
                      <div class="tooltip-item"><code>${loop.value}</code> 循环当前值</div>
                      <div class="tooltip-item"><code>${loop.index}</code> 循环索引</div>
                      <div class="tooltip-item"><code>${env.xxx}</code> 环境变量</div>
                      <div class="tooltip-example">示例: WHERE create_time > '${lastRunTime}'</div>
                    </div>
                  </template>
                  <el-icon class="label-tip-icon">
                    <QuestionFilled />
                  </el-icon>
                </el-tooltip>
              </template>
              <el-input type="textarea" v-model="selectedNode.data.sqlScript" :rows="8"
                placeholder="SELECT * FROM table WHERE ..." @change="updateNode" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" size="small" @click="testStep" :loading="testing">测试执行</el-button>
              <el-button type="danger" size="small" @click="deleteNode">删除步骤</el-button>
            </el-form-item>

            <!-- 批处理配置 -->
            <div class="divider"></div>
            <h5 style="margin: 8px 0; color: #606266;">批处理配置</h5>

            <el-form-item>
              <template #label>
                <span>启用批处理</span>
                <el-tooltip placement="right" effect="dark">
                  <template #content>
                    <div class="placeholder-tooltip">
                      <div class="tooltip-title">步骤级批处理</div>
                      <div class="tooltip-item">开启后使用Spring Batch执行</div>
                      <div class="tooltip-item">适合大数据量(>10万条)场景</div>
                      <div class="tooltip-item" style="color: #67c23a;">✓ 支持: SELECT、WHERE、ORDER BY</div>
                      <div class="tooltip-item" style="color: #f56c6c;">✗ 不支持: GROUP BY、DISTINCT、窗口函数</div>
                    </div>
                  </template>
                  <el-icon class="label-tip-icon">
                    <QuestionFilled />
                  </el-icon>
                </el-tooltip>
              </template>
              <el-switch v-model="stepBatchEnabled" @change="updateNode" />
            </el-form-item>

            <template v-if="stepBatchEnabled">
              <el-form-item label="分区字段">
                <el-input v-model="selectedNode.data.idColumn" placeholder="留空自动识别主键" @change="updateNode" />
              </el-form-item>
              <el-form-item label="分区数">
                <el-input-number v-model="selectedNode.data.partitionCount" :min="1" :max="50" :step="1"
                  @change="updateNode" />
              </el-form-item>
              <el-form-item label="Chunk大小">
                <el-input-number v-model="selectedNode.data.chunkSize" :min="100" :max="10000" :step="100"
                  @change="updateNode" />
              </el-form-item>
              <el-form-item label="缓存策略">
                <el-select v-model="selectedNode.data.cacheStrategy" placeholder="选择缓存策略" style="width: 100%"
                  @change="updateNode">
                  <el-option value="FILE" label="本地文件 (10万-1000万)" />
                  <el-option value="REDIS" label="Redis (<10万)" />
                  <el-option value="ES" label="Elasticsearch (>1000万)" />
                  <el-option value="TEMP_TABLE" label="临时表 (复杂聚合)" />
                </el-select>
              </el-form-item>
            </template>
          </template>

          <!-- CONSTANT 类型配置 -->
          <template v-else-if="selectedNode.data.stepType === 'CONSTANT'">
            <el-form-item>
              <template #label>
                <div class="constant-label-wrapper">
                  <span>常量定义</span>
                  <el-tooltip placement="top" effect="dark">
                    <template #content>
                      <div class="placeholder-tooltip">
                        <div class="tooltip-title">常量定义说明</div>
                        <div class="tooltip-item">添加键值对常量，使用 <code>${结果变量名.键名}</code> 引用</div>
                        <div class="tooltip-example">例如：结果变量名=orderConstant，键名=appId<br />SQL中使用 ${orderConstant.appId}
                        </div>
                      </div>
                    </template>
                    <el-icon class="label-tip-icon">
                      <QuestionFilled />
                    </el-icon>
                  </el-tooltip>
                  <el-button v-if="!showConstantInput" type="primary" :icon="Plus" circle size="small"
                    class="add-constant-btn" @click="showConstantInput = true" />
                </div>
              </template>
              <div v-for="(value, key) in parsedConstants" :key="key" class="constant-row">
                <el-input :model-value="key" placeholder="键名" style="width: 40%; margin-right: 8px" disabled />
                <el-input v-model="editingConstantValue" v-if="editingConstantKey === key" placeholder="值"
                  style="width: 50%; margin-right: 8px" @blur="handleConstantBlur"
                  @keyup.enter="confirmConstantEdit(key)" />
                <el-input :model-value="value" v-else placeholder="值" style="width: 50%; margin-right: 8px"
                  @focus="startEditConstant(key, value)" />
                <el-button v-if="editingConstantKey === key" type="success" :icon="Check" circle size="small"
                  @mousedown.prevent="confirmConstantEdit(key)" />
                <el-button v-else type="danger" :icon="Delete" circle size="small" @click="removeConstant(key)" />
              </div>
              <!-- 新增常量输入行 -->
              <div v-if="showConstantInput" class="constant-row" style="margin-top: 8px;">
                <el-input v-model="newConstKey" placeholder="键名" style="width: 35%; margin-right: 8px"
                  ref="newConstKeyInput" />
                <el-input v-model="newConstValue" placeholder="值" style="width: 40%; margin-right: 8px"
                  @keyup.enter="confirmAddConstant" />
                <el-button type="success" :icon="Check" circle size="small" @click="confirmAddConstant" />
                <el-button type="info" :icon="Close" circle size="small" @click="cancelAddConstant"
                  style="margin-left: 4px" />
              </div>
            </el-form-item>
            <el-form-item>
              <el-button type="danger" size="small" @click="deleteNode">删除步骤</el-button>
            </el-form-item>
          </template>

          <!-- LOOP 类型配置 -->
          <template v-else-if="selectedNode.data.stepType === 'LOOP'">
            <el-form-item label="循环来源">
              <el-radio-group v-model="loopConfig.loopSource" @change="updateLoopConfig">
                <el-radio value="CONSTANT">常量分割</el-radio>
                <el-radio value="VARIABLE">变量引用</el-radio>
              </el-radio-group>
            </el-form-item>
            <template v-if="loopConfig.loopSource === 'CONSTANT'">
              <el-form-item label="常量值">
                <el-input v-model="loopConfig.constantValue" placeholder="A,B,C" @change="updateLoopConfig" />
              </el-form-item>
              <el-form-item label="分隔符">
                <el-input v-model="loopConfig.separator" placeholder="," style="width: 100px"
                  @change="updateLoopConfig" />
              </el-form-item>
            </template>
            <template v-else>
              <el-form-item>
                <template #label>
                  <span>变量引用</span>
                  <el-tooltip placement="top" effect="dark">
                    <template #content>
                      <div class="placeholder-tooltip">
                        <div class="tooltip-title">变量引用说明</div>
                        <div class="tooltip-item">引用前序步骤结果列，如 <code>${step1.id}</code></div>
                        <div class="tooltip-item">将循环该列所有值执行循环 SQL</div>
                      </div>
                    </template>
                    <el-icon class="label-tip-icon">
                      <QuestionFilled />
                    </el-icon>
                  </el-tooltip>
                </template>
                <el-input v-model="loopConfig.variableRef" placeholder="${step1.column}" @change="updateLoopConfig" />
              </el-form-item>
            </template>
            <el-form-item label="数据源">
              <el-select v-model="selectedNode.data.datasourceId" placeholder="选择数据源" style="width: 100%"
                @change="updateNode">
                <el-option :value="0" label="本地监控库" />
                <el-option v-for="ds in datasources" :key="ds.id" :label="ds.name" :value="ds.id" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <template #label>
                <span>循环 SQL</span>
                <el-tooltip placement="top" effect="dark">
                  <template #content>
                    <div class="placeholder-tooltip">
                      <div class="tooltip-title">支持的动态占位符：</div>
                      <div class="tooltip-item"><code>${loop.index}</code> 当前索引(从0开始)</div>
                      <div class="tooltip-item"><code>${loop.value}</code> 当前循环值</div>
                      <div class="tooltip-item"><code>${loop.total}</code> 循环总数</div>
                      <div class="tooltip-item"><code>${now}</code> 当前时间</div>
                      <div class="tooltip-item"><code>${today}</code> 今天日期</div>
                      <div class="tooltip-item"><code>${yesterday}</code> 昨天日期</div>
                      <div class="tooltip-item"><code>${lastRunTime}</code> 上次执行时间</div>
                      <div class="tooltip-item"><code>${todayStart}</code> 今天 00:00:00</div>
                      <div class="tooltip-item"><code>${todayEnd}</code> 今天 23:59:59</div>
                      <div class="tooltip-item"><code>${stepName.field}</code> 引用前序步骤结果</div>
                      <div class="tooltip-item"><code>${varName.xxx}</code> 引用常量</div>
                      <div class="tooltip-item"><code>${env.xxx}</code> 环境变量</div>
                      <div class="tooltip-example">示例: WHERE orderID = '${loop.value}'</div>
                    </div>
                  </template>
                  <el-icon class="label-tip-icon">
                    <QuestionFilled />
                  </el-icon>
                </el-tooltip>
              </template>
              <el-input type="textarea" v-model="loopConfig.loopSql" :rows="6"
                placeholder="SELECT * FROM table WHERE id = '${loop.value}'" @change="updateLoopConfig" />
            </el-form-item>
            <el-form-item>
              <template #label>
                <span>测试值</span>
                <el-tooltip placement="top" effect="dark">
                  <template #content>
                    <div class="placeholder-tooltip">
                      <div class="tooltip-title">测试值说明</div>
                      <div class="tooltip-item">常量模式：可留空，自动取第一个常量值</div>
                      <div class="tooltip-item">变量模式：输入测试值或留空取变量第一行</div>
                    </div>
                  </template>
                  <el-icon class="label-tip-icon">
                    <QuestionFilled />
                  </el-icon>
                </el-tooltip>
              </template>
              <el-input v-model="loopTestValue" placeholder="输入测试用的循环值" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" size="small" @click="testLoopStep" :loading="testing">测试执行</el-button>
              <el-button type="danger" size="small" @click="deleteNode">删除步骤</el-button>
            </el-form-item>

            <!-- LOOP 聚合配置 -->
            <div class="divider"></div>
            <h5 style="margin: 8px 0; color: #606266;">聚合配置</h5>

            <el-form-item>
              <template #label>
                <span>启用聚合</span>
                <el-tooltip placement="right" effect="dark">
                  <template #content>
                    <div class="placeholder-tooltip">
                      <div class="tooltip-title">聚合配置说明</div>
                      <div class="tooltip-item">开启后将对循环结果进行分组聚合</div>
                      <div class="tooltip-item">支持 SUM/COUNT/AVG/MAX/MIN</div>
                      <div class="tooltip-item">类似 SQL 的 GROUP BY + 聚合函数</div>
                    </div>
                  </template>
                  <el-icon class="label-tip-icon">
                    <QuestionFilled />
                  </el-icon>
                </el-tooltip>
              </template>
              <el-switch v-model="loopConfig.aggregate.enabled" @change="updateLoopConfig" />
            </el-form-item>

            <template v-if="loopConfig.aggregate.enabled">
              <el-form-item label="分组字段">
                <el-input v-model="aggregateGroupByInput" placeholder="分组字段，多个用逗号分隔 (留空则全部聚合)"
                  @change="updateAggregateGroupBy" />
              </el-form-item>
              <el-form-item label="聚合字段">
                <div style="width: 100%;">
                  <div v-for="(aggField, index) in loopConfig.aggregate.aggregateFields" :key="index"
                    style="display: flex; gap: 8px; margin-bottom: 8px;">
                    <el-input v-model="aggField.field" placeholder="字段名" style="flex: 1;" @change="updateLoopConfig" />
                    <el-select v-model="aggField.method" style="width: 100px;" @change="updateLoopConfig">
                      <el-option value="SUM" label="SUM" />
                      <el-option value="COUNT" label="COUNT" />
                      <el-option value="AVG" label="AVG" />
                      <el-option value="MAX" label="MAX" />
                      <el-option value="MIN" label="MIN" />
                    </el-select>
                    <el-button type="danger" :icon="Delete" circle size="small" @click="removeAggregateField(index)" />
                  </div>
                  <el-button type="primary" size="small" :icon="Plus" @click="addAggregateField">添加聚合字段</el-button>
                </div>
              </el-form-item>
            </template>

            <!-- LOOP批处理配置 -->
            <div class="divider"></div>
            <h5 style="margin: 8px 0; color: #606266;">批处理配置</h5>

            <el-form-item>
              <template #label>
                <span>启用批处理</span>
                <el-tooltip placement="right" effect="dark">
                  <template #content>
                    <div class="placeholder-tooltip">
                      <div class="tooltip-title">LOOP批处理说明</div>
                      <div class="tooltip-item">开启后循环内SQL使用Spring Batch执行</div>
                      <div class="tooltip-item">适合每次迭代数据量大(>10万)的场景</div>
                      <div class="tooltip-item" style="color: #67c23a;">✓ 支持: SELECT、WHERE、ORDER BY</div>
                      <div class="tooltip-item" style="color: #f56c6c;">✗ 不支持: GROUP BY、DISTINCT、窗口函数</div>
                    </div>
                  </template>
                  <el-icon class="label-tip-icon">
                    <QuestionFilled />
                  </el-icon>
                </el-tooltip>
              </template>
              <el-switch v-model="stepBatchEnabled" @change="updateNode" />
            </el-form-item>

            <template v-if="stepBatchEnabled">
              <el-form-item label="分区字段">
                <el-input v-model="selectedNode.data.idColumn" placeholder="留空自动识别主键" @change="updateNode" />
              </el-form-item>
              <el-form-item label="分区数">
                <el-input-number v-model="selectedNode.data.partitionCount" :min="1" :max="50" :step="1"
                  @change="updateNode" />
              </el-form-item>
              <el-form-item label="Chunk大小">
                <el-input-number v-model="selectedNode.data.chunkSize" :min="100" :max="10000" :step="100"
                  @change="updateNode" />
              </el-form-item>
              <el-form-item label="缓存策略">
                <el-select v-model="selectedNode.data.cacheStrategy" placeholder="选择缓存策略" style="width: 100%"
                  @change="updateNode">
                  <el-option value="FILE" label="本地文件 (10万-1000万)" />
                  <el-option value="REDIS" label="Redis (<10万)" />
                  <el-option value="ES" label="Elasticsearch (>1000万)" />
                  <el-option value="TEMP_TABLE" label="临时表 (复杂聚合)" />
                </el-select>
              </el-form-item>
            </template>
          </template>

          <!-- 测试结果 -->
          <div v-if="testResult && Array.isArray(testResult) && testResult.length > 0" class="test-result">
            <h5>测试结果 ({{ testResult.length }} 行)</h5>
            <el-table :data="testResult.slice(0, 5)" size="small" max-height="200">
              <el-table-column v-for="col in testResultColumns" :key="col" :prop="col" :label="col" min-width="120" />
            </el-table>
          </div>
        </el-form>
      </div>

      <div class="config-panel placeholder" v-else-if="workflow.id">
        <el-icon class="placeholder-icon">
          <InfoFilled />
        </el-icon>
        <p>点击节点编辑配置</p>
      </div>
    </div>

    <!-- 执行历史对话框 -->
    <el-dialog v-model="showHistory" title="执行历史" width="800px">
      <el-table :data="executions" size="small">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="startTime" label="开始时间" width="180" />
        <el-table-column prop="endTime" label="结束时间" width="180" />
        <el-table-column prop="errorMessage" label="错误信息" show-overflow-tooltip />
      </el-table>
    </el-dialog>

    <!-- 新建工作流对话框 - 增强版：支持选择执行模式  -->
    <el-dialog v-model="showCreate" title="创建工作流" width="500px">
      <el-form label-position="top">
        <el-form-item label="工作流名称" required>
          <el-input v-model="newWorkflow.name" placeholder="输入名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input type="textarea" v-model="newWorkflow.description" />
        </el-form-item>

        <!-- 执行模式选择：普通模式适合小数据量，批处理模式适合大数据量 -->
        <el-form-item>
          <template #label>
            <span>执行模式</span>
            <el-tooltip placement="right" effect="dark">
              <template #content>
                <div class="placeholder-tooltip">
                  <div class="tooltip-title">执行模式说明</div>
                  <div class="tooltip-item">普通模式：顺序执行，适合&lt;10万条数据</div>
                  <div class="tooltip-item">批处理模式：分区并行，适合&gt;10万条数据</div>
                </div>
              </template>
              <el-icon class="label-tip-icon">
                <QuestionFilled />
              </el-icon>
            </el-tooltip>
          </template>
          <el-radio-group v-model="newWorkflow.batchMode">
            <el-radio :value="0">普通模式</el-radio>
            <el-radio :value="1">批处理模式</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- 批处理模式额外配置：缓存策略、分区数、Chunk大小 -->
        <template v-if="newWorkflow.batchMode === 1">
          <el-divider content-position="left">批处理配置</el-divider>
          <el-form-item label="缓存策略">
            <el-select v-model="newWorkflow.cacheStrategy" placeholder="选择缓存策略" style="width: 100%">
              <el-option value="FILE" label="本地文件 (10万-1000万)" />
              <el-option value="REDIS" label="Redis (<10万)" />
              <el-option value="ES" label="Elasticsearch (>1000万)" />
              <el-option value="TEMP_TABLE" label="临时表 (复杂聚合)" />
            </el-select>
          </el-form-item>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="分区数">
                <el-input-number v-model="newWorkflow.partitionCount" :min="1" :max="50" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="Chunk大小">
                <el-input-number v-model="newWorkflow.chunkSize" :min="100" :max="10000" :step="100"
                  style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" @click="doCreateWorkflow">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { VueFlow, Handle, Position, useVueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { Plus, Check, Delete, VideoPlay, Coin, Link, Share, InfoFilled, Document, Refresh, QuestionFilled, Loading, Close } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../api/request'
import { toJsonClean } from '../api/utils'

import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'

// 状态
const workflows = ref([])
const selectedWorkflowId = ref(null)
const workflow = ref({})
const nodes = ref([])
const edges = ref([])
const selectedNode = ref(null)
const datasources = ref([])
const executions = ref([])
const testResult = ref(null)
const vueFlowRef = ref(null)

// VueFlow 实例
const { project } = useVueFlow()

// UI 状态
const saving = ref(false)
const executing = ref(false)
const testing = ref(false)
const showHistory = ref(false)
const showCreate = ref(false)
// 新建工作流表单状态
const newWorkflow = ref({
  name: '',
  description: ''
})

// 拖拽类型
const dragType = ref('')

// CONSTANT 配置状态
const newConstKey = ref('')
const newConstValue = ref('')
const showConstantInput = ref(false)  // 控制新增常量输入行的显示
const editingConstantKey = ref(null)  // 追踪当前正在编辑的常量键名
const editingConstantValue = ref('')  // 临时存储编辑中的常量值

// LOOP 配置状态
const loopConfig = ref({
  loopSource: 'CONSTANT',
  constantValue: '',
  separator: ',',
  variableRef: '',
  loopSql: '',
  aggregate: {
    enabled: false,
    groupByFields: [],
    aggregateFields: []
  }
})
const loopTestValue = ref('')  // 用于测试的循环值
const aggregateGroupByInput = ref('')  // 分组字段输入框

// 计算属性：解析常量配置
const parsedConstants = computed(() => {
  if (!selectedNode.value || selectedNode.value.data.stepType !== 'CONSTANT') return {}
  try {
    const config = JSON.parse(selectedNode.value.data.config || '{}')
    return config.constants || {}
  } catch {
    return {}
  }
})

// 计算属性
const workflowActive = computed({
  get: () => workflow.value.isActive === 1,
  set: (val) => { workflow.value.isActive = val ? 1 : 0 }
})

// 步骤级批处理开关
const stepBatchEnabled = computed({
  get: () => selectedNode.value?.data?.batchEnabled === 1,
  set: (val) => {
    if (selectedNode.value?.data) {
      selectedNode.value.data.batchEnabled = val ? 1 : 0
      // 设置默认值
      if (val) {
        if (!selectedNode.value.data.cacheStrategy) {
          selectedNode.value.data.cacheStrategy = 'FILE'
        }
        if (!selectedNode.value.data.partitionCount) {
          selectedNode.value.data.partitionCount = 10
        }
        if (!selectedNode.value.data.chunkSize) {
          selectedNode.value.data.chunkSize = 1000
        }
      }
    }
  }
})

const testResultColumns = computed(() => {
  if (!testResult.value || testResult.value.length === 0) return []
  const firstRow = testResult.value[0]
  if (!firstRow || typeof firstRow !== 'object') return []
  return Object.keys(firstRow)
})

// 加载工作流列表
const loadWorkflows = async () => {
  try {
    const res = await request.get('/workflows')
    workflows.value = res
  } catch (e) {
    ElMessage.error('加载工作流列表失败')
  }
}

// 加载数据源
const loadDatasources = async () => {
  try {
    const res = await request.get('/datasource')
    datasources.value = res
  } catch (e) {
    console.error('加载数据源失败', e)
  }
}

// 加载工作流详情
const loadWorkflow = async () => {
  if (!selectedWorkflowId.value) {
    workflow.value = {}
    nodes.value = []
    edges.value = []
    selectedNode.value = null
    return
  }

  try {
    const res = await request.get(`/workflows/${selectedWorkflowId.value}`)
    workflow.value = res

    // 如果结果表名为空，前端设置默认值
    if (!workflow.value.outputTable) {
      workflow.value.outputTable = `monitor_wf_result_${workflow.value.id}`
    }

    // 转换步骤为节点
    nodes.value = (res.steps || []).map((step, idx) => ({
      id: `step-${step.id || idx}`,
      type: step.stepType === 'CONSTANT' ? 'constant' : step.stepType === 'LOOP' ? 'loop' : 'sql',
      position: { x: step.positionX || 200, y: step.positionY || 100 + idx * 150 },
      data: {
        label: step.name,
        stepType: step.stepType,
        datasourceId: step.datasourceId,
        sqlScript: step.sqlScript,
        resultVariable: step.resultVariable,
        stepOrder: step.stepOrder,
        originalId: step.id,
        config: step.config || '{}',
        // 批处理配置字段
        batchEnabled: step.batchEnabled || 0,
        idColumn: step.idColumn,
        partitionCount: step.partitionCount,
        chunkSize: step.chunkSize,
        cacheStrategy: step.cacheStrategy
      }
    }))

    // 根据步骤顺序生成边
    edges.value = []
    const sortedNodes = [...nodes.value].sort((a, b) => a.data.stepOrder - b.data.stepOrder)
    for (let i = 0; i < sortedNodes.length - 1; i++) {
      edges.value.push({
        id: `e${i}`,
        source: sortedNodes[i].id,
        target: sortedNodes[i + 1].id,
        type: 'smoothstep',
        animated: true
      })
    }

    selectedNode.value = null
  } catch (e) {
    ElMessage.error('加载工作流详情失败')
  }
}

// 保存工作流
const saveWorkflow = async () => {
  if (!workflow.value.name) {
    ElMessage.warning('请输入工作流名称')
    return
  }

  saving.value = true
  try {
    // 使用拓扑排序根据边的连接关系确定节点顺序
    const sortedNodes = topologicalSort(nodes.value, edges.value)

    // 转换节点为步骤
    const steps = sortedNodes.map((node, idx) => ({
      id: node.data.originalId,
      name: node.data.label,
      stepType: node.data.stepType || 'SQL',
      stepOrder: idx + 1,
      datasourceId: node.data.datasourceId,
      sqlScript: node.data.sqlScript,
      resultVariable: node.data.resultVariable,
      config: node.data.config || '{}',
      positionX: Math.round(node.position.x),
      positionY: Math.round(node.position.y),
      // 批处理配置字段
      batchEnabled: node.data.batchEnabled || 0,
      idColumn: node.data.idColumn || null,
      partitionCount: node.data.partitionCount || null,
      chunkSize: node.data.chunkSize || null,
      cacheStrategy: node.data.cacheStrategy || null
    }))

    const data = { ...workflow.value, steps }

    if (workflow.value.id) {
      await request.put(`/workflows/${workflow.value.id}`, data)
    } else {
      const res = await request.post('/workflows', data)
      workflow.value.id = res.id
      selectedWorkflowId.value = res.id
    }

    ElMessage.success('保存成功')
    loadWorkflows()
  } catch (e) {
    // 处理批处理校验错误
    if (e.response && e.response.status === 400 && e.response.data && e.response.data.batchErrors) {
      const errors = e.response.data.batchErrors
      ElMessageBox.alert(
        `<div style="max-height: 300px; overflow-y: auto;">
          <p style="margin-bottom: 10px; color: #606266;">${e.response.data.message || '以下步骤的SQL不支持批处理'}</p>
          <ul style="padding-left: 20px; color: #f56c6c;">
            ${errors.map(err => `<li>${err}</li>`).join('')}
          </ul>
          <p style="margin-top: 10px; color: #909399; font-size: 12px;">
            提示：GROUP BY、DISTINCT、窗口函数不支持分区批处理，请关闭批处理或修改SQL
          </p>
        </div>`,
        '批处理校验失败',
        {
          dangerouslyUseHTMLString: true,
          confirmButtonText: '确定',
          type: 'warning'
        }
      )
    } else {
      ElMessage.error('保存失败')
    }
  } finally {
    saving.value = false
  }
}
// 批处理状态轮询相关状态
const batchPolling = ref(false)  // 是否正在轮询
const batchProgress = ref(null)  // 批处理进度信息
let pollTimer = null             // 轮询定时器

/**
 * 执行工作流
 * - 普通模式：同步执行，直接返回结果
 * - 批处理模式：异步执行，启动后轮询状态
 */
const executeWorkflow = async () => {
  executing.value = true
  batchProgress.value = null
  try {
    const res = await request.post(`/workflows/${workflow.value.id}/execute`)
    const execution = res

    // 判断是否为批处理模式（状态以 BATCH_ 开头）
    if (execution.status && execution.status.startsWith('BATCH_')) {
      // 解析返回的批处理执行信息
      let batchInfo = {}
      try {
        batchInfo = JSON.parse(execution.stepResults || '{}')
      } catch (e) {
        // 忽略解析错误
      }

      ElMessage.info('批处理任务已启动，正在执行中...')

      // 启动状态轮询
      startBatchPolling(batchInfo.batchExecutionId, execution.id)
    } else {
      // 普通模式：直接显示结果
      ElMessage.success(`执行${execution.status === 'SUCCESS' ? '成功' : '失败'}`)
      executing.value = false
    }
  } catch (e) {
    ElMessage.error('执行失败: ' + (e.response?.data?.message || e.message))
    executing.value = false
  }
}

/**
 * 启动批处理状态轮询
 * 每3秒查询一次，直到任务完成或失败
 */
const startBatchPolling = (batchExecutionId, workflowExecutionId) => {
  if (!batchExecutionId) {
    ElMessage.warning('无法获取批处理执行ID')
    executing.value = false
    return
  }

  batchPolling.value = true
  batchProgress.value = { status: '执行中...', readCount: 0, writeCount: 0 }

  pollTimer = setInterval(async () => {
    try {
      const res = await request.get(`/batch/status/${batchExecutionId}`)
      const status = res

      // 更新进度信息
      batchProgress.value = {
        status: status.status || '执行中',
        readCount: status.readCount || 0,
        writeCount: status.writeCount || 0,
        createTime: status.createTime,
        endTime: status.endTime
      }

      // 检查是否完成
      if (status.status === 'COMPLETED') {
        stopBatchPolling()
        ElMessage.success('批处理执行完成！')
        batchProgress.value.status = '完成'
      } else if (status.status === 'FAILED' || status.status === 'STOPPED') {
        stopBatchPolling()
        ElMessage.error(`批处理执行${status.status === 'FAILED' ? '失败' : '已停止'}`)
        batchProgress.value.status = status.status === 'FAILED' ? '失败' : '已停止'
      }
    } catch (e) {
      console.error('轮询批处理状态失败:', e)
      // 出错时不停止轮询，继续尝试
    }
  }, 3000) // 每3秒轮询一次
}

/**
 * 停止批处理状态轮询
 */
const stopBatchPolling = () => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
  batchPolling.value = false
  executing.value = false
}

// 删除工作流
const deleteWorkflow = async () => {
  try {
    await ElMessageBox.confirm('确定要删除这个工作流吗？', '警告', { type: 'warning' })
    await request.delete(`/workflows/${workflow.value.id}`)
    ElMessage.success('删除成功')
    selectedWorkflowId.value = null
    workflow.value = {}
    nodes.value = []
    edges.value = []
    loadWorkflows()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

// 创建工作流：打开对话框并重置表单状态
const createWorkflow = () => {
  // 重置表单为默认值
  newWorkflow.value = {
    name: '',
    description: '',
    batchMode: 0,           // 默认普通模式
    cacheStrategy: 'FILE',
    partitionCount: 10,
    chunkSize: 1000
  }
  showCreate.value = true
}

/**
 * 执行创建工作流操作
 * 根据用户选择的执行模式传递不同的配置参数
 */
const doCreateWorkflow = async () => {
  if (!newWorkflow.value.name) {
    ElMessage.warning('请输入名称')
    return
  }

  try {
    // 构建请求数据：基础字段 + 批处理配置
    const requestData = {
      name: newWorkflow.value.name,
      description: newWorkflow.value.description,
      isActive: 1,
      timeoutSeconds: 3600,
      batchMode: newWorkflow.value.batchMode  // 执行模式
    }

    // 如果是批处理模式，附加批处理配置参数
    if (newWorkflow.value.batchMode === 1) {
      requestData.cacheStrategy = newWorkflow.value.cacheStrategy
      requestData.partitionCount = newWorkflow.value.partitionCount
      requestData.chunkSize = newWorkflow.value.chunkSize
    }

    const res = await request.post('/workflows', requestData)
    ElMessage.success('创建成功')
    showCreate.value = false
    await loadWorkflows()
    selectedWorkflowId.value = res.id
    loadWorkflow()
  } catch (e) {
    ElMessage.error('创建失败')
  }
}

// 拖拽开始
const onDragStart = (type) => {
  dragType.value = type
}

// 放置节点
const onDrop = (event) => {
  if (!dragType.value || !workflow.value.id) return

  // 获取 VueFlow 容器的边界
  const flowContainer = vueFlowRef.value?.$el || event.currentTarget
  const rect = flowContainer.getBoundingClientRect()

  // 计算相对于容器的位置并转换为画布坐标
  const position = project({
    x: event.clientX - rect.left,
    y: event.clientY - rect.top
  })

  // 根据步骤类型确定节点类型
  let nodeType = 'sql'
  if (dragType.value === 'CONSTANT') nodeType = 'constant'
  else if (dragType.value === 'LOOP') nodeType = 'loop'

  const newNode = {
    id: `step-new-${Date.now()}`,
    type: nodeType,
    position,
    data: {
      label: `步骤 ${nodes.value.length + 1}`,
      stepType: dragType.value,
      datasourceId: 0,
      sqlScript: '',
      resultVariable: `step${nodes.value.length + 1}`,
      stepOrder: nodes.value.length + 1,
      config: dragType.value === 'CONSTANT' ? '{"constants": {}}' :
        dragType.value === 'LOOP' ? '{"loopSource": "CONSTANT", "constantValue": "", "separator": ",", "loopSql": ""}' : '{}'
    }
  }

  nodes.value.push(newNode)
  dragType.value = ''
}

// 节点点击
const onNodeClick = ({ node }) => {
  selectedNode.value = node
  testResult.value = null
}

// 连接节点
const onConnect = (params) => {
  edges.value.push({
    id: `e-${params.source}-${params.target}`,
    source: params.source,
    target: params.target,
    type: 'smoothstep',
    animated: true
  })
}

// 更新节点
const updateNode = () => {
  if (!selectedNode.value) return

  // 找到 nodes 数组中对应的节点并同步 data
  const index = nodes.value.findIndex(n => n.id === selectedNode.value.id)
  if (index !== -1) {
    // 将 selectedNode 的 data 同步到 nodes 数组中的对应节点
    nodes.value[index].data = { ...selectedNode.value.data }
  }

  // 触发响应式更新
  nodes.value = [...nodes.value]
}

// 删除节点
const deleteNode = () => {
  if (!selectedNode.value) return

  nodes.value = nodes.value.filter(n => n.id !== selectedNode.value.id)
  edges.value = edges.value.filter(e => e.source !== selectedNode.value.id && e.target !== selectedNode.value.id)
  selectedNode.value = null
}

/**
 * 测试执行步骤 SQL
 * 后端响应格式: { data: [...], rowCount: number, warning?: string, message?: string }
 */
const testStep = async () => {
  if (!selectedNode.value?.data.sqlScript) {
    ElMessage.warning('请输入 SQL 脚本')
    return
  }

  testing.value = true
  testResult.value = null

  try {
    // 计算当前节点的 stepOrder
    const nodeIndex = nodes.value.findIndex(n => n.id === selectedNode.value.id)
    const stepOrder = nodeIndex >= 0 ? nodeIndex + 1 : 0

    const res = await request.post('/workflows/test-step', {
      workflowId: workflow.value.id,
      stepOrder: stepOrder,
      name: selectedNode.value.data.label,
      stepType: 'SQL',
      datasourceId: selectedNode.value.data.datasourceId,
      sqlScript: selectedNode.value.data.sqlScript,
      resultVariable: selectedNode.value.data.resultVariable
    })

    // 解析响应: { data, rowCount, warning, message }
    const { data, rowCount, warning } = res

    // 确保 data 是数组
    testResult.value = Array.isArray(data) ? data : []

    // 检查是否有错误返回
    if (testResult.value.length === 1 && testResult.value[0]?.error) {
      ElMessage.error(testResult.value[0].error)
      testResult.value = [] // 清空错误数据
    } else {
      ElMessage.success(`查询成功，返回 ${rowCount || testResult.value.length} 行`)

      // 数据量超阈值检测
      if (warning === 'DATA_VOLUME_HIGH') {
        handleDataVolumeWarning(rowCount)
      }
    }
  } catch (e) {
    // 处理后端返回的错误响应
    const errorData = e.response?.data
    if (errorData?.data?.[0]?.error) {
      ElMessage.error(errorData.data[0].error)
    } else {
      ElMessage.error('测试执行失败: ' + (errorData?.message || e.message))
    }
  } finally {
    testing.value = false
  }
}

/**
 * 处理数据量超阈值警告
 * 弹出对话框提示用户切换到批处理模式
 * 
 * @param {number} rowCount - 查询返回的数据行数
 */
const handleDataVolumeWarning = (rowCount) => {
  ElMessageBox.confirm(
    `当前查询返回 ${rowCount.toLocaleString()} 条数据，建议切换到批处理模式以获得更好的性能和稳定性。

批处理模式优势：
• 分区并行处理，提升执行速度
• 支持断点续传，避免数据丢失
• 中间结果缓存，减少内存压力

是否立即切换到批处理模式？`,
    '数据量较大提示',
    {
      confirmButtonText: '切换到批处理模式',
      cancelButtonText: '继续使用普通模式',
      type: 'warning',
      dangerouslyUseHTMLString: false
    }
  ).then(() => {
    // 用户确认切换：自动设置批处理模式和默认配置
    workflow.value.batchMode = 1
    if (!workflow.value.cacheStrategy) workflow.value.cacheStrategy = 'FILE'
    if (!workflow.value.partitionCount) workflow.value.partitionCount = 10
    if (!workflow.value.chunkSize) workflow.value.chunkSize = 1000
    ElMessage.success('已切换到批处理模式，请保存工作流以生效')
  }).catch(() => {
    // 用户取消：继续使用普通模式
    ElMessage.info('将继续使用普通模式')
  })
}

// 测试循环步骤（支持常量和变量模式）
const testLoopStep = async () => {
  if (!loopConfig.value.loopSql) {
    ElMessage.warning('请输入循环 SQL')
    return
  }

  let testValue = loopTestValue.value.trim()
  let totalCount = 1

  // 如果没有手动输入测试值，尝试自动获取
  if (!testValue) {
    if (loopConfig.value.loopSource === 'CONSTANT') {
      // 常量模式：从配置中取第一个值
      if (!loopConfig.value.constantValue) {
        ElMessage.warning('请输入常量值或手动指定测试值')
        return
      }
      const separator = loopConfig.value.separator || ','
      const values = loopConfig.value.constantValue.split(separator)
      testValue = values[0]?.trim() || ''
      totalCount = values.length
    } else {
      // 变量模式：需要先执行前序步骤获取变量值
      if (!loopConfig.value.variableRef) {
        ElMessage.warning('请输入变量引用或手动指定测试值')
        return
      }
      // 解析变量引用格式 ${stepName.field}
      const match = loopConfig.value.variableRef.match(/\$\{(\w+)\.(\w+)\}/)
      if (!match) {
        ElMessage.warning('变量引用格式错误，请手动输入测试值')
        return
      }

      // 提示用户需要手动输入
      ElMessage.warning('变量模式请在测试值输入框中手动输入一个值用于测试')
      return
    }
  }

  if (!testValue) {
    ElMessage.warning('无法获取循环值，请手动输入测试值')
    return
  }

  testing.value = true
  testResult.value = null

  try {
    // 替换 loop.* 占位符
    let testSql = loopConfig.value.loopSql
      .replace(/\$\{loop\.value\}/g, testValue)
      .replace(/\$\{loop\.index\}/g, '0')
      .replace(/\$\{loop\.total\}/g, String(totalCount))

    // 计算当前节点的 stepOrder，以便后端执行前序步骤
    const nodeIndex = nodes.value.findIndex(n => n.id === selectedNode.value.id)
    const stepOrder = nodeIndex >= 0 ? nodeIndex + 1 : 0

    const res = await request.post('/workflows/test-step', {
      workflowId: workflow.value.id,
      stepOrder: stepOrder,
      name: selectedNode.value.data.label + ' (循环测试)',
      stepType: 'SQL',
      datasourceId: selectedNode.value.data.datasourceId,
      sqlScript: testSql,
      resultVariable: 'loop_test'
    })

    // 解析响应: { data, rowCount, warning, message }
    const { data, rowCount } = res
    testResult.value = Array.isArray(data) ? data : []

    if (testResult.value.length === 1 && testResult.value[0]?.error) {
      ElMessage.error(testResult.value[0].error)
      testResult.value = []
    } else {
      ElMessage.success(`循环测试成功 (value=${testValue})，返回 ${rowCount || testResult.value.length} 行`)
    }
  } catch (e) {
    const errorData = e.response?.data
    if (errorData?.data?.[0]?.error) {
      ElMessage.error(errorData.data[0].error)
    } else {
      ElMessage.error('测试执行失败: ' + (errorData?.message || e.message))
    }
  } finally {
    testing.value = false
  }
}

// 获取状态类型
const getStatusType = (status) => {
  const types = { SUCCESS: 'success', FAILED: 'danger', RUNNING: 'warning', PENDING: 'info' }
  return types[status] || 'info'
}

// 获取常量数量
const getConstantCount = (configStr) => {
  try {
    if (!configStr) return '0 个'
    const config = JSON.parse(configStr)
    const count = config.constants ? Object.keys(config.constants).length : 0
    return `${count} 个`
  } catch {
    return '0 个'
  }
}

// 获取循环模式
const getLoopMode = (configStr) => {
  try {
    if (!configStr) return '未配置'
    const config = JSON.parse(configStr)
    return config.loopSource === 'CONSTANT' ? '常量分割' : '变量引用'
  } catch {
    return '未配置'
  }
}

// 拓扑排序：根据边的连接关系确定节点的正确执行顺序
const topologicalSort = (nodeList, edgeList) => {
  if (nodeList.length === 0) return []
  if (edgeList.length === 0) {
    // 没有边，按 Y 坐标排序
    return [...nodeList].sort((a, b) => a.position.y - b.position.y)
  }

  // 构建邻接表和入度表
  const inDegree = {}
  const adjList = {}
  nodeList.forEach(n => {
    inDegree[n.id] = 0
    adjList[n.id] = []
  })

  edgeList.forEach(e => {
    if (adjList[e.source]) {
      adjList[e.source].push(e.target)
    }
    if (inDegree[e.target] !== undefined) {
      inDegree[e.target]++
    }
  })

  // Kahn 算法
  const queue = []
  const result = []

  // 找出所有入度为 0 的节点
  Object.keys(inDegree).forEach(id => {
    if (inDegree[id] === 0) queue.push(id)
  })

  // 按 Y 坐标排序入度为 0 的节点
  queue.sort((a, b) => {
    const nodeA = nodeList.find(n => n.id === a)
    const nodeB = nodeList.find(n => n.id === b)
    return (nodeA?.position.y || 0) - (nodeB?.position.y || 0)
  })

  while (queue.length > 0) {
    const curr = queue.shift()
    const node = nodeList.find(n => n.id === curr)
    if (node) result.push(node)

    const neighbors = adjList[curr] || []
    neighbors.forEach(neighbor => {
      inDegree[neighbor]--
      if (inDegree[neighbor] === 0) {
        queue.push(neighbor)
        // 保持 Y 坐标顺序
        queue.sort((a, b) => {
          const nodeA = nodeList.find(n => n.id === a)
          const nodeB = nodeList.find(n => n.id === b)
          return (nodeA?.position.y || 0) - (nodeB?.position.y || 0)
        })
      }
    })
  }

  // 如果有未排序的节点（可能存在环或孤立节点），追加到末尾
  nodeList.forEach(n => {
    if (!result.includes(n)) result.push(n)
  })

  return result
}

// 确认添加常量
const confirmAddConstant = () => {
  if (!newConstKey.value.trim()) {
    ElMessage.warning('请输入键名')
    return
  }
  const constants = { ...parsedConstants.value, [newConstKey.value.trim()]: newConstValue.value }
  selectedNode.value.data.config = toJsonClean({ constants })
  updateNode()
  newConstKey.value = ''
  newConstValue.value = ''
  showConstantInput.value = false  // 隐藏输入行
}

// 更新常量值
const updateConstant = (key, value) => {
  const constants = { ...parsedConstants.value, [key]: value }
  selectedNode.value.data.config = toJsonClean({ constants })
  updateNode()
}

// 删除常量
const removeConstant = (key) => {
  const constants = { ...parsedConstants.value }
  delete constants[key]
  selectedNode.value.data.config = toJsonClean({ constants })
  updateNode()
}

// 取消添加常量
const cancelAddConstant = () => {
  showConstantInput.value = false
  newConstKey.value = ''
  newConstValue.value = ''
}

// 开始编辑常量
const startEditConstant = (key, value) => {
  editingConstantKey.value = key
  editingConstantValue.value = value
}

// 处理常量值输入框失焦
const handleConstantBlur = () => {
  // 使用 setTimeout 确保点击确认按钮时不会因为 blur 事件提前清除状态
  setTimeout(() => {
    editingConstantKey.value = null
    editingConstantValue.value = ''
  }, 150)
}

// 确认常量编辑
const confirmConstantEdit = (key) => {
  // 调用 updateConstant 保存修改
  updateConstant(key, editingConstantValue.value)
  editingConstantKey.value = null
  editingConstantValue.value = ''
}

// 更新循环配置
const updateLoopConfig = () => {
  if (!selectedNode.value || selectedNode.value.data.stepType !== 'LOOP') return
  selectedNode.value.data.config = toJsonClean(loopConfig.value)
  // 同步 loopSql 到 sqlScript 用于后端执行
  selectedNode.value.data.sqlScript = loopConfig.value.loopSql
  updateNode()
}

// 更新聚合分组字段
const updateAggregateGroupBy = () => {
  loopConfig.value.aggregate.groupByFields = aggregateGroupByInput.value
    .split(',')
    .map(s => s.trim())
    .filter(s => s.length > 0)
  updateLoopConfig()
}

// 添加聚合字段
const addAggregateField = () => {
  loopConfig.value.aggregate.aggregateFields.push({
    field: '',
    method: 'SUM',
    alias: ''
  })
  updateLoopConfig()
}

// 删除聚合字段
const removeAggregateField = (index) => {
  loopConfig.value.aggregate.aggregateFields.splice(index, 1)
  updateLoopConfig()
}

// 监听选中节点变化，同步 loopConfig
watch(selectedNode, (node) => {
  if (node && node.data.stepType === 'LOOP') {
    try {
      const config = JSON.parse(node.data.config || '{}')
      loopConfig.value = {
        loopSource: config.loopSource || 'CONSTANT',
        constantValue: config.constantValue || '',
        separator: config.separator || ',',
        variableRef: config.variableRef || '',
        loopSql: config.loopSql || '',
        aggregate: config.aggregate || { enabled: false, groupByFields: [], aggregateFields: [] }
      }
      // 同步分组字段到输入框
      aggregateGroupByInput.value = (loopConfig.value.aggregate.groupByFields || []).join(',')
    } catch {
      loopConfig.value = { loopSource: 'CONSTANT', constantValue: '', separator: ',', variableRef: '', loopSql: '', aggregate: { enabled: false, groupByFields: [], aggregateFields: [] } }
      aggregateGroupByInput.value = ''
    }
  }
  // 切换节点时重置常量输入状态
  showConstantInput.value = false
  newConstKey.value = ''
  newConstValue.value = ''
}, { immediate: true })

// 初始化
onMounted(() => {
  loadWorkflows()
  loadDatasources()
})
</script>

<style scoped>
.workflow-designer {
  height: calc(100vh - 120px);
  display: flex;
  flex-direction: column;
  background: var(--el-bg-color);
  border-radius: 12px;
  overflow: hidden;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: var(--el-bg-color-overlay);
  border-bottom: 1px solid var(--el-border-color);
}

.toolbar-left,
.toolbar-right {
  display: flex;
  gap: 10px;
  align-items: center;
}

.main-content {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.step-panel {
  width: 220px;
  padding: 16px;
  background: var(--el-bg-color-overlay);
  border-right: 1px solid var(--el-border-color);
  overflow-y: auto;
}

.step-panel h4 {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.step-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  margin-bottom: 8px;
  background: var(--el-fill-color);
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  cursor: grab;
  transition: all 0.2s;
}

.step-item:hover {
  background: var(--el-fill-color-light);
  border-color: var(--el-color-primary);
}

.step-item[disabled] {
  opacity: 0.5;
  cursor: not-allowed;
}

.divider {
  height: 1px;
  background: var(--el-border-color);
  margin: 16px 0;
}

.canvas-container {
  flex: 1;
  position: relative;
  background: var(--el-fill-color-lighter);
}

.empty-state {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  color: var(--el-text-color-placeholder);
}

.empty-icon {
  font-size: 64px;
  margin-bottom: 16px;
}

.config-panel {
  width: 280px;
  padding: 16px;
  background: var(--el-bg-color-overlay);
  border-left: 1px solid var(--el-border-color);
  overflow-y: auto;
}

.config-panel h4 {
  margin: 0 0 16px;
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.config-panel.placeholder {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  color: var(--el-text-color-placeholder);
}

.placeholder-icon {
  font-size: 48px;
  margin-bottom: 12px;
}

.test-result {
  margin-top: 16px;
  padding: 12px;
  background: var(--el-fill-color);
  border-radius: 8px;
}

.test-result h5 {
  margin: 0 0 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* 常量定义标签样式 */
.constant-label-wrapper {
  display: flex;
  align-items: center;
  gap: 4px;
  width: 100%;
}

/* 让表单项的 label 占满整行宽度 */
.config-panel :deep(.el-form-item__label) {
  width: 100% !important;
  justify-content: flex-start;
}

.constant-label-wrapper .add-constant-btn {
  margin-left: auto;
  width: 24px !important;
  height: 24px !important;
  min-width: 14px;
  font-size: 12px;
}

/* Vue Flow 节点样式 */
.sql-node {
  min-width: 180px;
  background: var(--el-bg-color);
  border: 2px solid var(--el-border-color);
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.sql-node.selected {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 3px rgba(var(--el-color-primary-rgb), 0.2);
}

.node-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: linear-gradient(135deg, var(--el-color-primary-light-3), var(--el-color-primary));
  color: white;
  font-size: 13px;
  font-weight: 500;
}

.node-body {
  padding: 8px 12px;
}

.node-info {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.form-tip {
  font-size: 11px;
  color: var(--el-text-color-placeholder);
  margin-top: 4px;
  line-height: 1.4;
}

.constant-row {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}

/* 标签提示图标 */
.label-tip-icon {
  margin-left: 4px;
  color: var(--el-color-primary);
  cursor: help;
  vertical-align: middle;
}

/* tooltip 内容样式 */
.placeholder-tooltip {
  line-height: 1.8;
}

.placeholder-tooltip .tooltip-title {
  font-weight: bold;
  margin-bottom: 8px;
  color: #00d4ff;
}

.placeholder-tooltip .tooltip-item {
  margin-bottom: 4px;
}

.placeholder-tooltip .tooltip-item code {
  color: #00d4ff;
  margin-right: 8px;
  font-family: monospace;
}

.placeholder-tooltip .tooltip-example {
  margin-top: 12px;
  padding-top: 8px;
  border-top: 1px solid rgba(255, 255, 255, 0.2);
  color: #aaa;
  font-size: 12px;
}

/* 常量节点样式 */
.constant-node {
  min-width: 180px;
  background: var(--el-bg-color);
  border: 2px solid #67c23a;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.constant-node.selected {
  border-color: #529b2e;
  box-shadow: 0 0 0 3px rgba(103, 194, 58, 0.2);
}

.constant-node .node-header {
  background: linear-gradient(135deg, #95d475, #67c23a);
}

/* 循环节点样式 */
.loop-node {
  min-width: 180px;
  background: var(--el-bg-color);
  border: 2px solid #e6a23c;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.loop-node.selected {
  border-color: #b88230;
  box-shadow: 0 0 0 3px rgba(230, 162, 60, 0.2);
}

.loop-node .node-header {
  background: linear-gradient(135deg, #eebe77, #e6a23c);
}

/* 批处理进度样式 */
.batch-progress {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 4px 12px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  margin-right: 12px;
}

.batch-progress .el-tag {
  display: flex;
  align-items: center;
  gap: 4px;
}

.progress-stats {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>

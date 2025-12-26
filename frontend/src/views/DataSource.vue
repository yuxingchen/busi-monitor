<template>
  <div class="page-container">
    <!-- Page Header -->
    <div class="page-header">
      <div class="header-info">
        <h3 class="header-title">
          <el-icon class="title-icon">
            <Connection />
          </el-icon>
          数据源配置
        </h3>
        <p class="header-desc">管理外部数据库连接，支持 MySQL、PostgreSQL、Oracle 等多种数据库</p>
      </div>
      <el-button type="primary" class="add-btn" @click="handleAdd">
        <el-icon>
          <Plus />
        </el-icon>
        新增数据源
      </el-button>
    </div>

    <!-- Data Table Card -->
    <div class="table-card">
      <el-table :data="tableData" class="custom-table"
        :header-cell-style="{ background: 'rgba(0, 40, 80, 0.5)', color: '#00f2fe', fontWeight: '600' }"
        :row-style="{ background: 'transparent' }">
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="name" label="数据源名称" min-width="150">
          <template #default="{ row }">
            <div class="ds-name">
              <span class="name-badge">{{ row.name?.charAt(0)?.toUpperCase() }}</span>
              <span>{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="url" label="JDBC URL" min-width="280">
          <template #default="{ row }">
            <el-tooltip :content="row.url" placement="top">
              <span class="url-text">{{ row.url }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="driverClassName" label="驱动类型" width="180">
          <template #default="{ row }">
            <el-tag size="small" effect="dark" class="driver-tag">
              {{ getDriverName(row.driverClassName) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" align="center">
          <template #default="{ row }">
            <div class="action-btns">
              <el-button size="small" class="btn-test" :loading="testingId === row.id" @click="handleTest(row)">
                <el-icon v-if="testingId !== row.id">
                  <Connection />
                </el-icon>
                测试
              </el-button>
              <el-button size="small" class="btn-edit" @click="handleEdit(row)">
                <el-icon>
                  <Edit />
                </el-icon>
                编辑
              </el-button>
              <el-button size="small" class="btn-delete" @click="handleDelete(row)">
                <el-icon>
                  <Delete />
                </el-icon>
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <!-- Empty State -->
      <div v-if="tableData.length === 0" class="empty-state">
        <el-icon class="empty-icon">
          <Box />
        </el-icon>
        <p>暂无数据源配置</p>
        <span>点击上方按钮添加您的第一个数据源</span>
      </div>
    </div>

    <!-- Dialog -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑数据源' : '新增数据源'" width="550px" class="custom-dialog"
      :close-on-click-modal="false">
      <el-form :model="form" label-width="100px" class="custom-form">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="输入数据源名称" />
        </el-form-item>
        <el-form-item label="JDBC URL" required>
          <el-input v-model="form.url" placeholder="jdbc:mysql://host:port/database" />
        </el-form-item>
        <el-row :gutter="15">
          <el-col :span="12">
            <el-form-item label="用户名" required>
              <el-input v-model="form.username" placeholder="数据库用户名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="密码" required>
              <el-input v-model="form.password" type="password" show-password placeholder="数据库密码" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="驱动类">
          <el-select v-model="form.driverClassName" style="width: 100%">
            <el-option label="MySQL" value="com.mysql.cj.jdbc.Driver" />
            <el-option label="PostgreSQL" value="org.postgresql.Driver" />
            <el-option label="Oracle" value="oracle.jdbc.OracleDriver" />
            <el-option label="SQL Server" value="com.microsoft.sqlserver.jdbc.SQLServerDriver" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSave">保存配置</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive } from 'vue'
import request from '../api/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Connection, Plus, Edit, Delete, Box } from '@element-plus/icons-vue'
import { encryptPassword } from '../utils/crypto'

const tableData = ref([])
const dialogVisible = ref(false)
const testingId = ref(null)
const form = reactive({
  id: null,
  name: '',
  url: '',
  username: '',
  password: '',
  driverClassName: 'com.mysql.cj.jdbc.Driver'
})

const getDriverName = (driver) => {
  const map = {
    'com.mysql.cj.jdbc.Driver': 'MySQL',
    'org.postgresql.Driver': 'PostgreSQL',
    'oracle.jdbc.OracleDriver': 'Oracle',
    'com.microsoft.sqlserver.jdbc.SQLServerDriver': 'SQL Server'
  }
  return map[driver] || driver
}

const loadData = async () => {
  try {
    const res = await request.get('/datasource')
    tableData.value = res
  } catch (e) { console.error(e) }
}

const handleAdd = () => {
  Object.assign(form, {
    id: null,
    name: '',
    url: '',
    username: '',
    password: '',
    driverClassName: 'com.mysql.cj.jdbc.Driver'
  })
  dialogVisible.value = true
}

const handleEdit = (row) => {
  Object.assign(form, row)
  dialogVisible.value = true
}

const handleDelete = (row) => {
  ElMessageBox.confirm('确认删除该数据源？删除后不可恢复。', '删除确认', {
    confirmButtonText: '确认删除',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await request.delete(`/datasource/${row.id}`)
    ElMessage.success('删除成功')
    loadData()
  }).catch(() => { })
}

const handleTest = async (row) => {
  testingId.value = row.id
  try {
    const res = await request.post(`/datasource/${row.id}/test`)
    if (res.success) {
      ElMessage.success(`连接成功！数据库: ${res.dbProductName} ${res.dbVersion}`)
    } else {
      ElMessage.error(res.message || '连接失败')
    }
  } catch (e) {
    ElMessage.error('测试连接失败')
  } finally {
    testingId.value = null
  }
}

const handleSave = async () => {
  if (!form.name || !form.url) {
    ElMessage.warning('请填写必要信息')
    return
  }
  try {
    // 加密密码后发送
    const payload = { ...form }
    if (payload.password) {
      payload.password = encryptPassword(payload.password)
    }
    if (form.id) {
      await request.put('/datasource', payload)
    } else {
      await request.post('/datasource', payload)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.page-container {
  min-height: 100%;
}

/* Page Header */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 25px;
}

.header-info {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 20px;
  font-weight: 600;
  color: var(--theme-text);
  margin: 0;
}

.title-icon {
  color: var(--theme-accent);
  font-size: 24px;
}

.header-desc {
  font-size: 13px;
  color: var(--theme-text-secondary);
  margin: 0;
}

.add-btn {
  background: linear-gradient(135deg, var(--theme-accent), var(--el-color-primary)) !important;
  border: none !important;
  padding: 10px 20px !important;
  font-weight: 500 !important;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.2) !important;
  transition: all 0.3s !important;
}

.add-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.3) !important;
}

/* Table Card */
.table-card {
  background: var(--theme-card-bg);
  border: 1px solid var(--theme-border);
  border-radius: 12px;
  padding: 20px;
  backdrop-filter: blur(10px);
  transition: background 0.3s ease, border-color 0.3s ease;
}

.custom-table {
  --el-table-bg-color: transparent !important;
  --el-table-tr-bg-color: transparent !important;
  --el-table-row-hover-bg-color: var(--el-fill-color-light) !important;
  --el-table-border-color: var(--el-border-color-lighter) !important;
  --el-table-text-color: var(--el-text-color-regular) !important;
}

:deep(.el-table) {
  background: transparent !important;
}

:deep(.el-table td.el-table__cell) {
  border-bottom: 1px solid var(--el-border-color-lighter) !important;
  padding: 14px 0;
}

:deep(.el-table th.el-table__cell) {
  border-bottom: 1px solid var(--el-border-color-light) !important;
  background: var(--el-fill-color) !important;
  color: var(--theme-accent) !important;
}

:deep(.el-table__inner-wrapper::before) {
  display: none;
}

.ds-name {
  display: flex;
  align-items: center;
  gap: 10px;
}

.name-badge {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--theme-accent), var(--el-color-primary));
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
}

.url-text {
  display: block;
  max-width: 250px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: 'Roboto Mono', monospace;
  font-size: 12px;
  color: var(--theme-text-secondary);
}

.driver-tag {
  background: var(--el-fill-color-light) !important;
  border: 1px solid var(--el-border-color) !important;
  color: var(--theme-accent) !important;
}

.action-btns {
  display: flex;
  gap: 8px;
  justify-content: center;
}

.btn-edit {
  background: var(--el-fill-color-light) !important;
  border: 1px solid var(--el-border-color) !important;
  color: var(--theme-accent) !important;
}

.btn-edit:hover {
  background: var(--el-fill-color) !important;
}

.btn-delete {
  background: rgba(255, 71, 87, 0.1) !important;
  border: 1px solid rgba(255, 71, 87, 0.3) !important;
  color: #ff4757 !important;
}

.btn-delete:hover {
  background: rgba(255, 71, 87, 0.2) !important;
}

.btn-test {
  background: rgba(0, 242, 254, 0.1) !important;
  border: 1px solid rgba(0, 242, 254, 0.3) !important;
  color: #00f2fe !important;
}

.btn-test:hover {
  background: rgba(0, 242, 254, 0.2) !important;
}

/* Empty State */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 60px 20px;
  color: var(--theme-text-secondary);
}

.empty-icon {
  font-size: 48px;
  color: var(--el-text-color-placeholder);
  margin-bottom: 15px;
}

.empty-state p {
  font-size: 16px;
  margin: 0 0 5px 0;
}

.empty-state span {
  font-size: 13px;
  color: var(--el-text-color-placeholder);
}

/* Dialog Overrides */
:deep(.el-dialog) {
  background: var(--el-bg-color-overlay) !important;
  border: 1px solid var(--theme-border) !important;
  border-radius: 12px !important;
}

:deep(.el-dialog__header) {
  border-bottom: 1px solid var(--el-border-color-light);
  padding: 20px;
}

:deep(.el-dialog__title) {
  color: var(--theme-text) !important;
  font-weight: 600;
}

:deep(.el-dialog__headerbtn .el-dialog__close) {
  color: var(--theme-text-secondary);
}

:deep(.el-dialog__body) {
  padding: 25px 20px;
}

:deep(.el-form-item__label) {
  color: var(--theme-text-secondary) !important;
}

:deep(.el-input__wrapper) {
  background: var(--el-fill-color-blank) !important;
  border: 1px solid var(--el-border-color) !important;
  box-shadow: none !important;
}

:deep(.el-input__wrapper:hover) {
  border-color: var(--el-border-color-light) !important;
}

:deep(.el-input__wrapper.is-focus) {
  border-color: var(--theme-accent) !important;
}

:deep(.el-input__inner) {
  color: var(--theme-text) !important;
}

:deep(.el-input__inner::placeholder) {
  color: var(--el-text-color-placeholder) !important;
}

:deep(.el-select .el-input__wrapper) {
  background: var(--el-fill-color-blank) !important;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>

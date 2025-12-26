<template>
    <div class="user-management">
        <!-- 搜索区域 -->
        <div class="search-bar">
            <el-input v-model="searchUsername" placeholder="搜索用户名" clearable @keyup.enter="loadUsers"
                style="width: 240px">
                <template #prefix>
                    <el-icon>
                        <Search />
                    </el-icon>
                </template>
            </el-input>
            <el-button type="primary" @click="loadUsers">搜索</el-button>
            <el-button type="success" @click="openAddDialog">
                <el-icon>
                    <Plus />
                </el-icon> 新增用户
            </el-button>
        </div>

        <!-- 用户列表 -->
        <el-table :data="users" v-loading="loading" stripe border>
            <el-table-column prop="id" label="ID" />
            <el-table-column prop="username" label="用户名" />
            <el-table-column prop="role" label="角色">
                <template #default="{ row }">
                    <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'info'">
                        {{ row.role === 'ADMIN' ? '管理员' : '普通用户' }}
                    </el-tag>
                </template>
            </el-table-column>
            <el-table-column prop="enabled" label="状态">
                <template #default="{ row }">
                    <el-tag :type="row.enabled === 1 ? 'success' : 'warning'">
                        {{ row.enabled === 1 ? '启用' : '禁用' }}
                    </el-tag>
                </template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" width="180" />
            <el-table-column prop="updateTime" label="更新时间" width="180" />
            <el-table-column label="操作" min-width="380" fixed="right">
                <template #default="{ row }">
                    <div class="action-btns">
                        <el-button size="small" class="btn-edit" @click="openEditDialog(row)">
                            <el-icon>
                                <Edit />
                            </el-icon> 编辑
                        </el-button>
                        <el-button size="small" class="btn-toggle" @click="toggleUserStatus(row)">
                            <el-icon>
                                <Switch />
                            </el-icon>
                            {{ row.enabled === 1 ? '禁用' : '启用' }}
                        </el-button>
                        <el-button size="small" class="btn-reset" @click="openResetPasswordDialog(row)">
                            <el-icon>
                                <Key />
                            </el-icon> 重置
                        </el-button>
                        <el-button size="small" class="btn-delete" @click="deleteUser(row)"
                            :disabled="row.username === 'admin'">
                            <el-icon>
                                <Delete />
                            </el-icon> 删除
                        </el-button>
                    </div>
                </template>
            </el-table-column>
        </el-table>

        <!-- 分页 -->
        <div class="pagination">
            <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize" :page-sizes="[10, 20, 50]"
                :total="total" layout="total, sizes, prev, pager, next" @size-change="loadUsers"
                @current-change="loadUsers" />
        </div>

        <!-- 新增/编辑对话框 -->
        <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑用户' : '新增用户'" width="450px">
            <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
                <el-form-item label="用户名" prop="username">
                    <el-input v-model="form.username" placeholder="请输入用户名" :disabled="isEdit" />
                </el-form-item>
                <el-form-item v-if="!isEdit" label="密码" prop="password">
                    <el-input v-model="form.password" type="password" placeholder="请输入密码(至少6位)" show-password />
                </el-form-item>
                <el-form-item label="角色" prop="role">
                    <el-select v-model="form.role" placeholder="请选择角色" style="width: 100%">
                        <el-option label="管理员" value="ADMIN" />
                        <el-option label="普通用户" value="USER" />
                    </el-select>
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="dialogVisible = false">取消</el-button>
                <el-button type="primary" @click="submitForm" :loading="submitting">确定</el-button>
            </template>
        </el-dialog>

        <!-- 重置密码对话框 -->
        <el-dialog v-model="resetPwdDialogVisible" title="重置密码" width="400px">
            <el-form ref="resetPwdFormRef" :model="resetPwdForm" :rules="resetPwdRules" label-width="80px">
                <el-form-item label="新密码" prop="password">
                    <el-input v-model="resetPwdForm.password" type="password" placeholder="请输入新密码(至少6位)"
                        show-password />
                </el-form-item>
                <el-form-item label="确认密码" prop="confirmPassword">
                    <el-input v-model="resetPwdForm.confirmPassword" type="password" placeholder="请再次输入新密码"
                        show-password />
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="resetPwdDialogVisible = false">取消</el-button>
                <el-button type="primary" @click="submitResetPassword" :loading="submitting">确定</el-button>
            </template>
        </el-dialog>
    </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Edit, Delete, Switch, Key } from '@element-plus/icons-vue'
import request from '../api/request'
import { encryptPassword } from '../utils/crypto'

// 搜索相关
const searchUsername = ref('')
const loading = ref(false)
const users = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

// 加载用户列表
const loadUsers = async () => {
    loading.value = true
    try {
        const res = await request.get('/user/list', {
            params: {
                username: searchUsername.value,
                page: currentPage.value,
                size: pageSize.value
            }
        })
        if (res.success) {
            users.value = res.data
            total.value = res.total
        } else {
            ElMessage.error(res.message || '获取用户列表失败')
        }
    } catch (error) {
        ElMessage.error('请求失败')
    } finally {
        loading.value = false
    }
}

// 新增/编辑对话框
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)
const form = reactive({
    id: null,
    username: '',
    password: '',
    role: 'USER'
})
const submitting = ref(false)

const rules = {
    username: [
        { required: true, message: '请输入用户名', trigger: 'blur' },
        { min: 3, max: 20, message: '用户名长度在3-20个字符之间', trigger: 'blur' }
    ],
    password: [
        { required: true, message: '请输入密码', trigger: 'blur' },
        { min: 6, message: '密码至少6位', trigger: 'blur' }
    ],
    role: [
        { required: true, message: '请选择角色', trigger: 'change' }
    ]
}

const openAddDialog = () => {
    isEdit.value = false
    form.id = null
    form.username = ''
    form.password = ''
    form.role = 'USER'
    dialogVisible.value = true
}

const openEditDialog = (row) => {
    isEdit.value = true
    form.id = row.id
    form.username = row.username
    form.password = ''
    form.role = row.role
    dialogVisible.value = true
}

const submitForm = async () => {
    if (!formRef.value) return

    try {
        // 编辑时密码不是必填
        if (isEdit.value) {
            await formRef.value.validateField(['username', 'role'])
        } else {
            await formRef.value.validate()
        }
    } catch {
        return
    }

    submitting.value = true
    try {
        const url = isEdit.value ? `/user/${form.id}` : '/user'
        const method = isEdit.value ? 'put' : 'post'
        const payload = { ...form }
        // 加密密码后发送
        if (payload.password) {
            payload.password = encryptPassword(payload.password)
        } else if (isEdit.value) {
            delete payload.password
        }

        const res = await request[method](url, payload)
        if (res.success) {
            ElMessage.success(res.message || (isEdit.value ? '更新成功' : '创建成功'))
            dialogVisible.value = false
            loadUsers()
        } else {
            ElMessage.error(res.message || '操作失败')
        }
    } catch (error) {
        ElMessage.error('请求失败')
    } finally {
        submitting.value = false
    }
}

// 切换用户状态
const toggleUserStatus = async (row) => {
    const action = row.enabled === 1 ? '禁用' : '启用'
    try {
        await ElMessageBox.confirm(`确定要${action}用户 "${row.username}" 吗？`, '提示', {
            type: 'warning'
        })

        const res = await request.post(`/user/${row.id}/toggle-status`)
        if (res.success) {
            ElMessage.success(res.message)
            loadUsers()
        } else {
            ElMessage.error(res.message || '操作失败')
        }
    } catch {
        // 取消操作
    }
}

// 删除用户
const deleteUser = async (row) => {
    try {
        await ElMessageBox.confirm(`确定要删除用户 "${row.username}" 吗？此操作不可恢复！`, '警告', {
            type: 'error'
        })

        const res = await request.delete(`/user/${row.id}`)
        if (res.success) {
            ElMessage.success(res.message)
            loadUsers()
        } else {
            ElMessage.error(res.message || '删除失败')
        }
    } catch {
        // 取消操作
    }
}

// 重置密码
const resetPwdDialogVisible = ref(false)
const resetPwdFormRef = ref(null)
const resetPwdForm = reactive({
    userId: null,
    username: '',
    password: '',
    confirmPassword: ''
})

const resetPwdRules = {
    password: [
        { required: true, message: '请输入新密码', trigger: 'blur' },
        { min: 6, message: '密码至少6位', trigger: 'blur' }
    ],
    confirmPassword: [
        { required: true, message: '请确认密码', trigger: 'blur' },
        {
            validator: (rule, value, callback) => {
                if (value !== resetPwdForm.password) {
                    callback(new Error('两次输入的密码不一致'))
                } else {
                    callback()
                }
            },
            trigger: 'blur'
        }
    ]
}

const openResetPasswordDialog = (row) => {
    resetPwdForm.userId = row.id
    resetPwdForm.username = row.username
    resetPwdForm.password = ''
    resetPwdForm.confirmPassword = ''
    resetPwdDialogVisible.value = true
}

const submitResetPassword = async () => {
    if (!resetPwdFormRef.value) return

    try {
        await resetPwdFormRef.value.validate()
    } catch {
        return
    }

    submitting.value = true
    try {
        // 加密密码后发送
        const res = await request.post(`/user/${resetPwdForm.userId}/reset-password`, {
            password: encryptPassword(resetPwdForm.password)
        })
        if (res.success) {
            ElMessage.success(res.message || '密码重置成功')
            resetPwdDialogVisible.value = false
        } else {
            ElMessage.error(res.message || '密码重置失败')
        }
    } catch (error) {
        ElMessage.error('请求失败')
    } finally {
        submitting.value = false
    }
}

onMounted(() => {
    loadUsers()
})
</script>

<style scoped>
.user-management {
    padding: 20px;
}

.search-bar {
    display: flex;
    gap: 12px;
    margin-bottom: 20px;
}

.pagination {
    margin-top: 20px;
    display: flex;
    justify-content: flex-end;
}

/* 操作按钮样式 */
.action-btns {
    display: flex;
    gap: 6px;
    justify-content: center;
    flex-wrap: wrap;
}

.btn-edit {
    background: var(--el-fill-color-light) !important;
    border: 1px solid var(--el-border-color) !important;
    color: var(--theme-accent) !important;
}

.btn-edit:hover {
    background: var(--el-fill-color) !important;
}

.btn-toggle {
    background: rgba(255, 165, 0, 0.1) !important;
    border: 1px solid rgba(255, 165, 0, 0.3) !important;
    color: #ffa500 !important;
}

.btn-toggle:hover {
    background: rgba(255, 165, 0, 0.2) !important;
}

.btn-reset {
    background: rgba(108, 117, 125, 0.1) !important;
    border: 1px solid rgba(108, 117, 125, 0.3) !important;
    color: #6c757d !important;
}

.btn-reset:hover {
    background: rgba(108, 117, 125, 0.2) !important;
}

.btn-delete {
    background: rgba(255, 71, 87, 0.1) !important;
    border: 1px solid rgba(255, 71, 87, 0.3) !important;
    color: #ff4757 !important;
}

.btn-delete:hover {
    background: rgba(255, 71, 87, 0.2) !important;
}

.btn-delete:disabled {
    opacity: 0.5;
    cursor: not-allowed;
}
</style>

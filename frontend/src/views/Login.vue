<template>
    <div class="login-container">
        <div class="login-box">
            <div class="login-header">
                <div class="logo">
                    <el-icon class="logo-icon">
                        <Monitor />
                    </el-icon>
                    <span class="logo-text">业务监控系统</span>
                </div>
                <p class="login-subtitle">企业级监控告警平台</p>
            </div>

            <el-form ref="formRef" :model="form" :rules="rules" class="login-form" @submit.prevent="handleLogin">
                <el-form-item prop="username">
                    <el-input v-model="form.username" placeholder="用户名" size="large">
                        <template #prefix>
                            <el-icon>
                                <User />
                            </el-icon>
                        </template>
                    </el-input>
                </el-form-item>

                <el-form-item prop="password">
                    <el-input v-model="form.password" type="password" placeholder="密码" size="large" show-password
                        @keyup.enter="handleLogin">
                        <template #prefix>
                            <el-icon>
                                <Lock />
                            </el-icon>
                        </template>
                    </el-input>
                </el-form-item>

                <el-form-item>
                    <el-button type="primary" class="login-btn" :loading="loading" @click="handleLogin">
                        {{ loading ? '登录中...' : '登 录' }}
                    </el-button>
                </el-form-item>
            </el-form>

            <div class="login-footer">
                <span>© 2025 业务监控系统</span>
            </div>
        </div>

        <!-- 背景装饰 -->
        <div class="bg-decoration">
            <div class="circle circle-1"></div>
            <div class="circle circle-2"></div>
            <div class="circle circle-3"></div>
        </div>
    </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Monitor, User, Lock } from '@element-plus/icons-vue'
import request from '../api/request'
import { encryptPassword } from '../utils/crypto'

const router = useRouter()
const formRef = ref(null)
const loading = ref(false)

const form = reactive({
    username: '',
    password: ''
})

const rules = {
    username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
    password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

// 检查是否已登录
onMounted(() => {
    const token = localStorage.getItem('token')
    if (token) {
        router.push('/dashboard')
    }
})

const handleLogin = async () => {
    if (!formRef.value) return

    await formRef.value.validate(async (valid) => {
        if (!valid) return

        loading.value = true
        try {
            // 加密密码后发送
            const res = await request.post('/auth/login', {
                username: form.username,
                password: encryptPassword(form.password)
            })
            if (res.success) {
                // 保存token和用户信息
                localStorage.setItem('token', res.token)
                localStorage.setItem('username', res.username)
                localStorage.setItem('role', res.role)

                ElMessage.success('登录成功')
                router.push('/dashboard')
            } else {
                ElMessage.error(res.message || '登录失败')
            }
        } catch (e) {
            ElMessage.error('登录失败，请检查网络连接')
        } finally {
            loading.value = false
        }
    })
}
</script>

<style scoped>
.login-container {
    min-height: 100vh;
    display: flex;
    align-items: center;
    justify-content: center;
    background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
    position: relative;
    overflow: hidden;
}

.login-box {
    width: 400px;
    padding: 40px;
    background: rgba(255, 255, 255, 0.05);
    backdrop-filter: blur(20px);
    border-radius: 20px;
    border: 1px solid rgba(255, 255, 255, 0.1);
    box-shadow: 0 25px 45px rgba(0, 0, 0, 0.2);
    z-index: 10;
}

.login-header {
    text-align: center;
    margin-bottom: 40px;
}

.logo {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 12px;
    margin-bottom: 10px;
}

.logo-icon {
    font-size: 36px;
    color: #00d4ff;
    filter: drop-shadow(0 0 10px rgba(0, 212, 255, 0.5));
}

.logo-text {
    font-size: 24px;
    font-weight: 700;
    background: linear-gradient(90deg, #00d4ff, #7c4dff);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    letter-spacing: 2px;
}

.login-subtitle {
    color: rgba(255, 255, 255, 0.5);
    font-size: 14px;
    margin: 0;
}

.login-form {
    margin-bottom: 20px;
}

:deep(.el-input__wrapper) {
    background: rgba(255, 255, 255, 0.05) !important;
    border: 1px solid rgba(255, 255, 255, 0.1) !important;
    box-shadow: none !important;
    border-radius: 10px !important;
    height: 50px;
}

:deep(.el-input__wrapper:hover) {
    border-color: rgba(0, 212, 255, 0.3) !important;
}

:deep(.el-input__wrapper.is-focus) {
    border-color: #00d4ff !important;
    box-shadow: 0 0 10px rgba(0, 212, 255, 0.2) !important;
}

:deep(.el-input__inner) {
    color: #fff !important;
    font-size: 15px;
}

:deep(.el-input__inner::placeholder) {
    color: rgba(255, 255, 255, 0.4) !important;
}

:deep(.el-input__prefix) {
    color: rgba(255, 255, 255, 0.5);
}

.login-btn {
    width: 100%;
    height: 50px;
    font-size: 16px;
    font-weight: 600;
    border-radius: 10px;
    background: linear-gradient(135deg, #00d4ff, #7c4dff) !important;
    border: none !important;
    box-shadow: 0 10px 30px rgba(0, 212, 255, 0.3);
    transition: all 0.3s;
}

.login-btn:hover {
    transform: translateY(-2px);
    box-shadow: 0 15px 35px rgba(0, 212, 255, 0.4);
}

.login-footer {
    text-align: center;
    color: rgba(255, 255, 255, 0.3);
    font-size: 12px;
}

/* 背景装饰 */
.bg-decoration {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    overflow: hidden;
    pointer-events: none;
}

.circle {
    position: absolute;
    border-radius: 50%;
    background: linear-gradient(135deg, rgba(0, 212, 255, 0.1), rgba(124, 77, 255, 0.1));
}

.circle-1 {
    width: 600px;
    height: 600px;
    top: -200px;
    right: -200px;
    animation: float 8s ease-in-out infinite;
}

.circle-2 {
    width: 400px;
    height: 400px;
    bottom: -100px;
    left: -100px;
    animation: float 10s ease-in-out infinite reverse;
}

.circle-3 {
    width: 200px;
    height: 200px;
    top: 50%;
    left: 20%;
    animation: float 6s ease-in-out infinite;
}

@keyframes float {

    0%,
    100% {
        transform: translateY(0) rotate(0deg);
    }

    50% {
        transform: translateY(-30px) rotate(5deg);
    }
}

/* 表单项间距 */
:deep(.el-form-item) {
    margin-bottom: 25px;
}

:deep(.el-form-item__error) {
    color: #ff6b6b;
}
</style>

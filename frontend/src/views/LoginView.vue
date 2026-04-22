<template>
  <div class="login-page">
    <div class="login-card">
      <!-- Logo 区域 -->
      <div class="login-logo">
        <div class="logo-icon-large">
          <el-icon :size="32"><Document /></el-icon>
        </div>
        <h1 class="logo-title">{{ $t('app.name') }}</h1>
        <p class="logo-subtitle">{{ $t('app.subtitle') }}</p>
      </div>

      <!-- 登录表单 -->
      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        size="large"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            :placeholder="$t('login.usernamePlaceholder')"
            :prefix-icon="User"
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            :placeholder="$t('login.passwordPlaceholder')"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>

        <el-form-item>
          <div class="login-options">
            <el-checkbox v-model="rememberMe">{{ $t('login.rememberMe') }}</el-checkbox>
          </div>
        </el-form-item>

        <el-form-item>
          <button
            type="button"
            class="login-btn"
            :disabled="loading"
            @click="handleLogin"
          >
            <span v-if="!loading">{{ $t('login.button') }}</span>
            <span v-else>{{ $t('login.logging') }}</span>
          </button>
        </el-form-item>
      </el-form>

      <!-- 底部提示 -->
      <div class="login-footer">
        <p>{{ $t('app.name') }} &copy; {{ currentYear }}</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Document, User, Lock } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const { t } = useI18n()

const loginFormRef = ref(null)
const loading = ref(false)
const rememberMe = ref(false)

const currentYear = computed(() => new Date().getFullYear())

const loginForm = reactive({
  username: '',
  password: ''
})

const loginRules = computed(() => ({
  username: [
    { required: true, message: t('login.usernameRequired'), trigger: 'blur' }
  ],
  password: [
    { required: true, message: t('login.passwordRequired'), trigger: 'blur' },
    { min: 6, message: t('login.passwordMin'), trigger: 'blur' }
  ]
}))

async function handleLogin() {
  if (!loginFormRef.value) return

  try {
    await loginFormRef.value.validate()
  } catch {
    return
  }

  loading.value = true
  try {
    await authStore.login({
      username: loginForm.username,
      password: loginForm.password
    }, rememberMe.value)

    ElMessage.success(t('login.loginSuccess'))

    // 跳转到之前的页面或首页
    const redirect = route.query.redirect || '/'
    router.push(redirect)
  } catch (error) {
    // 错误消息已在拦截器中处理
    ElMessage.error(t('login.loginFailed'))
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.login-options :deep(.el-checkbox__label) {
  color: var(--text-secondary);
}

.login-footer {
  text-align: center;
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid var(--border-light);
}

.login-footer p {
  font-size: 12px;
  color: var(--text-placeholder);
}

/* 覆盖 Element Plus 表单样式 */
:deep(.el-form-item) {
  margin-bottom: 22px;
}

:deep(.el-input__wrapper) {
  border-radius: 8px;
  padding: 4px 12px;
}

:deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--accent) inset;
}

:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--primary) inset;
}
</style>

<template>
  <div class="user-settings">
    <div class="page-card">
      <h2 class="page-title">{{ $t('settings.title') }}</h2>

      <el-tabs v-model="activeTab">
        <!-- 个人信息 -->
        <el-tab-pane :label="$t('settings.profile')" name="profile">
          <div class="settings-section" v-loading="profileLoading">
            <el-descriptions :column="2" border>
              <el-descriptions-item :label="$t('settings.username')">
                {{ userProfile.username || '-' }}
              </el-descriptions-item>
              <el-descriptions-item :label="$t('settings.role')">
                <el-tag size="small" type="info">{{ userProfile.role || '-' }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item :label="$t('settings.tenant')">
                {{ userProfile.tenantName || authStore.tenantName || '-' }}
              </el-descriptions-item>
              <el-descriptions-item :label="$t('settings.email')" v-if="userProfile.email">
                {{ userProfile.email }}
              </el-descriptions-item>
            </el-descriptions>
          </div>
        </el-tab-pane>

        <!-- 修改密码 -->
        <el-tab-pane :label="$t('settings.changePassword')" name="password">
          <div class="settings-section">
            <el-form
              ref="passwordFormRef"
              :model="passwordForm"
              :rules="passwordRules"
              label-width="140px"
              style="max-width: 480px"
            >
              <el-form-item :label="$t('settings.oldPassword')" prop="oldPassword">
                <el-input
                  v-model="passwordForm.oldPassword"
                  type="password"
                  show-password
                  :placeholder="$t('settings.oldPasswordPlaceholder')"
                />
              </el-form-item>
              <el-form-item :label="$t('settings.newPassword')" prop="newPassword">
                <el-input
                  v-model="passwordForm.newPassword"
                  type="password"
                  show-password
                  :placeholder="$t('settings.newPasswordPlaceholder')"
                />
              </el-form-item>
              <el-form-item :label="$t('settings.confirmPassword')" prop="confirmPassword">
                <el-input
                  v-model="passwordForm.confirmPassword"
                  type="password"
                  show-password
                  :placeholder="$t('settings.confirmPasswordPlaceholder')"
                />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="handleChangePassword" :loading="passwordLoading">
                  {{ $t('settings.submitPassword') }}
                </el-button>
              </el-form-item>
            </el-form>
          </div>
        </el-tab-pane>

        <!-- 偏好设置 -->
        <el-tab-pane :label="$t('settings.preferences')" name="preferences">
          <div class="settings-section">
            <el-form label-width="140px" style="max-width: 480px">
              <el-form-item :label="$t('settings.language')">
                <el-select
                  :model-value="locale"
                  @change="handleLocaleChange"
                  style="width: 200px"
                >
                  <el-option label="中文" value="zh-CN" />
                  <el-option label="English" value="en-US" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$t('settings.darkMode')">
                <el-switch
                  :model-value="isDark"
                  @change="handleDarkModeChange"
                  :active-text="$t('settings.on')"
                  :inactive-text="$t('settings.off')"
                />
              </el-form-item>
            </el-form>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getUserProfile, updatePassword } from '@/api/index'
import { useAuthStore } from '@/stores/auth'

const { t, locale } = useI18n()
const authStore = useAuthStore()

// ==================== 标签页 ====================
const activeTab = ref('profile')

// ==================== 个人信息 ====================
const profileLoading = ref(false)
const userProfile = ref({})

async function loadProfile() {
  profileLoading.value = true
  try {
    const data = await getUserProfile()
    userProfile.value = data || {}
  } catch (e) {
    console.error('获取用户信息失败', e)
    // 使用 store 中的缓存数据
    userProfile.value = authStore.user || {}
  } finally {
    profileLoading.value = false
  }
}

// ==================== 修改密码 ====================
const passwordFormRef = ref(null)
const passwordLoading = ref(false)

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const passwordRules = computed(() => ({
  oldPassword: [
    { required: true, message: t('settings.oldPasswordRequired'), trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: t('settings.newPasswordRequired'), trigger: 'blur' },
    { min: 6, message: t('settings.newPasswordMin'), trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: t('settings.confirmPasswordRequired'), trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== passwordForm.newPassword) {
          callback(new Error(t('settings.passwordMismatch')))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}))

async function handleChangePassword() {
  if (!passwordFormRef.value) return
  try {
    await passwordFormRef.value.validate()
  } catch {
    return
  }

  passwordLoading.value = true
  try {
    await updatePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    })
    ElMessage.success(t('settings.passwordChanged'))
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
    if (passwordFormRef.value) {
      passwordFormRef.value.resetFields()
    }
  } catch (e) {
    console.error('修改密码失败', e)
  } finally {
    passwordLoading.value = false
  }
}

// ==================== 偏好设置 ====================
const isDark = computed(() => document.documentElement.classList.contains('dark'))

function handleLocaleChange(newLocale) {
  locale.value = newLocale
  localStorage.setItem('locale', newLocale)
  ElMessage.success(t('settings.languageChanged'))
}

function handleDarkModeChange(value) {
  document.documentElement.classList.toggle('dark', value)
  localStorage.setItem('darkMode', String(value))
}

// ==================== 页面初始化 ====================
onMounted(() => {
  loadProfile()
})
</script>

<style scoped>
.user-settings {
  max-width: 900px;
  margin: 0 auto;
}

.settings-section {
  padding: 24px 0;
}
</style>

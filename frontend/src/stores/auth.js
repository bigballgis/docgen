import { defineStore } from 'pinia'
import { ref, computed, onMounted } from 'vue'
import { login as loginApi, getProfile, getTenants } from '@/api/index'
import router from '@/router'

export const useAuthStore = defineStore('auth', () => {
  // ==================== State ====================
  const token = ref(sessionStorage.getItem('token') || '')
  const user = ref(JSON.parse(sessionStorage.getItem('user') || 'null'))
  const tenantId = ref(sessionStorage.getItem('tenantId') || 'default')
  const tenantName = ref(sessionStorage.getItem('tenantName') || 'Default')
  const tenants = ref([]) // 用户可访问的租户列表

  // ==================== Getters ====================
  const isLoggedIn = computed(() => !!token.value)
  const username = computed(() => user.value?.username || user.value?.name || 'User')
  const isAdmin = computed(() => {
    const role = user.value?.role || user.value?.authorities?.[0] || ''
    return role === 'admin' || role === 'ADMIN' || role === 'ROLE_ADMIN'
  })

  // ==================== Actions ====================

  /**
   * 登录
   * @param {Object} credentials - { username, password }
   */
  async function login(credentials, rememberMe = false) {
    try {
      const data = await loginApi(credentials)
      const tokenValue = data?.token || data?.access_token || data
      token.value = tokenValue
      sessionStorage.setItem('token', tokenValue)
      if (rememberMe) {
        localStorage.setItem('token', tokenValue)
      }

      // 登录成功后获取用户信息
      await fetchProfile()

      // 获取租户列表
      await fetchTenants()

      return data
    } catch (error) {
      throw error
    }
  }

  /**
   * 获取用户信息
   */
  async function fetchProfile() {
    try {
      const data = await getProfile()
      user.value = data
      const userJson = JSON.stringify(data)
      sessionStorage.setItem('user', userJson)
      if (localStorage.getItem('token')) {
        localStorage.setItem('user', userJson)
      }
    } catch (error) {
      // 获取用户信息失败，可能是 token 过期
      // 获取用户信息失败，可能是 token 过期
      logout()
    }
  }

  /**
   * 获取租户列表
   */
  async function fetchTenants() {
    try {
      const data = await getTenants()
      const list = Array.isArray(data) ? data : (data?.list || data?.content || [])
      tenants.value = list
      // 如果当前 tenantId 不在列表中，切换到第一个
      if (list.length > 0 && !list.find(t => t.id === tenantId.value || t.tenantId === tenantId.value)) {
        const first = list[0]
        setTenant(first.id || first.tenantId, first.name || first.tenantName)
      }
    } catch (error) {
    }
  }

  /**
   * 设置当前租户
   */
  function setTenant(id, name) {
    tenantId.value = id
    tenantName.value = name
    sessionStorage.setItem('tenantId', id)
    sessionStorage.setItem('tenantName', name)
    if (localStorage.getItem('token')) {
      localStorage.setItem('tenantId', id)
      localStorage.setItem('tenantName', name)
    }
  }

  /**
   * 退出登录
   */
  function logout() {
    token.value = ''
    user.value = null
    tenants.value = []
    sessionStorage.removeItem('token')
    sessionStorage.removeItem('user')
    sessionStorage.removeItem('tenantId')
    sessionStorage.removeItem('tenantName')
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    localStorage.removeItem('tenantId')
    localStorage.removeItem('tenantName')
    router.push('/login')
  }

  return {
    token,
    user,
    isLoggedIn,
    username,
    isAdmin,
    tenantId,
    tenantName,
    tenants,
    login,
    logout,
    fetchProfile,
    fetchTenants,
    setTenant
  }
})

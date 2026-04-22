<template>
  <div class="user-manage">
    <div class="page-header">
      <h2>{{ $t('user.manage') }}</h2>
      <div class="header-actions">
        <el-input
          v-model="keyword"
          :placeholder="$t('user.searchPlaceholder')"
          :prefix-icon="Search"
          clearable
          style="width: 240px"
          @clear="loadUsers"
          @keyup.enter="loadUsers"
        />
      </div>
    </div>

    <el-table :data="users" v-loading="loading" stripe>
      <el-table-column prop="username" :label="$t('user.username')" min-width="120" />
      <el-table-column prop="email" :label="$t('settings.email')" min-width="180" />
      <el-table-column prop="role" :label="$t('user.role')" width="120">
        <template #default="{ row }">
          <el-select
            v-model="row.role"
            size="small"
            style="width: 100px"
            @change="handleRoleChange(row)"
          >
            <el-option label="Admin" value="admin" />
            <el-option label="User" value="user" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column prop="status" :label="$t('user.status')" width="120">
        <template #default="{ row }">
          <el-tag :type="row.status === 'active' ? 'success' : 'danger'" size="small">
            {{ row.status === 'active' ? $t('user.active') : $t('user.disabled') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('user.createdAt')" width="170">
        <template #default="{ row }">
          {{ formatTime(row.createTime || row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column :label="$t('common.actions')" width="120" fixed="right">
        <template #default="{ row }">
          <el-button
            :type="row.status === 'active' ? 'danger' : 'success'"
            link
            size="small"
            @click="handleToggleStatus(row)"
          >
            {{ row.status === 'active' ? $t('user.disable') : $t('user.enable') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @size-change="loadUsers"
        @current-change="loadUsers"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { getUserList, updateUserRole, updateUserStatus } from '@/api/index'
import { formatTime } from '@/utils/format'
import { extractList, extractTotal } from '@/utils/response'

const { t } = useI18n()

const users = ref([])
const loading = ref(false)
const keyword = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)

async function loadUsers() {
  loading.value = true
  try {
    const data = await getUserList({ keyword: keyword.value, page: page.value - 1, size: size.value })
    users.value = extractList(data)
    total.value = extractTotal(data)
  } catch (e) {
    ElMessage.error(t('common.loadFailed'))
  } finally {
    loading.value = false
  }
}

async function handleRoleChange(row) {
  try {
    await updateUserRole(row.id, row.role)
    ElMessage.success(t('user.roleUpdateSuccess'))
  } catch (e) {
    row.role = row.role === 'admin' ? 'user' : 'admin'
    ElMessage.error(t('user.roleUpdateFailed'))
  }
}

async function handleToggleStatus(row) {
  const newStatus = row.status === 'active' ? 'disabled' : 'active'
  try {
    await ElMessageBox.confirm(
      t('user.statusConfirmMsg'),
      t('common.confirm'),
      { type: 'warning' }
    )
    await updateUserStatus(row.id, newStatus)
    row.status = newStatus
    ElMessage.success(t('user.statusUpdateSuccess'))
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(t('user.statusUpdateFailed'))
    }
  }
}

onMounted(() => {
  loadUsers()
})
</script>

<style scoped>
.user-manage {
  padding: 0;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.page-header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary, #303133);
}
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}
</style>

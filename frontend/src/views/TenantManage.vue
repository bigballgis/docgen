<template>
  <div class="tenant-manage">
    <div class="page-header">
      <h2>{{ $t('tenant.manage') }}</h2>
      <el-button type="primary" @click="handleCreate">
        {{ $t('tenant.create') }}
      </el-button>
    </div>

    <el-table :data="tenants" v-loading="loading" stripe>
      <el-table-column prop="name" :label="$t('tenant.name')" min-width="150" />
      <el-table-column prop="description" :label="$t('tenant.description')" min-width="200" show-overflow-tooltip />
      <el-table-column prop="contactEmail" :label="$t('tenant.contactEmail')" min-width="180" />
      <el-table-column prop="status" :label="$t('tenant.status')" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small">
            {{ row.status === 'active' ? $t('tenant.active') : $t('tenant.inactive') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('tenant.createdAt')" width="170">
        <template #default="{ row }">
          {{ formatTime(row.createTime || row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column :label="$t('common.actions')" width="120" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="handleEdit(row)">
            {{ $t('common.edit') }}
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
        @size-change="loadTenants"
        @current-change="loadTenants"
      />
    </div>

    <!-- 新建/编辑对话框 -->
    <el-dialog
      v-model="showDialog"
      :title="isEdit ? $t('tenant.editTitle') : $t('tenant.createTitle')"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item :label="$t('tenant.name')" prop="name">
          <el-input v-model="form.name" :placeholder="$t('tenant.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('tenant.description')">
          <el-input v-model="form.description" type="textarea" :rows="3" :placeholder="$t('tenant.descPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('tenant.contactEmail')">
          <el-input v-model="form.contactEmail" :placeholder="$t('tenant.emailPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ $t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { getTenantList, createTenant, updateTenant } from '@/api/index'
import { formatTime } from '@/utils/format'
import { extractList, extractTotal } from '@/utils/response'

const { t } = useI18n()

const tenants = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)

const showDialog = ref(false)
const isEdit = ref(false)
const editId = ref(null)
const submitting = ref(false)
const formRef = ref(null)

const form = reactive({
  name: '',
  description: '',
  contactEmail: ''
})

const rules = {
  name: [{ required: true, message: () => t('tenant.nameRequired'), trigger: 'blur' }]
}

async function loadTenants() {
  loading.value = true
  try {
    const data = await getTenantList({ page: page.value - 1, size: size.value })
    tenants.value = extractList(data)
    total.value = extractTotal(data)
  } catch (e) {
    ElMessage.error(t('common.loadFailed'))
  } finally {
    loading.value = false
  }
}

function handleCreate() {
  isEdit.value = false
  editId.value = null
  Object.assign(form, { name: '', description: '', contactEmail: '' })
  showDialog.value = true
}

function handleEdit(row) {
  isEdit.value = true
  editId.value = row.id
  Object.assign(form, {
    name: row.name || '',
    description: row.description || '',
    contactEmail: row.contactEmail || ''
  })
  showDialog.value = true
}

async function handleSubmit() {
  try {
    await formRef.value.validate()
  } catch { return }

  submitting.value = true
  try {
    if (isEdit.value) {
      await updateTenant(editId.value, form)
      ElMessage.success(t('tenant.updateSuccess'))
    } else {
      await createTenant(form)
      ElMessage.success(t('tenant.createSuccess'))
    }
    showDialog.value = false
    loadTenants()
  } catch (e) {
    ElMessage.error(t('common.error'))
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadTenants()
})
</script>

<style scoped>
.tenant-manage { padding: 0; }
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

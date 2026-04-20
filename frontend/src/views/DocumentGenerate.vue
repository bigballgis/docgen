<template>
  <div class="document-generate">
    <el-row :gutter="24">
      <!-- 左侧：分类导航 + 模板选择 -->
      <el-col :span="8">
        <div class="page-card">
          <h3 class="card-title">{{ $t('document.selectTemplate') }}</h3>

          <!-- 分类导航 -->
          <div class="category-nav">
            <div
              v-for="cat in categories"
              :key="cat.value"
              :class="['category-item', { active: activeCategory === cat.value }]"
              @click="activeCategory = cat.value"
            >
              <el-icon :size="16"><component :is="cat.icon" /></el-icon>
              <span>{{ cat.label }}</span>
              <el-badge
                v-if="getCategoryCount(cat.value) > 0"
                :value="getCategoryCount(cat.value)"
                :max="99"
                class="category-badge"
              />
            </div>
          </div>

          <el-divider />

          <!-- 搜索 -->
          <el-input
            v-model="templateSearch"
            :placeholder="$t('template.search')"
            clearable
            :prefix-icon="Search"
            class="mb-16"
          />

          <!-- 模板列表 -->
          <div class="template-list" v-loading="templateLoading">
            <div
              v-for="tpl in filteredTemplates"
              :key="tpl.id"
              :class="['template-item', { active: selectedTemplate?.id === tpl.id }]"
              role="button"
              :aria-label="tpl.name"
              tabindex="0"
              @click="selectTemplate(tpl)"
              @keydown.enter="selectTemplate(tpl)"
            >
              <div class="template-item-icon">
                <el-icon :size="28">
                  <Document />
                </el-icon>
              </div>
              <div class="template-item-info">
                <div class="template-item-name">{{ tpl.name }}</div>
                <div class="template-item-meta">
                  <el-tag
                    v-if="tpl.category"
                    size="small"
                    type="info"
                  >
                    {{ tpl.category }}
                  </el-tag>
                  <span v-if="tpl.fields && tpl.fields.length">
                    {{ tpl.fields.length }} {{ $t('document.fieldUnit') }}
                  </span>
                </div>
                <div class="template-item-desc" v-if="tpl.description">
                  {{ tpl.description }}
                </div>
              </div>
            </div>
            <!-- 没有已发布模板时的友好提示 -->
            <el-empty
              v-if="!templateLoading && filteredTemplates.length === 0 && templates.length === 0"
              :description="$t('document.noPublishedTemplate')"
              :image-size="80"
            >
              <el-button type="primary" @click="$router.push('/templates')">
                {{ $t('document.goToTemplateManage') }}
              </el-button>
            </el-empty>
            <el-empty
              v-else-if="!templateLoading && filteredTemplates.length === 0"
              :description="$t('document.noTemplateInCategory')"
              :image-size="80"
            />
          </div>
        </div>
      </el-col>

      <!-- 右侧：表单填写 -->
      <el-col :span="16">
        <div class="page-card">
          <div class="form-header">
            <div>
              <h3 class="card-title">{{ $t('document.fillData') }}</h3>
              <p class="card-desc" v-if="selectedTemplate">
                {{ $t('document.templatePrefix') }}{{ selectedTemplate.name }}
                <span v-if="selectedTemplate.fields">
                  {{ $t('document.fieldCount', { count: selectedTemplate.fields.length }) }}
                </span>
              </p>
            </div>
            <div class="header-actions">
              <el-select
                v-model="outputFormat"
                style="width: 140px; margin-right: 12px;"
                :disabled="!selectedTemplate"
              >
                <el-option :label="$t('document.word')" value="docx" />
                <el-option :label="$t('document.pdf')" value="pdf" />
              </el-select>
              <el-button
                type="primary"
                @click="handleGenerate"
                :loading="generating"
                :disabled="!selectedTemplate"
              >
                <el-icon><Download /></el-icon>
                {{ $t('document.generateBtn') }}
              </el-button>
            </div>
          </div>

          <!-- 未选择模板提示 -->
          <div v-if="!selectedTemplate" class="empty-form">
            <el-empty :description="$t('document.selectTemplateFirst')">
              <el-button type="primary" @click="$router.push('/templates')">
                {{ $t('document.goToTemplateManage') }}
              </el-button>
            </el-empty>
          </div>

          <!-- 字段加载中 -->
          <div v-else-if="fieldsLoading" class="empty-form">
            <el-skeleton :rows="8" animated />
          </div>

          <!-- 无字段提示 -->
          <div v-else-if="!templateFields || templateFields.length === 0" class="empty-form">
            <el-empty :description="$t('document.noFields')">
              <el-button type="primary" @click="$router.push('/templates')">
                {{ $t('document.goToTemplateManage') }}
              </el-button>
            </el-empty>
          </div>

          <!-- 动态表单区域 -->
          <el-form
            v-else
            ref="formRef"
            :model="formData"
            :rules="formRules"
            label-width="140px"
            label-position="right"
            class="generate-form"
          >
            <el-row :gutter="20">
              <el-col
                :span="field.span || 12"
                v-for="field in templateFields"
                :key="field.name"
              >
                <el-form-item
                  :label="field.label || field.name"
                  :prop="field.name"
                >
                  <!-- text 类型 -->
                  <el-input
                    v-if="field.type === 'text'"
                    v-model="formData[field.name]"
                    :placeholder="field.placeholder || `${$t('common.loading')} ${field.label || field.name}`"
                  />

                  <!-- number 类型 -->
                  <el-input-number
                    v-else-if="field.type === 'number'"
                    v-model="formData[field.name]"
                    :min="field.min"
                    :max="field.max"
                    :precision="field.precision"
                    :step="field.step || 1"
                    :placeholder="field.placeholder || `${$t('common.loading')} ${field.label || field.name}`"
                    style="width: 100%"
                    controls-position="right"
                  />

                  <!-- date 类型 -->
                  <el-date-picker
                    v-else-if="field.type === 'date'"
                    v-model="formData[field.name]"
                    type="date"
                    :placeholder="field.placeholder || `${$t('common.loading')} ${field.label || field.name}`"
                    style="width: 100%"
                    value-format="YYYY-MM-DD"
                  />

                  <!-- select 类型 -->
                  <el-select
                    v-else-if="field.type === 'select'"
                    v-model="formData[field.name]"
                    :placeholder="field.placeholder || `${$t('common.loading')} ${field.label || field.name}`"
                    style="width: 100%"
                    clearable
                  >
                    <el-option
                      v-for="opt in (field.options || [])"
                      :key="opt.value"
                      :label="opt.label"
                      :value="opt.value"
                    />
                  </el-select>

                  <!-- textarea 类型 -->
                  <el-input
                    v-else-if="field.type === 'textarea'"
                    v-model="formData[field.name]"
                    type="textarea"
                    :rows="field.rows || 3"
                    :placeholder="field.placeholder || `${$t('common.loading')} ${field.label || field.name}`"
                  />

                  <!-- 默认 text -->
                  <el-input
                    v-else
                    v-model="formData[field.name]"
                    :placeholder="field.placeholder || `${$t('common.loading')} ${field.label || field.name}`"
                  />
                </el-form-item>
              </el-col>
            </el-row>

            <!-- 底部操作 -->
            <div class="form-actions">
              <el-button @click="handleResetForm">{{ $t('document.resetForm') }}</el-button>
              <el-button type="primary" @click="handleGenerate" :loading="generating">
                <el-icon><Download /></el-icon>
                {{ $t('document.generateDownload') }}
              </el-button>
            </div>
          </el-form>

          <!-- 生成成功提示 -->
          <div v-if="generatedFileKey" class="success-tip">
            <el-result icon="success" :title="$t('document.generateSuccess')" :sub-title="$t('document.generateSuccessSub')">
              <template #extra>
                <el-button type="primary" @click="handleOpenEditor">
                  <el-icon><EditPen /></el-icon>
                  {{ $t('document.openInEditor') }}
                </el-button>
                <el-button @click="generatedFileKey = ''">{{ $t('document.close') }}</el-button>
              </template>
            </el-result>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Search, Document, Download, EditPen, Tickets, CreditCard, More } from '@element-plus/icons-vue'
import { getTemplates, getTemplate, generateDocument } from '@/api/index'
import { downloadBlob, getContentDispositionFileName } from '@/utils/download'

const router = useRouter()
const { t } = useI18n()

// ==================== 分类导航 ====================
const activeCategory = ref('all')

const categories = computed(() => [
  { label: t('template.all'), value: 'all', icon: 'More' },
  { label: t('template.tradeFinance'), value: 'trade', icon: 'Tickets' },
  { label: t('template.credit'), value: 'credit', icon: 'CreditCard' },
  { label: t('template.other'), value: 'other', icon: 'More' }
])

/**
 * 获取分类下的模板数量
 */
function getCategoryCount(categoryValue) {
  if (categoryValue === 'all') return templates.value.length
  return templates.value.filter(t => {
    const cat = (t.category || '').toLowerCase()
    if (categoryValue === 'trade') return cat.includes('贸易') || cat.includes('trade') || cat.includes('fol')
    if (categoryValue === 'credit') return cat.includes('信用') || cat.includes('credit') || cat.includes('lo')
    return true
  }).length
}

// ==================== 模板列表 ====================
const templateSearch = ref('')
const templateLoading = ref(false)
const templates = ref([])

/**
 * 加载模板列表（只加载已发布的模板，后端已过滤）
 */
async function loadTemplates() {
  templateLoading.value = true
  try {
    const data = await getTemplates({ page: 0, size: 100 })
    let list = data?.content || data?.list || data?.records || []
    list = list.map(item => {
      if (item.fields && typeof item.fields === 'string') {
        try {
          item.fields = JSON.parse(item.fields)
        } catch {
          item.fields = []
        }
      }
      return item
    })
    templates.value = list
  } catch (e) {
    console.error('加载模板列表失败', e)
  } finally {
    templateLoading.value = false
  }
}

const filteredTemplates = computed(() => {
  let result = templates.value

  // 按分类筛选
  if (activeCategory.value !== 'all') {
    const cat = activeCategory.value
    result = result.filter(t => {
      const tCat = (t.category || '').toLowerCase()
      if (cat === 'trade') return tCat.includes('贸易') || tCat.includes('trade') || tCat.includes('fol')
      if (cat === 'credit') return tCat.includes('信用') || tCat.includes('credit') || tCat.includes('lo')
      if (cat === 'other') {
        return !tCat.includes('贸易') && !tCat.includes('trade') && !tCat.includes('fol')
          && !tCat.includes('信用') && !tCat.includes('credit') && !tCat.includes('lo')
      }
      return true
    })
  }

  // 按关键词搜索
  if (templateSearch.value) {
    const keyword = templateSearch.value.toLowerCase()
    result = result.filter(t =>
      t.name.toLowerCase().includes(keyword) ||
      (t.category && t.category.toLowerCase().includes(keyword))
    )
  }

  return result
})

// ==================== 选中模板 & 字段 ====================
const selectedTemplate = ref(null)
const templateFields = ref([])
const fieldsLoading = ref(false)

/**
 * 选择模板
 */
async function selectTemplate(tpl) {
  selectedTemplate.value = tpl
  generatedFileKey.value = ''
  formData = reactive({})
  formRules = {}

  if (tpl.fields && tpl.fields.length > 0) {
    templateFields.value = tpl.fields
    initFormData(tpl.fields)
    return
  }

  fieldsLoading.value = true
  templateFields.value = []
  try {
    const data = await getTemplate(tpl.id)
    let fields = data?.fields || []
    if (fields && typeof fields === 'string') {
      try {
        fields = JSON.parse(fields)
      } catch {
        fields = []
      }
    }
    templateFields.value = fields
    tpl.fields = fields
    initFormData(fields)
  } catch (e) {
    console.error('获取模板详情失败', e)
    ElMessage.error(t('document.selectFirst'))
  } finally {
    fieldsLoading.value = false
  }
}

/**
 * 根据字段定义初始化表单数据和校验规则
 */
function initFormData(fields) {
  const newFormData = {}
  const newRules = {}

  fields.forEach(field => {
    if (field.type === 'number') {
      newFormData[field.name] = field.defaultValue !== undefined ? Number(field.defaultValue) : undefined
    } else {
      newFormData[field.name] = field.defaultValue !== undefined ? field.defaultValue : ''
    }

    if (field.required) {
      const isSelect = field.type === 'select'
      newRules[field.name] = [
        {
          required: true,
          message: isSelect
            ? `请选择${field.label || field.name}`
            : `请输入${field.label || field.name}`,
          trigger: isSelect ? 'change' : 'blur'
        }
      ]
    }
  })

  Object.keys(formData).forEach(key => delete formData[key])
  Object.assign(formData, newFormData)

  Object.keys(formRules).forEach(key => delete formRules[key])
  Object.assign(formRules, newRules)
}

// ==================== 表单 ====================
let formData = reactive({})
let formRules = {}
const formRef = ref(null)
const generating = ref(false)
const outputFormat = ref('docx')
const generatedFileKey = ref('')

/**
 * 生成文档
 */
async function handleGenerate() {
  if (!selectedTemplate.value) {
    ElMessage.warning(t('document.selectFirst'))
    return
  }

  if (!formRef.value) return

  try {
    await formRef.value.validate()
  } catch {
    ElMessage.warning(t('document.fillRequired'))
    return
  }

  generating.value = true
  try {
    const response = await generateDocument({
      templateId: selectedTemplate.value.id,
      data: { ...formData },
      outputFormat: outputFormat.value
    })

    const blob = response.data || response

    let fileName = getContentDispositionFileName(response)
    if (fileName === 'download') {
      const ext = outputFormat.value === 'pdf' ? '.pdf' : '.docx'
      fileName = `${selectedTemplate.value.name || '文档'}${ext}`
    }

    downloadBlob(blob, fileName)
    ElMessage.success(t('document.generateDownloaded'))

    // 模拟一个 fileKey 用于编辑器跳转
    generatedFileKey.value = `doc_${Date.now()}`
  } catch (e) {
    console.error('生成文档失败', e)
  } finally {
    generating.value = false
  }
}

/**
 * 在编辑器中打开
 */
function handleOpenEditor() {
  if (generatedFileKey.value) {
    router.push(`/editor/${generatedFileKey.value}`)
  }
}

/**
 * 重置表单
 */
function handleResetForm() {
  if (formRef.value) {
    formRef.value.resetFields()
  }
  if (templateFields.value.length > 0) {
    initFormData(templateFields.value)
  }
  ElMessage.info(t('document.formReset'))
}

// ==================== 页面初始化 ====================
onMounted(() => {
  loadTemplates()
})
</script>

<style scoped>
.document-generate {
  max-width: 1200px;
  margin: 0 auto;
}

.card-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.card-desc {
  font-size: 13px;
  color: var(--text-secondary);
  margin-top: 4px;
}

/* 分类导航 */
.category-nav {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}

.category-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: 6px;
  font-size: 13px;
  color: var(--text-regular);
  cursor: pointer;
  transition: all 0.2s ease;
  border: 1px solid var(--border-light);
  background: var(--bg);
  user-select: none;
}

.category-item:hover {
  border-color: var(--primary);
  color: var(--primary);
}

.category-item.active {
  background: var(--primary);
  color: #fff;
  border-color: var(--primary);
}

.category-badge {
  margin-left: 4px;
}

/* 模板列表 */
.template-list {
  max-height: 500px;
  overflow-y: auto;
}

.template-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
  border: 2px solid transparent;
  margin-bottom: 8px;
}

.template-item:hover {
  background-color: var(--bg);
}

.template-item.active {
  background-color: rgba(26, 54, 93, 0.05);
  border-color: var(--primary);
}

.template-item-icon {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  background-color: var(--bg);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--primary);
  flex-shrink: 0;
}

.template-item.active .template-item-icon {
  background-color: rgba(26, 54, 93, 0.1);
}

.template-item-info {
  flex: 1;
  min-width: 0;
}

.template-item-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.template-item-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--text-secondary);
}

.template-item-desc {
  font-size: 12px;
  color: var(--text-placeholder);
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 表单区域 */
.form-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

.header-actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.empty-form {
  padding: 60px 0;
}

.generate-form {
  margin-top: 8px;
}

.generate-form :deep(.el-form-item__label) {
  font-weight: 500;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 32px;
  padding-top: 24px;
  border-top: 1px solid var(--border-light);
}

.success-tip {
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px solid var(--border-light);
}
</style>

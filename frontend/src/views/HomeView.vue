<template>
  <div class="home-view">
    <!-- 欢迎横幅 -->
    <section class="welcome-banner">
      <div class="banner-content">
        <div class="banner-text">
          <h1 class="banner-title">
            {{ $t('home.welcome') }}
          </h1>
          <p class="banner-desc">
            {{ $t('home.description') }}
            {{ $t('home.descriptionExtra') }}
          </p>
          <div class="banner-actions">
            <el-button type="primary" size="large" @click="$router.push('/generate')">
              <el-icon><EditPen /></el-icon>
              {{ $t('home.generateNow') }}
            </el-button>
            <el-button size="large" class="banner-btn-outline" @click="$router.push('/templates')">
              <el-icon><Files /></el-icon>
              {{ $t('home.manageTemplates') }}
            </el-button>
          </div>
        </div>
        <div class="banner-decoration">
          <el-icon :size="120" class="deco-icon"><Document /></el-icon>
        </div>
      </div>
    </section>

    <!-- 快速操作 -->
    <section class="quick-actions">
      <h2 class="section-title">{{ $t('home.quickActions') }}</h2>
      <el-row :gutter="20">
        <el-col :xs="24" :sm="8" v-for="action in quickActions" :key="action.title">
          <div class="quick-action-card" @click="$router.push({ path: action.path, query: action.query })">
            <div class="action-icon" :style="{ backgroundColor: action.bgColor, color: action.iconColor }">
              <el-icon :size="24"><component :is="action.icon" /></el-icon>
            </div>
            <div class="action-title">{{ action.title }}</div>
            <div class="action-desc">{{ action.desc }}</div>
          </div>
        </el-col>
      </el-row>
    </section>

    <!-- 统计数据 -->
    <section class="stats-section" v-loading="statsLoading">
      <h2 class="section-title">{{ $t('home.dataOverview') }}</h2>
      <el-row :gutter="20">
        <el-col :xs="24" :sm="8" v-for="stat in stats" :key="stat.label">
          <div class="stat-card">
            <div class="stat-icon" :style="{ backgroundColor: stat.bgColor, color: stat.iconColor }">
              <el-icon :size="24"><component :is="stat.icon" /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value" :style="{ color: stat.iconColor }">{{ stat.value }}</div>
              <div class="stat-label">{{ stat.label }}</div>
            </div>
          </div>
        </el-col>
      </el-row>
    </section>

    <!-- 最近文档 -->
    <section class="recent-section page-card">
      <div class="section-header">
        <h2 class="section-title-inline">{{ $t('home.recentDocs') }}</h2>
        <el-button type="primary" link @click="$router.push('/history')">
          {{ $t('home.viewAll') }}
          <el-icon><ArrowRight /></el-icon>
        </el-button>
      </div>
      <el-table
        :data="recentDocs"
        stripe
        style="width: 100%"
        v-loading="recentLoading"
        :empty-text="$t('home.noDocs')"
      >
        <el-table-column prop="name" :label="$t('document.docName')" min-width="200" show-overflow-tooltip />
        <el-table-column prop="templateName" :label="$t('document.template')" width="160" show-overflow-tooltip />
        <el-table-column prop="format" :label="$t('document.format')" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ row.format || 'DOCX' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" :label="$t('document.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="statusType(row.status)"
            >
              {{ statusLabel(row.status, t) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" :label="$t('document.generatedAt')" width="180" sortable />
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { EditPen, Files, Document, DataLine, Tickets, ArrowRight } from '@element-plus/icons-vue'
import { getDocumentList, getTemplates, getDashboardStats } from '@/api/index'
import { extractList, extractTotal } from '@/utils/response'
import { statusType, statusLabel } from '@/utils/status'

const { t } = useI18n()

// ==================== 统计数据 ====================
const statsLoading = ref(false)
const todayCount = ref(0)
const templateCount = ref(0)
const docCount = ref(0)

/**
 * 加载统计数据
 */
async function loadStats() {
  statsLoading.value = true
  try {
    // 尝试调用仪表盘 API
    const data = await getDashboardStats()
    todayCount.value = data?.todayCount || data?.todayGenerated || 0
    templateCount.value = data?.templateCount || data?.templateTotal || 0
    docCount.value = data?.docCount || data?.documentTotal || 0
  } catch (e) {
    // 仪表盘 API 不可用，前端聚合
    console.warn('仪表盘 API 不可用，前端聚合数据', e)
    try {
      // 并行请求文档和模板的 total
      const [docRes, tplRes] = await Promise.allSettled([
        getDocumentList({ page: 0, size: 1 }),
        getTemplates({ page: 0, size: 1 })
      ])
      if (docRes.status === 'fulfilled') {
        docCount.value = extractTotal(docRes.value)
      }
      if (tplRes.status === 'fulfilled') {
        templateCount.value = extractTotal(tplRes.value)
      }
      // 今日生成数暂时无法从列表 API 获取，设为 0
      todayCount.value = 0
    } catch (e2) {
      console.warn('聚合统计数据失败', e2)
    }
  } finally {
    statsLoading.value = false
  }
}

const quickActions = computed(() => [
  {
    title: t('home.generateFOL'),
    desc: t('home.generateFOLDesc'),
    icon: 'Tickets',
    path: '/generate',
    query: { type: 'FOL' },
    bgColor: 'rgba(26, 54, 93, 0.08)',
    iconColor: '#1a365d'
  },
  {
    title: t('home.generateLO'),
    desc: t('home.generateLODesc'),
    icon: 'DataLine',
    path: '/generate',
    query: { type: 'LO' },
    bgColor: 'rgba(201, 169, 110, 0.12)',
    iconColor: '#c9a96e'
  },
  {
    title: t('home.manageTemplatesCard'),
    desc: t('home.manageTemplatesDesc'),
    icon: 'Files',
    path: '/templates',
    bgColor: 'rgba(103, 194, 58, 0.08)',
    iconColor: '#67c23a'
  }
])

const stats = computed(() => [
  {
    value: todayCount.value,
    label: t('home.todayGenerated'),
    icon: 'EditPen',
    bgColor: 'rgba(26, 54, 93, 0.08)',
    iconColor: '#1a365d'
  },
  {
    value: templateCount.value,
    label: t('home.templateCount'),
    icon: 'Files',
    bgColor: 'rgba(201, 169, 110, 0.12)',
    iconColor: '#c9a96e'
  },
  {
    value: docCount.value,
    label: t('home.docCount'),
    icon: 'Document',
    bgColor: 'rgba(103, 194, 58, 0.08)',
    iconColor: '#67c23a'
  }
])

// ==================== 最近文档 ====================
const recentDocs = ref([])
const recentLoading = ref(false)

/**
 * 加载最近文档
 */
async function loadRecentDocs() {
  recentLoading.value = true
  try {
    const data = await getDocumentList({ page: 0, size: 5 })
    recentDocs.value = extractList(data)
  } catch (e) {
    ElMessage.error(t('common.loadFailed'))
    recentDocs.value = []
  } finally {
    recentLoading.value = false
  }
}

// ==================== 页面初始化 ====================
onMounted(() => {
  loadStats()
  loadRecentDocs()
})
</script>

<style scoped>
.home-view {
  max-width: 1200px;
  margin: 0 auto;
}

/* 欢迎横幅 */
.welcome-banner {
  background: linear-gradient(135deg, var(--primary-dark) 0%, var(--primary) 60%, var(--primary-light) 100%);
  border-radius: 12px;
  padding: 48px;
  margin-bottom: 32px;
  color: #fff;
  position: relative;
  overflow: hidden;
}

.welcome-banner::after {
  content: '';
  position: absolute;
  width: 300px;
  height: 300px;
  background: radial-gradient(circle, rgba(201, 169, 110, 0.15) 0%, transparent 70%);
  top: -100px;
  right: -50px;
  border-radius: 50%;
}

.banner-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  position: relative;
  z-index: 1;
}

.banner-text {
  flex: 1;
  max-width: 640px;
}

.banner-title {
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 12px;
  line-height: 1.3;
}

.banner-desc {
  font-size: 15px;
  line-height: 1.8;
  opacity: 0.85;
  margin-bottom: 28px;
}

.banner-actions {
  display: flex;
  gap: 12px;
}

.banner-actions .el-button--primary {
  background-color: var(--accent);
  border-color: var(--accent);
  color: var(--primary-dark);
  font-weight: 600;
}

.banner-actions .el-button--primary:hover {
  background-color: var(--accent-light);
  border-color: var(--accent-light);
}

.banner-btn-outline {
  background: transparent !important;
  color: #fff !important;
  border-color: rgba(255, 255, 255, 0.5) !important;
}

.banner-btn-outline:hover {
  border-color: #fff !important;
  background: rgba(255, 255, 255, 0.1) !important;
}

.banner-decoration {
  flex-shrink: 0;
  margin-left: 40px;
}

.deco-icon {
  opacity: 0.15;
}

/* 快速操作 */
.quick-actions {
  margin-bottom: 32px;
}

.section-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 20px;
}

/* 统计数据 */
.stats-section {
  margin-bottom: 32px;
}

/* 最近文档 */
.recent-section {
  margin-bottom: 32px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.section-title-inline {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}

@media (max-width: 768px) {
  .welcome-banner {
    padding: 32px 24px;
  }

  .banner-content {
    flex-direction: column;
  }

  .banner-decoration {
    display: none;
  }

  .banner-title {
    font-size: 22px;
  }
}
</style>

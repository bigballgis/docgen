<template>
  <div class="not-found">
    <div class="not-found-content">
      <h1>404</h1>
      <p>{{ $t('common.pageNotFound') }}</p>
      <el-button type="primary" @click="$router.push('/')">
        {{ $t('common.backToHome') }}
      </el-button>
    </div>
  </div>
</template>

<script setup>
</script>

<style scoped>
.not-found {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: var(--bg, #f0f2f5);
}
.not-found-content {
  text-align: center;
}
.not-found-content h1 {
  font-size: 120px;
  font-weight: 700;
  color: var(--text-primary, #303133);
  margin: 0;
  line-height: 1;
}
.not-found-content p {
  font-size: 18px;
  color: var(--text-secondary, #909399);
  margin: 16px 0 32px;
}
</style>

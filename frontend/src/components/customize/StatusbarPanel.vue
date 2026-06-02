<template>
  <div class="panel">
    <h3 class="panel-title">状态栏设置</h3>

    <div class="form-group">
      <label class="form-label">背景颜色</label>
      <el-color-picker v-model="config.statusbar.color" @change="themeStore.saveTheme()" />
    </div>

    <div class="form-group">
      <label class="form-label">透明度 {{ config.statusbar.opacity }}%</label>
      <el-slider v-model="config.statusbar.opacity" :min="0" :max="100" @change="themeStore.saveTheme()" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { useThemeStore } from '@/stores/theme'
const themeStore = useThemeStore()
const config = themeStore.config
</script>

<style scoped lang="scss">
.panel {
  background: var(--card-bg);
  border-radius: 12px;
  box-shadow: var(--card-shadow);
  padding: 24px;
}

.panel-title {
  margin: 0 0 24px;
  font-size: 16px;
  color: var(--text-primary);
}

.form-group {
  margin-bottom: 20px;
}

.form-label {
  display: block;
  font-size: 13px;
  color: var(--text-regular);
  margin-bottom: 10px;
  font-weight: 500;
}
</style>

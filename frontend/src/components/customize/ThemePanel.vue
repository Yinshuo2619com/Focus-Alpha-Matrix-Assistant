<template>
  <div class="panel">
    <h3 class="panel-title">主题设置</h3>

    <div class="form-group">
      <label class="form-label">预设主题</label>
      <div class="preset-options">
        <div
          v-for="p in presets"
          :key="p.value"
          class="preset-item"
          :class="{ active: config.theme.preset === p.value }"
          @click="config.theme.preset = p.value; themeStore.saveTheme()"
        >
          <div class="preset-preview" :class="p.value">
            <span v-if="p.value === 'auto'">A</span>
            <span v-else-if="p.value === 'light'">L</span>
            <span v-else>D</span>
          </div>
          <span class="preset-label">{{ p.label }}</span>
        </div>
      </div>
    </div>

    <div class="form-group">
      <label class="form-label">主题色</label>
      <div class="color-options">
        <div
          v-for="c in colors"
          :key="c"
          class="color-dot"
          :class="{ active: config.theme.color === c }"
          :style="{ background: c }"
          @click="config.theme.color = c; themeStore.saveTheme()"
        />
        <el-color-picker
          v-model="config.theme.color"
          size="small"
          @change="themeStore.saveTheme()"
        />
      </div>
    </div>

    <div class="form-group">
      <label class="form-label">圆角大小</label>
      <div class="rounded-options">
        <div
          v-for="r in roundedOptions"
          :key="r.value"
          class="rounded-item"
          :class="{ active: config.interface.rounded === r.value }"
          @click="config.interface.rounded = r.value; themeStore.saveTheme()"
          :style="{ borderRadius: r.radius }"
        >
          {{ r.label }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useThemeStore } from '@/stores/theme'

const themeStore = useThemeStore()
const config = themeStore.config

const presets = [
  { value: 'auto' as const, label: '自动' },
  { value: 'light' as const, label: '浅色' },
  { value: 'dark' as const, label: '深色' },
]

const colors = ['#409eff', '#10B981', '#f56c6c', '#e6a23c', '#9b59b6', '#e91e63']

const roundedOptions = [
  { value: 'none' as const, label: '无', radius: '0' },
  { value: 'small' as const, label: '小', radius: '4px' },
  { value: 'medium' as const, label: '中', radius: '8px' },
  { value: 'large' as const, label: '大', radius: '16px' },
]
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
  margin-bottom: 24px;

  &:last-child {
    margin-bottom: 0;
  }
}

.form-label {
  display: block;
  font-size: 13px;
  color: var(--text-regular);
  margin-bottom: 10px;
  font-weight: 500;
}

.preset-options {
  display: flex;
  gap: 12px;
}

.preset-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  cursor: pointer;

  &.active .preset-preview {
    border-color: var(--accent);
    box-shadow: 0 0 0 2px var(--accent-light);
  }
}

.preset-preview {
  width: 56px;
  height: 36px;
  border-radius: 6px;
  border: 2px solid var(--card-border);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  transition: all 0.2s;

  &.light {
    background: #f5f7fa;
    color: #303133;
  }

  &.dark {
    background: #161b22;
    color: #e6edf3;
  }

  &.auto {
    background: linear-gradient(135deg, #f5f7fa 50%, #161b22 50%);
    color: #909399;
  }
}

.preset-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.color-options {
  display: flex;
  gap: 10px;
  align-items: center;
}

.color-dot {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  cursor: pointer;
  border: 2px solid transparent;
  transition: all 0.2s;

  &.active {
    border-color: var(--card-bg);
    box-shadow: 0 0 0 2px var(--accent);
  }
}

.rounded-options {
  display: flex;
  gap: 10px;
}

.rounded-item {
  padding: 8px 20px;
  border: 1px solid var(--card-border);
  font-size: 13px;
  color: var(--text-regular);
  cursor: pointer;
  transition: all 0.2s;

  &.active {
    border-color: var(--accent);
    color: var(--accent);
    background: var(--accent-light);
  }

  &:hover:not(.active) {
    border-color: var(--text-placeholder);
  }
}
</style>

<template>
  <div class="customize-page">
    <StatusBar />
    <div class="status-bar-placeholder"></div>

    <div class="customize-header">
      <div class="header-left">
        <el-icon class="back-btn" @click="$router.back()"><ArrowLeft /></el-icon>
        <h2>主题自定义</h2>
      </div>
      <div class="header-actions">
        <el-button @click="handleReset">重置默认</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">保存配置</el-button>
        <el-button @click="openJsonEditor">编辑 JSON</el-button>
      </div>
    </div>

    <div class="customize-body">
      <div class="side-nav">
        <div
          v-for="tab in tabs"
          :key="tab.key"
          class="nav-item"
          :class="{ active: activeTab === tab.key }"
          @click="activeTab = tab.key"
        >
          <el-icon class="nav-icon"><component :is="tab.icon" /></el-icon>
          <span>{{ tab.label }}</span>
        </div>
      </div>

      <div class="config-panel">
        <Transition name="panel-fade" mode="out-in">
          <ThemePanel v-if="activeTab === 'theme'" key="theme" />
          <LogoPanel v-else-if="activeTab === 'logo'" key="logo" />
          <StatusbarPanel v-else-if="activeTab === 'statusbar'" key="statusbar" />
          <InterfacePanel v-else-if="activeTab === 'interface'" key="interface" />
        </Transition>
      </div>
    </div>

    <el-dialog v-model="showJsonDialog" title="编辑 JSON" width="600px">
      <el-input
        v-model="jsonText"
        type="textarea"
        :rows="16"
        placeholder="主题 JSON 配置..."
      />
      <div class="json-actions">
        <el-button size="small" @click="handleCopyJson">复制到剪贴板</el-button>
        <el-button size="small" @click="handlePasteJson">从剪贴板粘贴</el-button>
      </div>
      <template #footer>
        <el-button @click="showJsonDialog = false">取消</el-button>
        <el-button type="primary" @click="handleApplyJson">应用</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Brush, Picture, Monitor, Grid } from '@element-plus/icons-vue'
import StatusBar from '@/components/StatusBar.vue'
import { useThemeStore } from '@/stores/theme'
import ThemePanel from '@/components/customize/ThemePanel.vue'
import LogoPanel from '@/components/customize/LogoPanel.vue'
import StatusbarPanel from '@/components/customize/StatusbarPanel.vue'
import InterfacePanel from '@/components/customize/InterfacePanel.vue'

const themeStore = useThemeStore()
const saving = ref(false)
const activeTab = ref('theme')
const showJsonDialog = ref(false)
const jsonText = ref('')

const tabs = [
  { key: 'theme', label: '主题', icon: Brush },
  { key: 'logo', label: '图标', icon: Picture },
  { key: 'statusbar', label: '状态栏', icon: Monitor },
  { key: 'interface', label: '界面', icon: Grid },
]

async function handleSave() {
  saving.value = true
  try {
    await themeStore.saveTheme()
    ElMessage.success('配置已保存')
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

function handleReset() {
  ElMessageBox.confirm('确定要重置为默认主题吗？', '重置确认', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    themeStore.resetTheme()
    ElMessage.success('已重置为默认主题')
  }).catch(() => {})
}

function openJsonEditor() {
  jsonText.value = themeStore.exportTheme()
  showJsonDialog.value = true
}

function handleCopyJson() {
  navigator.clipboard.writeText(jsonText.value).then(() => {
    ElMessage.success('已复制到剪贴板')
  }).catch(() => {
    ElMessage.error('复制失败，请手动复制')
  })
}

async function handlePasteJson() {
  try {
    const text = await navigator.clipboard.readText()
    jsonText.value = text
    ElMessage.success('已粘贴')
  } catch {
    ElMessage.error('无法读取剪贴板，请手动粘贴')
  }
}

function handleApplyJson() {
  if (!jsonText.value.trim()) {
    ElMessage.warning('JSON 不能为空')
    return
  }
  const ok = themeStore.importTheme(jsonText.value)
  if (ok) {
    jsonText.value = themeStore.exportTheme()
    ElMessage.success('已应用')
    showJsonDialog.value = false
  } else {
    ElMessage.error('JSON 格式错误')
  }
}
</script>

<style scoped lang="scss">
.customize-page {
  min-height: 100vh;
  background: var(--page-bg);
}

.status-bar-placeholder {
  height: 60px;
}

.customize-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  background: var(--card-bg);
  border-bottom: 1px solid var(--card-border);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;

  h2 {
    margin: 0;
    font-size: 18px;
    color: var(--text-primary);
  }
}

.back-btn {
  font-size: 20px;
  cursor: pointer;
  color: var(--text-regular);
  padding: 4px;
  border-radius: 4px;
  transition: background 0.2s;

  &:hover {
    background: var(--dropdown-hover-bg);
  }
}

.header-actions {
  display: flex;
  gap: 8px;
}

.customize-body {
  display: flex;
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
  gap: 20px;
}

.side-nav {
  width: 160px;
  flex-shrink: 0;
  background: var(--card-bg);
  border-radius: 12px;
  box-shadow: var(--card-shadow);
  overflow: hidden;
  position: sticky;
  top: 80px;
  align-self: flex-start;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 18px;
  cursor: pointer;
  font-size: 14px;
  color: var(--text-regular);
  transition: all 0.3s ease;
  border-left: 3px solid transparent;
  position: relative;

  &:hover {
    color: var(--accent);
    background: var(--page-bg);
    transform: translateX(2px);
  }

  &.active {
    color: var(--accent);
    font-weight: 600;
    border-left-color: var(--accent);
    background: var(--accent-light);
    transform: translateX(0);
  }
}

.nav-icon {
  font-size: 16px;
}

.config-panel {
  flex: 1;
  min-width: 0;
}

.json-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

/* 面板切换过渡 */
.panel-fade-enter-active,
.panel-fade-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}

.panel-fade-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.panel-fade-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

@media screen and (max-width: 768px) {
  .customize-header {
    flex-direction: column;
    gap: 12px;
    align-items: flex-start;
  }

  .header-actions {
    flex-wrap: wrap;
  }

  .customize-body {
    flex-direction: column;
    padding: 12px;
  }

  .side-nav {
    width: 100%;
    position: static;
    display: flex;
    overflow-x: auto;
  }

  .nav-item {
    border-left: none;
    border-bottom: 3px solid transparent;
    white-space: nowrap;

    &.active {
      border-left-color: transparent;
      border-bottom-color: var(--accent);
    }
  }
}
</style>

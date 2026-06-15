<template>
  <div class="theme-switcher" @mouseenter="showPopover = true" @mouseleave="showPopover = false">
    <div class="theme-icon" @click="showPopover = !showPopover">
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
        <path d="M20.38 3.46L16 2a4 4 0 01-8 0L3.62 3.46a2 2 0 00-1.34 2.23l.58 3.47a1 1 0 00.99.84H6v10c0 1.1.9 2 2 2h8a2 2 0 002-2V10h2.15a1 1 0 00.99-.84l.58-3.47a2 2 0 00-1.34-2.23z"/>
      </svg>
    </div>
    <transition name="fade">
      <div v-show="showPopover" class="popover-menu">
        <div class="popover-item" @click="handleCyclePreset">
          <el-icon><Sunny v-if="themeStore.config.theme.preset === 'light'" /><Moon v-else-if="themeStore.config.theme.preset === 'dark'" /><Monitor v-else /></el-icon>
          <span>主题：{{ presetLabel }}</span>
        </div>
        <div class="popover-item" @click="goCustomize">
          <el-icon><Setting /></el-icon>
          <span>自定义</span>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useThemeStore } from '@/stores/theme'
import { Sunny, Moon, Monitor, Setting } from '@element-plus/icons-vue'

const router = useRouter()
const themeStore = useThemeStore()
const showPopover = ref(false)

const presetLabel = computed(() => {
  const map = { auto: '自动', light: '浅色', dark: '深色' }
  return map[themeStore.config.theme.preset]
})

function handleCyclePreset() {
  themeStore.cyclePreset()
  showPopover.value = false
}

function goCustomize() {
  router.push('/customize')
  showPopover.value = false
}
</script>

<style scoped>
.theme-switcher {
  position: fixed;
  right: 190px;
  top: 14px;
  z-index: 1000;
  cursor: pointer;
}

.theme-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 6px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.12);
  transition: background 0.2s;
  color: var(--statusbar-text, #fff);
}

.theme-icon:hover {
  background: rgba(255, 255, 255, 0.25);
}

.popover-menu {
  position: absolute;
  top: 40px;
  right: 0;
  background: var(--dropdown-bg, #fff);
  border-radius: 8px;
  box-shadow: var(--dropdown-shadow, 0 4px 12px rgba(0,0,0,0.15));
  min-width: 140px;
  overflow: hidden;
  z-index: 1001;
}

.popover-item {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  cursor: pointer;
  transition: background-color 0.2s;
  color: var(--text-primary, #333);
  font-size: 14px;
}

.popover-item:hover {
  background: var(--dropdown-hover-bg, #f5f5f5);
}

.popover-item .el-icon {
  margin-right: 8px;
  font-size: 16px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

@media screen and (max-width: 768px) {
  .theme-switcher {
    right: 120px;
    top: 8px;
  }

  .theme-icon {
    padding: 4px;
  }

  .theme-icon svg {
    width: 16px;
    height: 16px;
  }
}
</style>

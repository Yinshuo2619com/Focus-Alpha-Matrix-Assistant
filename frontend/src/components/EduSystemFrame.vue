<template>
  <div class="edu-frame-container">
    <iframe
      ref="iframeRef"
      :src="proxyUrl"
      class="edu-iframe"
      @load="onIframeLoad"
    />
    <div v-if="loadError" class="edu-frame-error">
      <el-icon :size="40" color="#909399"><WarningFilled /></el-icon>
      <p>{{ loadError }}</p>
    </div>
    <div v-else-if="!loaded" class="edu-frame-loading">
      <el-icon class="is-loading" :size="24"><Loading /></el-icon>
      <span>教务系统加载中...</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { WarningFilled, Loading } from '@element-plus/icons-vue'

const props = defineProps<{ schoolId: string }>()

const iframeRef = ref<HTMLIFrameElement | null>(null)
const loaded = ref(false)
const loadError = ref('')
let loadTimer: ReturnType<typeof setTimeout> | null = null

const proxyUrl = computed(() => {
  const token = localStorage.getItem('token') || ''
  return `/api/edu-proxy/${props.schoolId}/student/login?token=${encodeURIComponent(token)}`
})

const emit = defineEmits<{
  (e: 'loaded'): void
  (e: 'error', msg: string): void
}>()

onMounted(() => {
  // 8 秒超时检测
  loadTimer = setTimeout(() => {
    if (!loaded.value) {
      loadError.value = '教务系统加载超时，请确认后端服务已启动'
      emit('error', loadError.value)
    }
  }, 8000)
})

onUnmounted(() => {
  if (loadTimer) clearTimeout(loadTimer)
})

const onIframeLoad = () => {
  if (loadTimer) clearTimeout(loadTimer)
  // 检查 iframe 内容是否为错误页
  try {
    const iframe = iframeRef.value
    const doc = iframe?.contentDocument
    if (doc && doc.body) {
      const text = doc.body.textContent || ''
      // 浏览器错误页通常包含这些关键词（避免过于宽泛的匹配如 "500"）
      if (text.includes('拒绝连接') || text.includes('ERR_CONNECTION') ||
          text.includes('refused to connect') || text.includes('502 Bad Gateway') ||
          text.includes('503 Service Unavailable') || text.includes('504 Gateway Timeout')) {
        loadError.value = '无法连接到教务系统，请确认后端服务已启动'
        emit('error', loadError.value)
        return
      }
    }
  } catch {
    // 跨域时无法访问 contentDocument，忽略
  }
  loaded.value = true
  emit('loaded')
}

const getIframe = () => iframeRef.value

defineExpose({ getIframe })
</script>

<style scoped>
.edu-frame-container {
  width: 100%;
  height: calc(100vh - 200px);
  max-height: 800px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  overflow: hidden;
  position: relative;
}

.edu-iframe {
  width: 100%;
  height: 100%;
  border: none;
  position: absolute;
  top: 0;
  left: 0;
}

.edu-frame-error,
.edu-frame-loading {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  background: #fafafa;
  color: var(--text-secondary);
  z-index: 1;
}

.edu-frame-error p {
  margin: 0;
  font-size: 14px;
}
</style>

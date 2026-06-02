<template>
  <div class="panel">
    <h3 class="panel-title">图标设置</h3>

    <div class="form-group">
      <label class="form-label">浏览器标签页图标 (Favicon)</label>
      <div class="upload-area" @click="triggerUpload">
        <img v-if="config.logo.favicon" :src="config.logo.favicon" class="preview-img" />
        <div v-else class="upload-placeholder">
          <el-icon size="20"><Plus /></el-icon>
          <span>上传图标</span>
        </div>
      </div>
      <el-button v-if="config.logo.favicon" size="small" @click="config.logo.favicon = ''; themeStore.saveTheme()">清除</el-button>
    </div>

    <input ref="fileInput" type="file" accept="image/*" style="display: none" @change="handleUpload" />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useThemeStore } from '@/stores/theme'
import request from '@/utils/request'

const themeStore = useThemeStore()
const config = themeStore.config
const fileInput = ref<HTMLInputElement>()

function triggerUpload() {
  fileInput.value?.click()
}

async function handleUpload(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  try {
    const formData = new FormData()
    formData.append('file', file)
    const res: any = await request.post('/theme/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    if (res.code === 200 && res.data) {
      config.logo.favicon = res.data
      themeStore.saveTheme()
      ElMessage.success('上传成功')
    }
  } catch {
    ElMessage.error('上传失败')
  }
  if (fileInput.value) fileInput.value.value = ''
}
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

.upload-area {
  width: 80px;
  height: 80px;
  border: 2px dashed var(--card-border);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: border-color 0.2s;
  overflow: hidden;

  &:hover {
    border-color: var(--accent);
  }
}

.preview-img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.upload-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  color: var(--text-placeholder);
  font-size: 12px;
}
</style>

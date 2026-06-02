<template>
  <div class="panel">
    <h3 class="panel-title">界面设置</h3>

    <div class="form-group">
      <label class="form-label">背景图片</label>
      <div class="upload-area" @click="triggerUpload">
        <img v-if="config.interface.bg_image" :src="config.interface.bg_image" class="preview-img" />
        <div v-else class="upload-placeholder">
          <el-icon size="24"><Plus /></el-icon>
          <span>点击上传背景图</span>
        </div>
      </div>
      <el-button v-if="config.interface.bg_image" size="small" @click="config.interface.bg_image = ''; themeStore.saveTheme()">清除</el-button>
    </div>

    <div class="form-group">
      <label class="form-label">背景图透明度 {{ config.interface.bg_image_opacity }}%</label>
      <el-slider v-model="config.interface.bg_image_opacity" :min="0" :max="100" @change="themeStore.saveTheme()" />
    </div>

    <div class="form-group">
      <label class="form-label">内容区透明度 {{ config.interface.content_opacity }}%</label>
      <el-slider v-model="config.interface.content_opacity" :min="0" :max="100" @change="themeStore.saveTheme()" />
    </div>

    <div class="form-group">
      <label class="form-label">卡片背景透明度 {{ config.interface.card_opacity }}%</label>
      <el-slider v-model="config.interface.card_opacity" :min="0" :max="100" @change="themeStore.saveTheme()" />
    </div>

    <div class="form-group">
      <label class="form-label">阴影颜色</label>
      <el-color-picker v-model="config.interface.shadow_color" @change="themeStore.saveTheme()" />
    </div>

    <div class="form-group">
      <label class="form-label">阴影透明度 {{ config.interface.shadow_opacity }}%</label>
      <el-slider v-model="config.interface.shadow_opacity" :min="0" :max="100" @change="themeStore.saveTheme()" />
    </div>

    <div class="form-group">
      <label class="form-label">容器宽度</label>
      <el-select v-model="config.interface.container_width" @change="themeStore.saveTheme()">
        <el-option label="100%" value="100%" />
        <el-option label="1200px" value="1200px" />
        <el-option label="960px" value="960px" />
        <el-option label="800px" value="800px" />
      </el-select>
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
      config.interface.bg_image = res.data
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
  width: 200px;
  height: 120px;
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
  object-fit: cover;
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

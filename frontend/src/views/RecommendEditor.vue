<template>
  <div class="editor-page">
    <StatusBar />
    <div class="status-bar-placeholder"></div>

    <div class="editor-container">
      <div class="editor-header">
        <el-button @click="$router.back()">
          <el-icon><ArrowLeft /></el-icon>
          返回
        </el-button>
        <h2>{{ isEdit ? (isTool ? '编辑工具' : '编辑推荐') : (isTool ? '发布工具' : '发布推荐') }}</h2>
        <div class="header-actions">
          <el-button v-if="isEdit" type="danger" @click="handleDelete">
            <el-icon><Delete /></el-icon>
            删除
          </el-button>
          <el-button @click="handleSaveDraft" :loading="savingDraft">
            <el-icon><Document /></el-icon>
            存为草稿
          </el-button>
          <el-button type="primary" @click="handleSubmit" :loading="submitting">
            {{ isEdit ? '保存修改' : '发布' }}
          </el-button>
        </div>
      </div>

      <!-- 元信息 -->
      <div class="meta-section">
        <div class="meta-row">
          <div class="meta-field">
            <label>标题 <span class="required">*</span></label>
            <el-input v-model="form.title" placeholder="请输入标题" maxlength="100" show-word-limit />
          </div>
        </div>
        <div class="meta-row">
          <div class="meta-field">
            <label>简介</label>
            <el-input v-model="form.summary" type="textarea" :rows="2" placeholder="一句话描述你的推荐内容" maxlength="500" show-word-limit />
          </div>
        </div>
        <div class="meta-row">
          <div class="meta-field">
            <label>封面图</label>
            <div class="cover-upload">
              <el-upload
                :show-file-list="false"
                :before-upload="handleCoverSelect"
                accept="image/*"
              >
                <div v-if="form.coverUrl" class="cover-preview">
                  <img :src="form.coverUrl" alt="封面" />
                  <div class="cover-remove" @click.stop="form.coverUrl = ''">
                    <el-icon><Close /></el-icon>
                  </div>
                </div>
                <div v-else class="cover-placeholder">
                  <el-icon><Plus /></el-icon>
                  <span>上传封面</span>
                </div>
              </el-upload>
            </div>
          </div>
          <div class="meta-field">
            <label>导入 MD 文件</label>
            <el-upload
              :show-file-list="false"
              :before-upload="handleMdUpload"
              accept=".md,.markdown,.txt"
            >
              <el-button>
                <el-icon><Upload /></el-icon>
                选择文件
              </el-button>
            </el-upload>
          </div>
        </div>
        <div v-if="isTool" class="meta-row">
          <div class="meta-field">
            <label>跳转卡片</label>
            <div class="redirect-toggle">
              <el-switch v-model="isRedirect" />
              <span class="redirect-hint">{{ isRedirect ? '点击卡片将直接跳转到链接地址' : '普通卡片，点击进入详情页' }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 跳转卡片 URL 输入 -->
      <div v-if="isRedirect" class="editor-body redirect-url-body">
        <div class="redirect-url-field">
          <label>跳转链接 <span class="required">*</span></label>
          <el-input v-model="form.content" placeholder="https://example.com" />
        </div>
      </div>

      <!-- MD 编辑器 + 预览 -->
      <div v-else class="editor-body">
        <div class="editor-pane">
          <div class="pane-header">
            <span>Markdown 编辑</span>
            <div class="editor-toolbar">
              <el-upload
                :show-file-list="false"
                :before-upload="handleImageUpload"
                accept="image/*"
              >
                <el-button size="small" text>
                  <el-icon><Picture /></el-icon>
                  插入图片
                </el-button>
              </el-upload>
            </div>
          </div>
          <textarea
            v-model="form.content"
            class="md-textarea"
            placeholder="在这里编写 Markdown 内容..."
            @scroll="syncScroll('editor')"
            @drop="handleDrop"
            @dragover.prevent
            ref="editorRef"
          ></textarea>
        </div>
        <div class="preview-pane">
          <div class="pane-header">实时预览</div>
          <div
            class="md-preview"
            v-html="renderedContent"
            @scroll="syncScroll('preview')"
            ref="previewRef"
          ></div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Plus, Upload, Close, Picture, Document, Delete } from '@element-plus/icons-vue'
import MarkdownIt from 'markdown-it'
import StatusBar from '@/components/StatusBar.vue'
import request from '@/utils/request'

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => !!route.params.id)
const isTool = computed(() => route.path.startsWith('/tool/'))
const apiBase = computed(() => isTool.value ? '/tools' : '/recommendations')
const submitting = ref(false)
const savingDraft = ref(false)

const form = ref({
  title: '',
  summary: '',
  content: '',
  coverUrl: '',
})
const isRedirect = ref(false)

const md = new MarkdownIt()
const renderedContent = computed(() => md.render(form.value.content || ''))

const editorRef = ref<HTMLTextAreaElement>()
const previewRef = ref<HTMLDivElement>()

let syncing = false
const syncScroll = (source: 'editor' | 'preview') => {
  if (syncing) return
  syncing = true
  if (source === 'editor' && editorRef.value && previewRef.value) {
    const ratio = editorRef.value.scrollTop / (editorRef.value.scrollHeight - editorRef.value.clientHeight)
    previewRef.value.scrollTop = ratio * (previewRef.value.scrollHeight - previewRef.value.clientHeight)
  } else if (source === 'preview' && editorRef.value && previewRef.value) {
    const ratio = previewRef.value.scrollTop / (previewRef.value.scrollHeight - previewRef.value.clientHeight)
    editorRef.value.scrollTop = ratio * (editorRef.value.scrollHeight - editorRef.value.clientHeight)
  }
  setTimeout(() => { syncing = false }, 50)
}

const handleMdUpload = (file: File) => {
  const reader = new FileReader()
  reader.onload = (e) => {
    form.value.content = e.target?.result as string || ''
    ElMessage.success('文件已导入')
  }
  reader.readAsText(file)
  return false
}

const handleCoverSelect = async (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('type', 'cover')
  try {
    ElMessage.info('封面上传中...')
    const res = await request.post('/cos/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    }) as any
    form.value.coverUrl = res.data
    ElMessage.success('封面上传成功')
  } catch (err: any) {
    ElMessage.error(err.message || '封面上传失败')
  }
  return false
}

const insertTextAtCursor = (text: string) => {
  const textarea = editorRef.value
  if (!textarea) return
  const start = textarea.selectionStart
  const end = textarea.selectionEnd
  const content = form.value.content
  form.value.content = content.substring(0, start) + text + content.substring(end)
  setTimeout(() => {
    textarea.focus()
    textarea.setSelectionRange(start + text.length, start + text.length)
  }, 0)
}

const handleImageUpload = async (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('type', 'image')
  try {
    ElMessage.info('图片上传中...')
    const res = await request.post('/cos/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    }) as any
    const url = res.data
    insertTextAtCursor(`![${file.name}](${url})`)
    ElMessage.success('图片插入成功')
  } catch (err: any) {
    ElMessage.error(err.message || '图片上传失败')
  }
  return false
}

const handleDrop = async (e: DragEvent) => {
  e.preventDefault()
  const files = e.dataTransfer?.files
  if (!files || files.length === 0) return
  const file = files[0]
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('只能拖拽图片文件')
    return
  }
  await handleImageUpload(file)
}

const handleSaveDraft = async () => {
  if (!form.value.title.trim()) {
    ElMessage.warning('请输入标题')
    return
  }
  savingDraft.value = true
  try {
    const draftData = { ...form.value, status: 0 }
    if (isEdit.value) {
      await request.put(`${apiBase.value}/${route.params.id}`, draftData)
      ElMessage.success('草稿已更新')
    } else {
      const res = await request.post(apiBase.value, draftData) as any
      const newId = res.data
      if (!newId) {
        ElMessage.error('保存失败：服务器未返回 ID')
        return
      }
      ElMessage.success('草稿已保存')
      const editPath = isTool.value ? `/tool/${newId}/edit` : `/recommend/${newId}/edit`
      await router.replace(editPath)
    }
  } catch (err: any) {
    ElMessage.error(err.message || '保存失败')
  } finally {
    savingDraft.value = false
  }
}

const handleSubmit = async () => {
  if (!form.value.title.trim()) {
    ElMessage.warning('请输入标题')
    return
  }
  if (!form.value.content.trim()) {
    ElMessage.warning('请输入内容')
    return
  }
  submitting.value = true
  try {
    const status = isRedirect.value ? 2 : 1
    const publishData = { ...form.value, status }
    if (isEdit.value) {
      await request.put(`${apiBase.value}/${route.params.id}`, publishData)
      ElMessage.success('修改成功')
    } else {
      await request.post(apiBase.value, publishData)
      ElMessage.success('发布成功')
    }
    router.push('/tools')
  } catch (err: any) {
    ElMessage.error(err.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

const handleDelete = async () => {
  try {
    const label = isTool.value ? '这个工具' : '这条推荐'
    await ElMessageBox.confirm(`确定删除${label}吗？`, '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await request.delete(`${apiBase.value}/${route.params.id}`)
    ElMessage.success('删除成功')
    router.push('/tools')
  } catch (err: any) {
    if (err !== 'cancel') {
      ElMessage.error(err.message || '删除失败')
    }
  }
}

const loadRecommendation = async (id: string) => {
  try {
    // 详情接口统一走 recommendations，后端不过滤 type
    const res = await request.get(`/recommendations/${id}`) as any
    const data = res.data
    form.value.title = data.title
    form.value.summary = data.summary || ''
    form.value.coverUrl = data.coverUrl || ''
    if (data.status === 2) {
      isRedirect.value = true
      form.value.content = data.contentUrl || ''
    } else if (data.contentUrl) {
      const contentRes = await fetch(data.contentUrl)
      form.value.content = await contentRes.text()
    }
  } catch (err: any) {
    ElMessage.error(isTool.value ? '加载工具内容失败' : '加载推荐内容失败')
    router.back()
  }
}

onMounted(() => {
  if (isEdit.value) {
    loadRecommendation(route.params.id as string)
  }
})

watch(() => route.params.id, (newId) => {
  if (newId) {
    loadRecommendation(newId as string)
  }
})
</script>

<style scoped lang="scss">
.editor-page {
  min-height: 100vh;
  background: #f5f7fa;
}

.status-bar-placeholder {
  height: 60px;
}

.editor-container {
  max-width: 1100px;
  margin: 0 auto;
  padding: 20px;
}

.editor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;

  h2 {
    margin: 0;
    font-size: 18px;
    color: #303133;
  }
}

.header-actions {
  display: flex;
  gap: 8px;
}

.meta-section {
  background: white;
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.meta-row {
  display: flex;
  gap: 20px;
  margin-bottom: 16px;

  &:last-child {
    margin-bottom: 0;
  }
}

.meta-field {
  flex: 1;

  label {
    display: block;
    font-size: 14px;
    color: #606266;
    margin-bottom: 6px;
    font-weight: 500;

    .required {
      color: #f56c6c;
    }
  }
}

.cover-upload {
  .cover-preview {
    width: 160px;
    height: 90px;
    border-radius: 8px;
    overflow: hidden;
    position: relative;

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }

    .cover-remove {
      position: absolute;
      top: 4px;
      right: 4px;
      width: 22px;
      height: 22px;
      background: rgba(0, 0, 0, 0.5);
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      color: white;
      font-size: 12px;
    }
  }

  .cover-placeholder {
    width: 160px;
    height: 90px;
    border: 1px dashed #dcdfe6;
    border-radius: 8px;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 4px;
    color: #909399;
    cursor: pointer;
    transition: border-color 0.2s;

    &:hover {
      border-color: #409eff;
      color: #409eff;
    }

    .el-icon {
      font-size: 24px;
    }

    span {
      font-size: 12px;
    }
  }
}

.redirect-toggle {
  display: flex;
  align-items: center;
  gap: 10px;
}

.redirect-hint {
  font-size: 13px;
  color: #909399;
}

.redirect-url-body {
  height: auto;
  padding: 20px;
}

.redirect-url-field {
  label {
    display: block;
    font-size: 14px;
    color: #606266;
    margin-bottom: 6px;
    font-weight: 500;

    .required {
      color: #f56c6c;
    }
  }
}

.editor-body {
  display: flex;
  gap: 0;
  background: white;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  height: 60vh;
}

.editor-pane,
.preview-pane {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.editor-pane {
  border-right: 1px solid #ebeef5;
}

.pane-header {
  padding: 10px 16px;
  font-size: 13px;
  font-weight: 600;
  color: #606266;
  background: #fafafa;
  border-bottom: 1px solid #ebeef5;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.editor-toolbar {
  display: flex;
  align-items: center;
  gap: 4px;
}

.md-textarea {
  flex: 1;
  border: none;
  outline: none;
  resize: none;
  padding: 16px;
  font-size: 14px;
  font-family: 'Courier New', Consolas, monospace;
  line-height: 1.6;
  color: #303133;
  background: #fff;
}

.md-preview {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
  font-size: 14px;
  line-height: 1.8;
  color: #303133;

  :deep(h1), :deep(h2), :deep(h3) {
    margin: 16px 0 8px;
    font-weight: 600;
  }

  :deep(h1) { font-size: 22px; }
  :deep(h2) { font-size: 18px; }
  :deep(h3) { font-size: 16px; }

  :deep(p) {
    margin: 8px 0;
  }

  :deep(code) {
    background: #f5f7fa;
    padding: 2px 6px;
    border-radius: 4px;
    font-size: 13px;
    font-family: 'Courier New', Consolas, monospace;
  }

  :deep(pre) {
    background: #f5f7fa;
    padding: 12px;
    border-radius: 8px;
    overflow-x: auto;

    code {
      background: none;
      padding: 0;
    }
  }

  :deep(blockquote) {
    border-left: 4px solid #409eff;
    padding-left: 12px;
    margin: 8px 0;
    color: #606266;
  }

  :deep(ul), :deep(ol) {
    padding-left: 24px;
    margin: 8px 0;
  }

  :deep(img) {
    max-width: 100%;
    border-radius: 8px;
  }

  :deep(table) {
    border-collapse: collapse;
    width: 100%;
    margin: 8px 0;

    th, td {
      border: 1px solid #ebeef5;
      padding: 8px 12px;
      text-align: left;
    }

    th {
      background: #f5f7fa;
      font-weight: 600;
    }
  }
}

@media screen and (max-width: 768px) {
  .editor-container {
    padding: 12px;
  }

  .meta-row {
    flex-direction: column;
    gap: 12px;
  }

  .editor-body {
    flex-direction: column;
    height: auto;
  }

  .editor-pane {
    border-right: none;
    border-bottom: 1px solid #ebeef5;
  }

  .md-textarea {
    min-height: 200px;
  }

  .md-preview {
    min-height: 200px;
  }
}
</style>

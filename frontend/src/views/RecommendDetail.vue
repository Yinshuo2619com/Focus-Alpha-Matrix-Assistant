<template>
  <div class="detail-page">
    <StatusBar />
    <div class="status-bar-placeholder"></div>

    <div class="detail-container">
      <div class="detail-header">
        <el-button @click="$router.push('/tools')">
          <el-icon><ArrowLeft /></el-icon>
          返回列表
        </el-button>
        <el-button v-if="store.token" @click="handleFavorite" :class="{ 'is-favorited': hasFavorited }">
          <el-icon><StarFilled v-if="hasFavorited" /><Star v-else /></el-icon>
          {{ hasFavorited ? '已收藏' : '收藏' }}
        </el-button>
      </div>

      <div v-if="loading" class="loading-wrapper">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>加载中...</span>
      </div>

      <template v-else-if="recommendation">
        <div class="detail-card">
          <div v-if="recommendation.coverUrl" class="detail-cover">
            <img :src="recommendation.coverUrl" alt="封面" />
          </div>

          <div class="detail-author-bar">
            <img class="author-avatar" :src="recommendation.authorAvatar || '/default-avatar.jpg'" />
            <div class="author-info">
              <span class="author-name">{{ recommendation.authorName }}</span>
              <span class="author-time">{{ formatTime(recommendation.createdAt) }}</span>
            </div>
          </div>

          <div class="detail-body">
            <h1 class="detail-title">{{ recommendation.title }}</h1>
            <p v-if="recommendation.summary" class="detail-summary">{{ recommendation.summary }}</p>

            <div class="detail-meta">
              <span class="meta-views">
                <el-icon><View /></el-icon>
                {{ recommendation.views }}
              </span>
              <span class="meta-favorites">
                <el-icon><Star /></el-icon>
                {{ recommendation.favorites }}
              </span>
            </div>

            <div class="detail-actions" v-if="isAuthor && route.query.mine">
              <el-button size="small" @click="$router.push(`/recommend/${recommendation.id}/edit`)">
                <el-icon><Edit /></el-icon>
                编辑
              </el-button>
              <el-button size="small" type="danger" @click="handleDelete">
                <el-icon><Delete /></el-icon>
                删除
              </el-button>
            </div>

            <el-divider />

            <div class="md-content" v-html="renderedContent"></div>
          </div>
        </div>

        <!-- 评论区 -->
        <div class="comment-section">
          <h3 class="comment-title">评论 ({{ totalCommentCount }})</h3>

          <!-- 评论输入框 -->
          <div v-if="store.token" class="comment-input-box">
            <el-input
              v-model="commentContent"
              type="textarea"
              :rows="3"
              placeholder="写下你的评论..."
              maxlength="1000"
              show-word-limit
            />
            <div class="comment-input-actions">
              <el-button type="primary" size="small" :disabled="!commentContent.trim()" @click="submitComment(null)">
                发表评论
              </el-button>
            </div>
          </div>
          <div v-else class="comment-login-hint">
            <span>登录后即可发表评论</span>
            <el-button type="primary" size="small" @click="$router.push('/login')">去登录</el-button>
          </div>

          <!-- 评论列表 -->
          <div v-if="commentLoading" class="comment-loading">
            <el-icon class="is-loading"><Loading /></el-icon>
            <span>加载评论中...</span>
          </div>
          <div v-else-if="commentTree.length === 0" class="comment-empty">
            暂无评论，快来发表第一条评论吧
          </div>
          <div v-else class="comment-list">
            <div v-for="comment in commentTree" :key="comment.id" :id="'comment-' + comment.id" class="comment-item">
              <div class="comment-main">
                <img class="comment-avatar" :src="comment.authorAvatar || '/default-avatar.jpg'" />
                <div class="comment-content-wrap">
                  <div class="comment-header">
                    <span class="comment-author">{{ comment.authorName }}</span>
                    <span class="comment-time">{{ formatTime(comment.createdAt) }}</span>
                  </div>
                  <div class="comment-text">{{ comment.content }}</div>
                  <div class="comment-actions">
                    <span class="action-btn" @click="handleCommentLike(comment)">
                      <svg v-if="comment.liked" viewBox="0 0 24 24" width="14" height="14" fill="#f56c6c"><path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/></svg>
                      <svg v-else viewBox="0 0 24 24" width="14" height="14" fill="#909399"><path d="M16.5 3c-1.74 0-3.41.81-4.5 2.09C10.91 3.81 9.24 3 7.5 3 4.42 3 2 5.42 2 8.5c0 3.78 3.4 6.86 8.55 11.54L12 21.35l1.45-1.32C18.6 15.36 22 12.28 22 8.5 22 5.42 19.58 3 16.5 3zm-4.4 15.55l-.1.1-.1-.1C7.14 14.24 4 11.39 4 8.5 4 6.5 5.5 5 7.5 5c1.54 0 3.04.99 3.57 2.36h1.87C13.46 5.99 14.96 5 16.5 5c2 0 3.5 1.5 3.5 3.5 0 2.89-3.14 5.74-7.9 10.05z"/></svg>
                      {{ comment.likes || '' }}
                    </span>
                    <span class="action-btn" @click="startReply(comment)">回复</span>
                    <span v-if="isCommentOwner(comment)" class="action-btn delete-btn" @click="handleDeleteComment(comment.id)">
                      删除
                    </span>
                  </div>

                  <!-- 回复输入框 -->
                  <div v-if="replyTarget?.id === comment.id" class="reply-input-box">
                    <el-input
                      v-model="replyContent"
                      type="textarea"
                      :rows="2"
                      :placeholder="`回复 ${comment.authorName}...`"
                      maxlength="1000"
                      show-word-limit
                    />
                    <div class="reply-input-actions">
                      <el-button size="small" @click="replyTarget = null">取消</el-button>
                      <el-button type="primary" size="small" :disabled="!replyContent.trim()" @click="submitComment(comment.id)">
                        回复
                      </el-button>
                    </div>
                  </div>

                  <!-- 子回复 -->
                  <div v-if="comment.children && comment.children.length > 0" class="replies">
                    <div v-for="reply in comment.children" :key="reply.id" class="comment-item reply-item">
                      <div class="comment-main">
                        <img class="comment-avatar small" :src="reply.authorAvatar || '/default-avatar.jpg'" />
                        <div class="comment-content-wrap">
                          <div class="comment-header">
                            <span class="comment-author">{{ reply.authorName }}</span>
                            <span class="comment-time">{{ formatTime(reply.createdAt) }}</span>
                          </div>
                          <div class="comment-text">{{ reply.content }}</div>
                          <div class="comment-actions">
                            <span class="action-btn" @click="handleCommentLike(reply)">
                              <svg v-if="reply.liked" viewBox="0 0 24 24" width="14" height="14" fill="#f56c6c"><path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/></svg>
                              <svg v-else viewBox="0 0 24 24" width="14" height="14" fill="#909399"><path d="M16.5 3c-1.74 0-3.41.81-4.5 2.09C10.91 3.81 9.24 3 7.5 3 4.42 3 2 5.42 2 8.5c0 3.78 3.4 6.86 8.55 11.54L12 21.35l1.45-1.32C18.6 15.36 22 12.28 22 8.5 22 5.42 19.58 3 16.5 3zm-4.4 15.55l-.1.1-.1-.1C7.14 14.24 4 11.39 4 8.5 4 6.5 5.5 5 7.5 5c1.54 0 3.04.99 3.57 2.36h1.87C13.46 5.99 14.96 5 16.5 5c2 0 3.5 1.5 3.5 3.5 0 2.89-3.14 5.74-7.9 10.05z"/></svg>
                              {{ reply.likes || '' }}
                            </span>
                            <span class="action-btn" @click="startReply(reply)">回复</span>
                            <span v-if="isCommentOwner(reply)" class="action-btn delete-btn" @click="handleDeleteComment(reply.id)">
                              删除
                            </span>
                          </div>

                          <!-- 回复输入框 -->
                          <div v-if="replyTarget?.id === reply.id" class="reply-input-box">
                            <el-input
                              v-model="replyContent"
                              type="textarea"
                              :rows="2"
                              :placeholder="`回复 ${reply.authorName}...`"
                              maxlength="1000"
                              show-word-limit
                            />
                            <div class="reply-input-actions">
                              <el-button size="small" @click="replyTarget = null">取消</el-button>
                              <el-button type="primary" size="small" :disabled="!replyContent.trim()" @click="submitComment(reply.id)">
                                回复
                              </el-button>
                            </div>
                          </div>

                          <!-- 递归渲染更深层回复 -->
                          <div v-if="reply.children && reply.children.length > 0" class="replies">
                            <div v-for="deepReply in flattenDeep(reply.children)" :key="deepReply.id" class="comment-item reply-item">
                              <div class="comment-main">
                                <img class="comment-avatar small" :src="deepReply.authorAvatar || '/default-avatar.jpg'" />
                                <div class="comment-content-wrap">
                                  <div class="comment-header">
                                    <span class="comment-author">{{ deepReply.authorName }}</span>
                                    <span class="comment-time">{{ formatTime(deepReply.createdAt) }}</span>
                                  </div>
                                  <div class="comment-text">{{ deepReply.content }}</div>
                                  <div class="comment-actions">
                                    <span class="action-btn" @click="handleCommentLike(deepReply)">
                                      <svg v-if="deepReply.liked" viewBox="0 0 24 24" width="14" height="14" fill="#f56c6c"><path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/></svg>
                                      <svg v-else viewBox="0 0 24 24" width="14" height="14" fill="#909399"><path d="M16.5 3c-1.74 0-3.41.81-4.5 2.09C10.91 3.81 9.24 3 7.5 3 4.42 3 2 5.42 2 8.5c0 3.78 3.4 6.86 8.55 11.54L12 21.35l1.45-1.32C18.6 15.36 22 12.28 22 8.5 22 5.42 19.58 3 16.5 3zm-4.4 15.55l-.1.1-.1-.1C7.14 14.24 4 11.39 4 8.5 4 6.5 5.5 5 7.5 5c1.54 0 3.04.99 3.57 2.36h1.87C13.46 5.99 14.96 5 16.5 5c2 0 3.5 1.5 3.5 3.5 0 2.89-3.14 5.74-7.9 10.05z"/></svg>
                                      {{ deepReply.likes || '' }}
                                    </span>
                                    <span class="action-btn" @click="startReply(deepReply)">回复</span>
                                    <span v-if="isCommentOwner(deepReply)" class="action-btn delete-btn" @click="handleDeleteComment(deepReply.id)">
                                      删除
                                    </span>
                                  </div>
                                  <div v-if="replyTarget?.id === deepReply.id" class="reply-input-box">
                                    <el-input v-model="replyContent" type="textarea" :rows="2"
                                      :placeholder="`回复 ${deepReply.authorName}...`" maxlength="1000" show-word-limit />
                                    <div class="reply-input-actions">
                                      <el-button size="small" @click="replyTarget = null">取消</el-button>
                                      <el-button type="primary" size="small" :disabled="!replyContent.trim()" @click="submitComment(deepReply.id)">回复</el-button>
                                    </div>
                                  </div>
                                </div>
                              </div>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </template>

      <div v-else class="empty-state">
        <el-empty description="推荐内容不存在或已被删除" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Loading, View, Star, StarFilled, Edit, Delete } from '@element-plus/icons-vue'
import MarkdownIt from 'markdown-it'
import StatusBar from '@/components/StatusBar.vue'
import request from '@/utils/request'

interface Recommendation {
  id: number
  userId: number
  authorName: string
  authorAvatar: string
  title: string
  summary: string
  coverUrl: string
  contentUrl: string
  views: number
  likes: number
  favorites: number
  createdAt: string
  updatedAt: string
}

const route = useRoute()
const router = useRouter()
const store = useUserStore()
const loading = ref(true)
const recommendation = ref<Recommendation | null>(null)
const mdContent = ref('')
const hasFavorited = ref(false)

// 评论相关
interface Comment {
  id: number
  userId: number
  recommendId: number
  parentId: number | null
  content: string
  likes: number
  liked: boolean
  createdAt: string
  authorName: string
  authorAvatar: string
  children?: Comment[]
}

const commentTree = ref<Comment[]>([])
const totalCommentCount = computed(() => {
  let count = 0
  const recurse = (list: Comment[]) => {
    for (const c of list) {
      count++
      if (c.children) recurse(c.children)
    }
  }
  recurse(commentTree.value)
  return count
})
const commentLoading = ref(false)
const commentContent = ref('')
const replyContent = ref('')
const replyTarget = ref<Comment | null>(null)

const md = new MarkdownIt()
const renderedContent = computed(() => md.render(mdContent.value || ''))

const isAuthor = computed(() => {
  return recommendation.value && store.userInfo?.id === recommendation.value.userId
})

const formatTime = (dateStr: string) => {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${month}-${day}`
}

const fetchDetail = async () => {
  loading.value = true
  try {
    const res = await request.get(`/recommendations/${route.params.id}`) as any
    recommendation.value = res.data

    // Fetch MD content from COS
    if (res.data.contentUrl) {
      const contentRes = await fetch(res.data.contentUrl)
      mdContent.value = await contentRes.text()
    }
  } catch (err: any) {
    ElMessage.error(err.message || '获取推荐详情失败')
  } finally {
    loading.value = false
  }
}

const fetchFavoriteStatus = async () => {
  if (!store.token) return
  try {
    const res = await request.get(`/recommendations/${route.params.id}/favorite-status`) as any
    hasFavorited.value = res.data === true
  } catch {
    // ignore
  }
}

const handleFavorite = async () => {
  try {
    if (hasFavorited.value) {
      await request.delete(`/recommendations/${route.params.id}/favorite`)
      hasFavorited.value = false
      if (recommendation.value) recommendation.value.favorites--
      ElMessage.success('已取消收藏')
    } else {
      await request.post(`/recommendations/${route.params.id}/favorite`)
      hasFavorited.value = true
      if (recommendation.value) recommendation.value.favorites++
      ElMessage.success('收藏成功')
    }
  } catch (err: any) {
    ElMessage.error(err.message || '操作失败')
  }
}

// ========== 评论功能 ==========

const fetchComments = async () => {
  commentLoading.value = true
  try {
    const res = await request.get(`/recommendations/${route.params.id}/comments`) as any
    const comments: Comment[] = res.data || []
    commentTree.value = buildCommentTree(comments)
  } catch {
    // ignore
  } finally {
    commentLoading.value = false
  }
}

const buildCommentTree = (comments: Comment[]): Comment[] => {
  const map = new Map<number, Comment>()
  const roots: Comment[] = []
  for (const c of comments) {
    c.children = []
    map.set(c.id, c)
  }
  for (const c of comments) {
    if (c.parentId && map.has(c.parentId)) {
      map.get(c.parentId)!.children!.push(c)
    } else {
      roots.push(c)
    }
  }
  return roots
}

const flattenDeep = (comments: Comment[]): Comment[] => {
  const result: Comment[] = []
  for (const c of comments) {
    result.push(c)
    if (c.children && c.children.length > 0) {
      result.push(...flattenDeep(c.children))
    }
  }
  return result
}

const isCommentOwner = (comment: Comment) => {
  return store.userInfo?.id === comment.userId
}

const startReply = (comment: Comment) => {
  replyTarget.value = comment
  replyContent.value = ''
}

const submitComment = async (parentId: number | null) => {
  const content = parentId ? replyContent.value.trim() : commentContent.value.trim()
  if (!content) return
  try {
    await request.post(`/recommendations/${route.params.id}/comments`, { parentId, content })
    ElMessage.success(parentId ? '回复成功' : '评论成功')
    if (parentId) {
      replyContent.value = ''
      replyTarget.value = null
    } else {
      commentContent.value = ''
    }
    await fetchComments()
  } catch (err: any) {
    ElMessage.error(err.message || '评论失败')
  }
}

const handleCommentLike = async (comment: Comment) => {
  try {
    if (comment.liked) {
      await request.delete(`/comments/${comment.id}/like`)
      comment.liked = false
      comment.likes = Math.max(0, (comment.likes || 1) - 1)
    } else {
      await request.post(`/comments/${comment.id}/like`)
      comment.liked = true
      comment.likes = (comment.likes || 0) + 1
    }
  } catch (err: any) {
    ElMessage.error(err.message || '操作失败')
  }
}

const handleDeleteComment = async (commentId: number) => {
  try {
    await ElMessageBox.confirm('确定删除这条评论吗？', '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await request.delete(`/comments/${commentId}`)
    ElMessage.success('评论已删除')
    await fetchComments()
  } catch (err: any) {
    if (err !== 'cancel') {
      ElMessage.error(err.message || '删除失败')
    }
  }
}

const handleDelete = async () => {
  try {
    await ElMessageBox.confirm('确定删除这条推荐吗？', '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await request.delete(`/recommendations/${route.params.id}`)
    ElMessage.success('删除成功')
    router.push('/tools')
  } catch (err: any) {
    if (err !== 'cancel') {
      ElMessage.error(err.message || '删除失败')
    }
  }
}

onMounted(async () => {
  fetchDetail()
  fetchFavoriteStatus()
  await fetchComments()

  const commentId = route.query.commentId
  if (commentId) {
    setTimeout(() => {
      const el = document.getElementById('comment-' + commentId)
      if (el) {
        el.scrollIntoView({ behavior: 'smooth', block: 'center' })
        el.classList.add('highlight-comment')
        setTimeout(() => el.classList.remove('highlight-comment'), 2000)
      }
    }, 100)
  }
})
</script>

<style scoped lang="scss">
.detail-page {
  min-height: 100vh;
  background: var(--page-bg);
}

.status-bar-placeholder {
  height: 60px;
}

.detail-container {
  max-width: 800px;
  margin: 0 auto;
  padding: 20px;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;

  .is-favorited {
    color: #e6a23c;
    border-color: #e6a23c;
  }
}

.loading-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 80px 0;
  color: var(--text-secondary);
}

.empty-state {
  padding: 60px 0;
}

.detail-card {
  background: var(--card-bg);
  border-radius: 12px;
  overflow: hidden;
  box-shadow: var(--card-shadow);
}

.detail-cover {
  width: 100%;
  max-height: 300px;
  overflow: hidden;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.detail-author-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 24px;
  border-bottom: 1px solid #ebeef5;

  .author-avatar {
    width: 40px;
    height: 40px;
    border-radius: 50%;
    object-fit: cover;
  }

  .author-info {
    display: flex;
    flex-direction: column;
    gap: 2px;

    .author-name {
      font-size: 14px;
      font-weight: 600;
      color: var(--text-primary);
    }

    .author-time {
      font-size: 12px;
      color: var(--text-secondary);
    }
  }
}

.detail-body {
  padding: 24px;
}

.detail-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 8px;
}

.detail-summary {
  font-size: 15px;
  color: var(--text-secondary);
  margin: 0 0 16px;
}

.detail-meta {
  display: flex;
  gap: 20px;
  font-size: 13px;
  color: var(--text-secondary);
  flex-wrap: wrap;

  span {
    display: flex;
    align-items: center;
    gap: 4px;
  }

  .meta-favorites {
    color: #e6a23c;
  }
}

.detail-actions {
  margin-top: 12px;
  display: flex;
  gap: 8px;
}

.md-content {
  font-size: 15px;
  line-height: 1.8;
  color: var(--text-primary);

  :deep(h1), :deep(h2), :deep(h3) {
    margin: 20px 0 10px;
    font-weight: 600;
  }

  :deep(h1) { font-size: 24px; }
  :deep(h2) { font-size: 20px; }
  :deep(h3) { font-size: 17px; }

  :deep(p) {
    margin: 10px 0;
  }

  :deep(code) {
    background: var(--page-bg);
    padding: 2px 6px;
    border-radius: 4px;
    font-size: 13px;
    font-family: 'Courier New', Consolas, monospace;
  }

  :deep(pre) {
    background: var(--page-bg);
    padding: 14px;
    border-radius: 8px;
    overflow-x: auto;
    margin: 12px 0;

    code {
      background: none;
      padding: 0;
    }
  }

  :deep(blockquote) {
    border-left: 4px solid var(--accent);
    padding-left: 14px;
    margin: 10px 0;
    color: var(--text-regular);
  }

  :deep(ul), :deep(ol) {
    padding-left: 24px;
    margin: 10px 0;
  }

  :deep(img) {
    max-width: 100%;
    border-radius: 8px;
    margin: 8px 0;
  }

  :deep(table) {
    border-collapse: collapse;
    width: 100%;
    margin: 10px 0;

    th, td {
      border: var(--card-border);
      padding: 8px 12px;
      text-align: left;
    }

    th {
      background: var(--page-bg);
      font-weight: 600;
    }
  }

  :deep(hr) {
    border: none;
    border-top: 1px solid #ebeef5;
    margin: 16px 0;
  }
}

/* 评论区样式 */
.comment-section {
  background: var(--card-bg);
  border-radius: 12px;
  padding: 24px;
  margin-top: 16px;
  box-shadow: var(--card-shadow);
}

.comment-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 20px;
}

.comment-input-box {
  margin-bottom: 24px;
}

.comment-input-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}

.comment-login-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 20px;
  color: var(--text-secondary);
  font-size: 14px;
  margin-bottom: 24px;
  background: var(--page-bg);
  border-radius: 8px;
}

.comment-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 30px 0;
  color: var(--text-secondary);
}

.comment-empty {
  text-align: center;
  padding: 30px 0;
  color: var(--text-secondary);
  font-size: 14px;
}

.comment-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.comment-item {
  &.reply-item {
    margin-top: 12px;
  }
}

.comment-main {
  display: flex;
  gap: 12px;
}

.comment-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;

  &.small {
    width: 32px;
    height: 32px;
  }
}

.comment-content-wrap {
  flex: 1;
  min-width: 0;
}

.comment-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 4px;
}

.comment-author {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.comment-time {
  font-size: 12px;
  color: var(--text-secondary);
}

.comment-text {
  font-size: 14px;
  color: var(--text-primary);
  line-height: 1.6;
  margin-bottom: 6px;
  word-break: break-word;
}

.comment-actions {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: var(--text-secondary);

  .action-btn {
    display: flex;
    align-items: center;
    gap: 3px;
    cursor: pointer;
    transition: color 0.2s;

    &:hover {
      color: var(--accent);
    }

  }

  .delete-btn:hover {
    color: #f56c6c;
  }
}

.reply-input-box {
  margin-top: 8px;
  margin-bottom: 8px;
}

.reply-input-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}

.replies {
  margin-left: 0;
  padding-left: 12px;
  border-left: 2px solid #ebeef5;
}

@media screen and (max-width: 768px) {
  .detail-container {
    padding: 12px;
  }

  .detail-body {
    padding: 16px;
  }

  .detail-title {
    font-size: 20px;
  }

  .detail-meta {
    gap: 12px;
  }
}

.highlight-comment {
  animation: highlight-fade 2s ease;
}

@keyframes highlight-fade {
  0% { background-color: #ecf5ff; }
  100% { background-color: transparent; }
}
</style>

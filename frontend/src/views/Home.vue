<template>
  <div class="home-page">
    <status-bar />
    <div class="status-bar-placeholder"></div>

    <div class="home-container">
      <div class="main-content">
        <div class="schedule-section">
          <div class="schedule-header">
            <h3 class="schedule-title">我的课表</h3>
            <div class="header-actions">
              <el-button v-if="scheduleStore.hasSchedule" size="small" @click="handleShare">
                <el-icon><Share /></el-icon>
                分享课表
              </el-button>
              <el-button v-if="scheduleStore.hasSchedule" size="small" @click="handleRefresh" :loading="refreshing">
                <el-icon><Refresh /></el-icon>
                刷新课表
              </el-button>
              <el-button type="primary" size="small" @click="goToImport">
                <el-icon><Plus /></el-icon>
                打开课程表
              </el-button>
            </div>
          </div>

          <div v-if="scheduleStore.loading" class="loading-wrapper">
            <el-icon class="is-loading"><Loading /></el-icon>
            <span>加载中...</span>
          </div>

          <ScheduleGrid
            v-else-if="scheduleStore.hasSchedule"
            :courses="scheduleStore.scheduleData.courses"
            :semester="scheduleStore.scheduleData.schedule?.semester"
            :start-date="scheduleStore.scheduleData.schedule?.startDate"
          />

          <div v-else class="empty-schedule">
            <el-empty description="暂无课表数据">
              <el-button type="primary" @click="goToImport">导入课表</el-button>
            </el-empty>
          </div>
        </div>

        <div class="side-cards">
          <ElectricityCard v-if="isLoggedIn" class="side-card" />
          <ExamCard v-if="isLoggedIn" class="side-card" />
        </div>
      </div>
    </div>

    <footer class="icp-footer">
      <a href="https://beian.miit.gov.cn" target="_blank" rel="noopener noreferrer">皖ICP备2026013845号-1</a>
      <span class="icp-divider">|</span>
      <a href="https://www.beian.gov.cn/portal/registerSystemInfo?recordcode=34012402000497" target="_blank" rel="noopener noreferrer">
        <img src="https://www.beian.gov.cn/img/new/gongan.png" alt="" class="icp-gongan-icon" />
        皖公网安备34012402000497号
      </a>
    </footer>

    <!-- 分享链接对话框 -->
    <el-dialog v-model="shareDialogVisible" title="分享课表" width="420px" center>
      <div class="share-dialog-content">
        <p class="share-hint">复制下方链接发送给好友即可分享你的课表：</p>
        <div class="share-link-box">
          <el-input v-model="shareUrl" readonly size="large">
            <template #append>
              <el-button @click="copyShareUrl">复制</el-button>
            </template>
          </el-input>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { Plus, Loading, Refresh, Share } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import StatusBar from '@/components/StatusBar.vue'
import ScheduleGrid from '@/components/ScheduleGrid.vue'
import ElectricityCard from '@/components/ElectricityCard.vue'
import ExamCard from '@/components/ExamCard.vue'
import { useScheduleStore } from '@/stores/schedule'
import { useUserStore } from '@/stores/user'

const scheduleStore = useScheduleStore()
const userStore = useUserStore()
const isLoggedIn = computed(() => !!userStore.token)
const refreshing = ref(false)
const shareDialogVisible = ref(false)
const shareUrl = ref('')

onMounted(() => {
  scheduleStore.fetchSchedule()
})

const goToImport = () => window.open('/schedule-import', '_blank')

const handleShare = async () => {
  try {
    const data = await scheduleStore.generateShareToken()
    shareUrl.value = `${window.location.origin}/schedule/share/${data.token}`
    shareDialogVisible.value = true
  } catch (err: any) {
    ElMessage.error(err.message || '生成分享链接失败')
  }
}

const copyShareUrl = async () => {
  try {
    await navigator.clipboard.writeText(shareUrl.value)
    ElMessage.success('链接已复制')
  } catch {
    // fallback
    const input = document.createElement('input')
    input.value = shareUrl.value
    document.body.appendChild(input)
    input.select()
    document.execCommand('copy')
    document.body.removeChild(input)
    ElMessage.success('链接已复制')
  }
}

const handleRefresh = async () => {
  refreshing.value = true
  try {
    const msg = await scheduleStore.refreshSchedule()
    ElMessage.success(msg)
  } catch (err: any) {
    if (err.message?.includes('未登录')) {
      ElMessage.warning('教务系统未登录，请先打开课表导入页面登录')
    } else {
      ElMessage.error(err.message || '刷新失败')
    }
  } finally {
    refreshing.value = false
  }
}
</script>

<style scoped lang="scss">
.home-page {
  min-height: 100vh;
  background: var(--page-bg);
  display: flex;
  flex-direction: column;
}

.status-bar-placeholder {
  height: 60px;
}

.home-container {
  width: 100%;
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
  flex: 1;
  box-sizing: border-box;
}

.main-content {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}

.side-cards {
  width: 280px;
  flex-shrink: 0;
  position: sticky;
  top: 80px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.side-card {
  width: 100%;
}

.schedule-section {
  background: var(--card-bg);
  border-radius: 12px;
  padding: 20px;
  box-shadow: var(--card-shadow);
  flex: 1;
  min-width: 0;
}

.schedule-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.schedule-title {
  margin: 0;
  font-size: 18px;
  color: var(--text-primary);
}

.loading-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 40px 0;
  color: var(--text-secondary);
}

.empty-schedule {
  padding: 20px 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 200px;
}

.icp-footer {
  text-align: center;
  padding: 16px 0;
  font-size: 13px;
  color: var(--text-secondary);

  a {
    color: var(--text-secondary);
    text-decoration: none;
    margin: 0 12px;

    &:hover {
      color: var(--accent);
    }
  }

  .icp-divider {
    color: var(--text-placeholder);
  }

  .icp-gongan-icon {
    vertical-align: middle;
    margin-right: 4px;
    height: 14px;
  }
}

.share-dialog-content {
  text-align: center;
}

.share-hint {
  color: var(--text-regular);
  font-size: 14px;
  margin-bottom: 16px;
}

.share-link-box {
  margin-top: 8px;
}

@media screen and (max-width: 768px) {
  .home-container {
    padding: 12px;
  }

  .main-content {
    flex-direction: column;
    align-items: stretch;
  }

  .schedule-section {
    width: 100%;
  }

  .side-cards {
    width: 100%;
    position: static;

    // 手机端：考试卡片排在电费卡片上面
    .side-card:first-child {
      order: 1;
    }

    .side-card:last-child {
      order: -1;
    }
  }

  .side-card {
    width: 100%;
  }

  .header-actions {
    flex-wrap: wrap;
  }
}
</style>

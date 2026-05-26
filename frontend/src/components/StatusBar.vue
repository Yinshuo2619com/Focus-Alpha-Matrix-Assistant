<template>
    <div class="sticky-header">
      <div class="nav-menu" @mouseenter="showNavMenu = true" @mouseleave="showNavMenu = false">
        <el-icon class="hamburger-icon"><Operation /></el-icon>
        <span v-if="unreadCount > 0" class="nav-badge"></span>
        <transition name="fade">
          <div v-show="showNavMenu" class="dropdown-menu nav-dropdown">
            <div class="menu-item" @click="goTo('/home')">
              <el-icon><HomeFilled /></el-icon>
              <span>主页</span>
            </div>
            <div class="menu-item" @click="goTo('/tools?from=nav')">
              <el-icon><Grid /></el-icon>
              <span>工具</span>
            </div>
            <div v-if="isLoggedIn" class="menu-item" @click="goTo('/notifications')">
              <el-icon><Bell /></el-icon>
              <span>消息</span>
              <span v-if="unreadCount > 0" class="menu-badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
            </div>
          </div>
        </transition>
      </div>
      <div class="header-center">
        <div class="date-line">{{ currentDate }}</div>
        <div class="weekday-line">{{ currentWeekday }}</div>
        <div class="time-line">{{ currentTime }}</div>
      </div>
    </div>
    <div class="user-info" @mouseenter="showMenu = true" @mouseleave="showMenu = false">
        <span class="username">{{ store.userInfo?.nickname || '用户' }}</span>
        <img :src="store.userAvatar" alt="用户头像" class="avatar-img" />
        <transition name="fade">
          <div v-show="showMenu" class="dropdown-menu">
            <template v-if="isLoggedIn">
              <div class="menu-item" @click="goToProfile">
                <el-icon><User /></el-icon>
                <span>个人中心</span>
              </div>
              <div class="menu-item" @click="goTo('/favorites')">
                <el-icon><Star /></el-icon>
                <span>我的收藏</span>
              </div>
              <div v-if="store.isAdmin" class="menu-item" @click="goToUserManagement">
                <el-icon><User /></el-icon>
                <span>用户管理</span>
              </div>
              <div class="menu-item" @click="handleLogout">
                <el-icon><SwitchButton /></el-icon>
                <span>退出登录</span>
              </div>
            </template>
            <template v-else>
              <div class="menu-item" @click="handleLogin">
                <el-icon><UserFilled /></el-icon>
                <span>登录</span>
              </div>
              <div class="menu-item" @click="handleRegister">
                <el-icon><User /></el-icon>
                <span>注册</span>
              </div>
            </template>
          </div>
        </transition>
    </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { User, SwitchButton, UserFilled, Operation, HomeFilled, Grid, Star, Bell } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useNotification } from '@/composables/useNotification'


defineOptions({
  name: 'StatusBar'
})


const router = useRouter()
const store = useUserStore()
const showMenu = ref(false)
const showNavMenu = ref(false)
const { unreadCount, connect, fetchUnreadCount } = useNotification()

const goTo = (path: string) => {
  router.push(path)
  showNavMenu.value = false
}

// 判断是否登录
const isLoggedIn = computed(() => !!store.token)

const currentDate = ref('')
const currentWeekday = ref('')
const currentTime = ref('')
const weekdays = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

const goToProfile = () => {
  router.push('/profile')
  showMenu.value = false
}

const handleLogout = () => {
  store.logout()
  ElMessage.success('已退出登录')
  router.push('/home')
  showMenu.value = false
}

const handleLogin = () => {
  showMenu.value = false
  router.push('/login')
}

const handleRegister = () => {
  showMenu.value = false
  router.push('/register')
}

const goToUserManagement = () => {
  // 跳转到用户管理页面
  router.push('/admin/users')
  showMenu.value = false
}

let animationFrameId: number | null = null

// 更新时间显示
const updateTime = () => {
  const now = new Date()

  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  currentDate.value = `${year}年${month}月${day}日`

  currentWeekday.value = weekdays[now.getDay()]

  const hours = String(now.getHours()).padStart(2, '0')
  const minutes = String(now.getMinutes()).padStart(2, '0')
  const seconds = String(now.getSeconds()).padStart(2, '0')
  currentTime.value = `${hours}:${minutes}:${seconds}`

  animationFrameId = requestAnimationFrame(updateTime)
}

onMounted(() => {
  updateTime()
  if (isLoggedIn.value) {
    fetchUnreadCount()
    connect()
  }
})
onUnmounted(() => {
  if (animationFrameId) cancelAnimationFrame(animationFrameId)
})
</script>

<style scoped>
.sticky-header {
  top: 0;
  left: 0;
  width: 100%;
  position: fixed;       
  height: 60px;
  background-color: #b7a091;
  color: white;
  line-height: 60px;
  text-align: center;
  z-index: 999;
}

.header-center {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  line-height: 1.2;
}

.date-line {
  font-size: 12px;
  opacity: 0.9;
}

.weekday-line {
  font-size: 13px;
  font-weight: bold;
}

.time-line {
  font-size: 16px;
  font-weight: bold;
  font-family: 'Courier New', monospace;
}

.nav-menu {
  position: fixed;
  left: 20px;
  top: 10px;
  z-index: 1000;
  cursor: pointer;
}

.hamburger-icon {
  color: white;
  font-size: 28px;
  transition: transform 0.2s;
}

.nav-badge {
  position: absolute;
  top: -2px;
  right: -4px;
  width: 10px;
  height: 10px;
  background: #f56c6c;
  border-radius: 50%;
  border: 2px solid #b7a091;
}

.menu-badge {
  margin-left: auto;
  background: #f56c6c;
  color: white;
  font-size: 10px;
  min-width: 18px;
  height: 18px;
  line-height: 18px;
  border-radius: 9px;
  text-align: center;
  padding: 0 5px;
}

.hamburger-icon:hover {
  transform: scale(1.1);
}

.nav-dropdown {
  left: 0;
  right: auto;
}

.user-info {
  position: fixed;
  right: 20px;
  top: 10px;
  z-index: 1000;
  display: flex;
  align-items: center;
  gap: 10px;
}

.username {
  color: white;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.avatar-img {
  width: 45px;
  height: 45px;
  object-fit: cover;
  cursor: pointer;
  border-radius: 50%;
  transition: transform 0.2s;
}

.avatar-img:hover {
  transform: scale(1.05);
}

.dropdown-menu {
  position: absolute;
  top: 50px;
  right: 0;
  background: white;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  min-width: 140px;
  overflow: hidden;
  z-index: 1001;
}

.menu-item {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  cursor: pointer;
  transition: background-color 0.2s;
  color: #333;
  font-size: 14px;
}

.menu-item:hover {
  background-color: #f5f5f5;
}

.menu-item .el-icon {
  margin-right: 8px;
  font-size: 16px;
}

/* 过渡动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}
</style>

<template>
  <div class="login-container">
    <div class="login-wrapper">
      <!-- 人物装饰（仅眼睛跟随，无对话框） -->
      <EyeCharacter />

      <el-card class="login-card">
        <template #header>
          <h2 class="login-title">登录</h2>
        </template>

        <el-form
          ref="loginFormRef"
          :model="loginForm"
          :rules="loginRules"
          label-width="0"
        >
          <el-form-item prop="username">
            <el-input
              v-model="loginForm.username"
              prefix-icon="User"
              placeholder="请输入用户名/手机号"
              size="large"
            />
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              v-model="loginForm.password"
              prefix-icon="Lock"
              type="password"
              placeholder="请输入密码"
              size="large"
              show-password
              @keyup.enter="handleLogin"
            />
          </el-form-item>

          <el-form-item>
            <el-button
              type="primary"
              size="large"
              class="login-btn"
              :loading="loading"
              @click="handleLogin"
            >
              登录
            </el-button>
          </el-form-item>

          <el-form-item>
            <div class="register-link">
              还没有账号？
              <el-button type="primary" link @click="goToRegister">立即注册</el-button>
            </div>
          </el-form-item>

          <el-form-item>
            <div class="forgot-password">
              <el-button type="primary" link @click="handleForgotPassword">忘记密码？</el-button>
            </div>
          </el-form-item>
        </el-form>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { useScheduleStore } from '@/stores/schedule'
import EyeCharacter from '@/components/EyeCharacter.vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const scheduleStore = useScheduleStore()

const loginFormRef = ref<FormInstance>()
const loading = ref(false)

const loginForm = reactive({
  username: '',
  password: ''
})

const loginRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ]
}

const handleLogin = async () => {
  const valid = await loginFormRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const res: any = await userStore.login(loginForm.username, loginForm.password)
    if (res.code === 200) {
      ElMessage.success('登录成功')
      // 检查是否有待导入的分享课表
      const pendingToken = sessionStorage.getItem('pendingImportToken')
      if (pendingToken) {
        sessionStorage.removeItem('pendingImportToken')
        try {
          await scheduleStore.importSharedSchedule(pendingToken)
          ElMessage.success('课表导入成功')
        } catch {
          // 导入失败不阻塞跳转
        }
        router.push('/home')
      } else {
        const redirect = route.query.redirect as string
        router.push(redirect || '/home')
      }
    } else {
      ElMessage.error(res.message || '登录失败')
    }
  } catch (error: any) {
    ElMessage.error(error.message || '登录失败')
  } finally {
    loading.value = false
  }
}

const goToRegister = () => {
  router.push('/register')
}

const handleForgotPassword = () => {
  ElMessageBox.alert(
    '请联系管理员重置密码：admin@example.com',
    '忘记密码',
    {
      confirmButtonText: '我知道了',
      type: 'info'
    }
  )
}
</script>

<style scoped lang="scss">
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: rgb(183, 160, 145);
  padding: 20px;
  box-sizing: border-box;
}

.login-wrapper {
  display: flex;
  align-items: flex-end;
  gap: 20px;
}

.login-card {
  width: 400px;
  border-radius: 10px;
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.15);
}

.login-title {
  text-align: center;
  font-size: 24px;
  color: #303133;
  margin: 0;
}

.login-btn {
  width: 100%;
}

.register-link {
  text-align: center;
  width: 100%;
}

.forgot-password {
  text-align: center;
  width: 100%;
  margin-top: -10px;
}

// 移动端适配
@media screen and (max-width: 768px) {
  .login-container {
    padding: 15px;
  }

  .login-wrapper {
    flex-direction: column;
    align-items: center;
  }

  .login-card {
    width: 100%;
    max-width: 400px;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  }

  .login-title {
    font-size: 22px;
  }
}

// 小屏幕手机适配
@media screen and (max-width: 375px) {
  .login-container {
    padding: 10px;
  }

  .login-card {
    max-width: 100%;
    border-radius: 8px;
  }

  .login-title {
    font-size: 20px;
  }
}
</style>

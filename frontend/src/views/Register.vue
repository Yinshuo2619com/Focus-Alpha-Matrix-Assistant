<template>
  <div class="register-container">
    <div class="register-wrapper">
      <!-- 人物装饰（带对话框） -->
      <EyeCharacter
        :message="characterMessage"
        :message-type="characterMessageType"
      />

      <el-card class="register-card">
        <template #header>
          <h2 class="register-title">注册账号</h2>
        </template>

        <el-form
          ref="registerFormRef"
          :model="registerForm"
          :rules="registerRules"
          label-width="0"
        >
          <el-form-item prop="username">
            <el-input
              v-model="registerForm.username"
              prefix-icon="User"
              placeholder="请输入用户名"
              size="large"
              @focus="handleFocus('username')"
              @blur="handleBlur"
            />
          </el-form-item>

          <el-form-item prop="nickname">
            <el-input
              v-model="registerForm.nickname"
              prefix-icon="User"
              placeholder="请输入昵称"
              size="large"
              @focus="handleFocus('nickname')"
              @blur="handleBlur"
            />
          </el-form-item>

          <el-form-item prop="phone">
            <el-input
              v-model="registerForm.phone"
              prefix-icon="Phone"
              placeholder="请输入手机号"
              size="large"
              @focus="handleFocus('phone')"
              @blur="handleBlur"
            />
          </el-form-item>

          <el-form-item prop="email">
            <el-input
              v-model="registerForm.email"
              prefix-icon="Mail"
              placeholder="请输入邮箱（可选）"
              size="large"
              @focus="handleFocus('email')"
              @blur="handleBlur"
            />
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              v-model="registerForm.password"
              prefix-icon="Lock"
              type="password"
              placeholder="至少6位，含大小写字母、数字、特殊字符"
              @input="onPasswordInput"
              size="large"
              show-password
              @focus="handleFocus('password')"
              @blur="handleBlur"
            />
          </el-form-item>

          <el-form-item prop="confirmPassword">
            <el-input
              v-model="registerForm.confirmPassword"
              prefix-icon="Lock"
              type="password"
              placeholder="请确认密码"
              size="large"
              show-password
              @keyup.enter="handleRegister"
              @focus="handleFocus('confirmPassword')"
              @blur="handleBlur"
            />
          </el-form-item>

          <el-form-item>
            <el-button
              type="primary"
              size="large"
              class="register-btn"
              :loading="loading"
              @click="handleRegister"
            >
              注册
            </el-button>
          </el-form-item>

          <el-form-item>
            <div class="login-link">
              已有账号？
              <el-button type="primary" link @click="goToLogin">立即登录</el-button>
            </div>
          </el-form-item>
        </el-form>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import request from '../utils/request'
import EyeCharacter from '@/components/EyeCharacter.vue'

const router = useRouter()

const registerFormRef = ref<FormInstance>()
const loading = ref(false)

// 人物对话框消息
const characterMessage = ref('嗨！欢迎注册，填写以下信息加入我们吧~')
const characterMessageType = ref<'info' | 'success' | 'error' | 'warning'>('info')

const registerForm = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  nickname: '',
  phone: '',
  email: ''
})

const showMessage = (msg: string, type: 'info' | 'success' | 'error' | 'warning') => {
  characterMessage.value = msg
  characterMessageType.value = type
}

// 字段提示信息
const fieldTips: Record<string, string> = {
  username: '请输入3-20个字符的用户名',
  nickname: '给自己取个好听的昵称吧',
  phone: '请输入11位手机号码',
  email: '邮箱是可选的，但有助于找回密码',
  password: '密码需要6位以上，含大小写字母、数字和特殊字符',
  confirmPassword: '再输入一次密码确认'
}

const handleFocus = (field: string) => {
  showMessage(fieldTips[field] || '', 'info')
}

const handleBlur = () => {
  characterMessage.value = '嗨！欢迎注册，填写以下信息加入我们吧~'
  characterMessageType.value = 'info'
}

const filterPassword = () => {
  registerForm.password = registerForm.password.replace(/[^A-Za-z0-9!@#$%^&*()_+\-=\[\]{}|;:'",.<>?\/`~\\]/g, '')
}

// 密码实时反馈
const getPasswordFeedback = (pwd: string): string => {
  if (!pwd) return '密码需要6位以上，含大小写字母、数字和特殊字符'

  const missing: string[] = []
  if (pwd.length < 6) missing.push('至少6位')
  if (!/[A-Z]/.test(pwd)) missing.push('大写字母')
  if (!/[a-z]/.test(pwd)) missing.push('小写字母')
  if (!/[0-9]/.test(pwd)) missing.push('数字')
  if (!/[^A-Za-z0-9]/.test(pwd)) missing.push('特殊字符')

  if (missing.length === 0) return '密码强度合格！'
  return '还缺少：' + missing.join('、')
}

const onPasswordInput = () => {
  filterPassword()
  const feedback = getPasswordFeedback(registerForm.password)
  showMessage(feedback, registerForm.password && feedback === '密码强度合格！' ? 'success' : 'info')
}

const validateConfirmPassword = (_rule: any, value: any, callback: any) => {
  if (value === '') {
    callback(new Error('请确认密码'))
  } else if (value !== registerForm.password) {
    callback(new Error('两次输入密码不一致'))
  } else {
    callback()
  }
}

const registerRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度为3-20个字符', trigger: 'blur' }
  ],
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { min: 1, max: 20, message: '昵称长度为1-20个字符', trigger: 'blur' }
  ],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ],
  email: [
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

const handleRegister = async () => {
  const valid = await registerFormRef.value?.validate().catch(() => false)
  if (!valid) {
    // 找到第一个验证失败的字段并显示提示
    if (!registerForm.username) {
      showMessage('请输入用户名', 'warning')
    } else if (registerForm.username.length < 3 || registerForm.username.length > 20) {
      showMessage('用户名长度为3-20个字符', 'warning')
    } else if (!registerForm.nickname) {
      showMessage('请输入昵称', 'warning')
    } else if (!registerForm.phone) {
      showMessage('请输入手机号', 'warning')
    } else if (!/^1[3-9]\d{9}$/.test(registerForm.phone)) {
      showMessage('请输入正确的手机号', 'warning')
    } else if (registerForm.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(registerForm.email)) {
      showMessage('请输入正确的邮箱地址', 'warning')
    } else if (!registerForm.password) {
      showMessage('请输入密码', 'warning')
    } else if (!registerForm.confirmPassword) {
      showMessage('请确认密码', 'warning')
    } else if (registerForm.confirmPassword !== registerForm.password) {
      showMessage('两次输入密码不一致', 'warning')
    }
    return
  }

  loading.value = true
  try {
    await request.post('/auth/register', {
      username: registerForm.username,
      password: registerForm.password,
      nickname: registerForm.nickname,
      phone: registerForm.phone,
      email: registerForm.email
    })

    showMessage('注册成功！正在跳转到登录页...', 'success')
    setTimeout(() => router.push('/login'), 1000)
  } catch (error: any) {
    showMessage(error.message || '注册失败，请重试', 'error')
  } finally {
    loading.value = false
  }
}

const goToLogin = () => {
  router.push('/login')
}
</script>

<style scoped lang="scss">
.register-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: rgb(183, 160, 145);
  padding: 20px;
  box-sizing: border-box;
}

.register-wrapper {
  display: flex;
  align-items: flex-end;
  gap: 20px;
}

.register-card {
  width: 400px;
  border-radius: 10px;
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.15);
}

.register-title {
  text-align: center;
  font-size: 24px;
  color: #303133;
  margin: 0;
}

.register-btn {
  width: 100%;
}

.login-link {
  text-align: center;
  width: 100%;
}

// 移动端适配
@media screen and (max-width: 768px) {
  .register-container {
    padding: 15px;
  }

  .register-wrapper {
    flex-direction: column-reverse;
    align-items: center;
  }

  .register-card {
    width: 100%;
    max-width: 400px;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  }

  .register-title {
    font-size: 22px;
  }
}

// 小屏幕手机适配
@media screen and (max-width: 375px) {
  .register-container {
    padding: 10px;
  }

  .register-card {
    max-width: 100%;
    border-radius: 8px;
  }

  .register-title {
    font-size: 20px;
  }
}
</style>

<template>
  <div class="profile-page">
    <status-bar />
    <div class="status-bar-placeholder"></div>

    <div class="profile-wrapper">
      <!-- 人物装饰 -->
      <EyeCharacter
        :message="characterMessage"
        :message-type="characterMessageType"
      />

      <div class="profile-container">
        <el-button class="back-btn" @click="router.back()">
          <el-icon><ArrowLeft /></el-icon>
          返回
        </el-button>

        <el-card class="profile-card">
          <template #header>
            <div class="card-header">
              <span>个人中心</span>
            </div>
          </template>

          <!-- 头像区域 -->
          <div class="avatar-section">
            <div class="avatar-wrapper">
              <img
                :src="userStore.userAvatar"
                alt="用户头像"
                class="user-avatar"
              />

              <el-upload
                class="avatar-uploader"
                :show-file-list="false"
                :on-success="handleUploadSuccess"
                :on-error="handleUploadError"
                :before-upload="beforeAvatarUpload"
                action="/api/user/avatar"
                name="file"
                :headers="uploadHeaders"
              >
                <div class="upload-mask">
                  <el-icon class="upload-icon"><Camera /></el-icon>
                  <div class="upload-text">更换头像</div>
                </div>
              </el-upload>
            </div>

            <div class="upload-hint">
              <p>支持 JPG、PNG、GIF 格式，最大 20MB</p>
              <p>每日最多可上传 3 次</p>
            </div>
          </div>

          <el-divider />

          <!-- 用户信息 -->
          <div class="user-info">
            <el-descriptions :column="2" border>
              <el-descriptions-item label="用户名">
                {{ userStore.userInfo?.username }}
              </el-descriptions-item>
              <el-descriptions-item label="昵称">
                {{ userStore.userInfo?.nickname }}
              </el-descriptions-item>
              <el-descriptions-item label="邮箱">
                {{ userStore.userInfo?.email || '未填写' }}
              </el-descriptions-item>
              <el-descriptions-item label="手机号">
                {{ userStore.userInfo?.phone }}
              </el-descriptions-item>
              <el-descriptions-item label="生日">
                {{ userStore.userInfo?.birthday || '未填写' }}
              </el-descriptions-item>
              <el-descriptions-item label="性别">
                {{ userStore.userInfo?.gender || '未填写' }}
              </el-descriptions-item>
              <el-descriptions-item label="注册时间">
                {{ userStore.userInfo?.createdAt }}
              </el-descriptions-item>
              <el-descriptions-item label="角色">
                <el-tag :type="userStore.userInfo?.role === 'ADMIN' ? 'danger' : 'primary'">
                  {{ userStore.userInfo?.role === 'ADMIN' ? '管理员' : '普通用户' }}
                </el-tag>
              </el-descriptions-item>
            </el-descriptions>
          </div>

          <el-divider />

          <!-- 宿舍绑定 -->
          <div class="dorm-section">
            <h3 class="section-title">宿舍绑定</h3>

            <div v-if="userStore.userInfo?.roomId" class="current-bind">
              <span class="bind-label">当前绑定：</span>
              <span class="bind-value">{{ userStore.userInfo.roomName }}</span>
              <el-button type="danger" size="small" text :loading="unbindLoading" @click="handleUnbind">解绑</el-button>
            </div>

            <div class="bind-form">
              <div class="bind-form-row">
                <el-select
                  v-model="bindForm.buiId"
                  placeholder="选择楼栋"
                  style="width: 180px"
                  @change="onBuildingChange"
                  filterable
                >
                  <el-option
                    v-for="b in buildings"
                    :key="b.buiId"
                    :label="b.name"
                    :value="b.buiId"
                  />
                </el-select>
                <el-select
                  v-model="bindForm.roomId"
                  placeholder="选择房间"
                  style="width: 180px"
                  :disabled="!bindForm.buiId"
                  :loading="roomsLoading"
                  filterable
                >
                  <el-option
                    v-for="r in rooms"
                    :key="r.roomId"
                    :label="r.roomName"
                    :value="r.roomId"
                  />
                </el-select>
                <el-button type="primary" :loading="bindLoading" :disabled="!bindForm.roomId" @click="handleBind">绑定</el-button>
              </div>
            </div>
          </div>

          <el-divider />

          <!-- 操作按钮 -->
          <div class="action-buttons">
            <el-button type="primary" @click="openEditDialog">编辑资料</el-button>
            <el-button type="warning" @click="openPasswordDialog">修改密码</el-button>
            <el-button type="danger" @click="handleLogout">退出登录</el-button>
          </div>
        </el-card>
      </div>
    </div>

    <!-- 编辑资料弹窗 -->
    <el-dialog v-model="editDialogVisible" title="编辑资料" width="480px" destroy-on-close>
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="70px">
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="editForm.username"
            placeholder="请输入用户名"
            @blur="checkUsername"
          />
          <div v-if="usernameCheckMsg" :class="['check-msg', usernameCheckOk ? 'ok' : 'fail']">
            {{ usernameCheckMsg }}
          </div>
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="editForm.nickname" placeholder="请输入昵称" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input
            v-model="editForm.phone"
            placeholder="请输入手机号"
            @blur="checkPhone"
          />
          <div v-if="phoneCheckMsg" :class="['check-msg', phoneCheckOk ? 'ok' : 'fail']">
            {{ phoneCheckMsg }}
          </div>
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="editForm.email" placeholder="可选" />
        </el-form-item>
        <el-form-item label="生日">
          <el-date-picker
            v-model="editForm.birthday"
            type="date"
            placeholder="可选"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="editForm.gender">
            <el-radio label="男">男</el-radio>
            <el-radio label="女">女</el-radio>
            <el-radio label="保密">保密</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="editLoading" @click="submitEditForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 修改密码弹窗 -->
    <el-dialog v-model="passwordDialogVisible" title="修改密码" width="450px" destroy-on-close>
      <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-width="90px">
        <el-form-item label="旧密码" prop="oldPassword">
          <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="请输入旧密码" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="至少6位，含大小写字母、数字、特殊字符" @input="filterPassword" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="passwordLoading" @click="submitPasswordForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Camera, ArrowLeft } from '@element-plus/icons-vue'
import StatusBar from '@/components/StatusBar.vue'
import EyeCharacter from '@/components/EyeCharacter.vue'
import router from '@/router'
import request from '@/utils/request'

const userStore = useUserStore()

// ========== 宿舍绑定 ==========
const buildings = ref<{ buiId: number; name: string }[]>([])
const rooms = ref<{ roomId: number; roomName: string }[]>([])
const roomsLoading = ref(false)
const bindLoading = ref(false)
const unbindLoading = ref(false)
const bindForm = reactive({ buiId: null as number | null, roomId: null as number | null })

const fetchBuildings = async () => {
  try {
    const res: any = await request.get('/electricity/buildings')
    if (res.code === 200) buildings.value = res.data || []
  } catch { /* ignore */ }
}

const onBuildingChange = async () => {
  bindForm.roomId = null
  rooms.value = []
  if (!bindForm.buiId) return
  roomsLoading.value = true
  try {
    const res: any = await request.get('/electricity/rooms', { params: { buiId: bindForm.buiId } })
    if (res.code === 200) rooms.value = res.data || []
  } catch { /* ignore */ }
  finally { roomsLoading.value = false }
}

const handleBind = async () => {
  if (!bindForm.buiId || !bindForm.roomId) return
  const room = rooms.value.find(r => r.roomId === bindForm.roomId)
  if (!room) return
  bindLoading.value = true
  try {
    const res: any = await request.post('/electricity/bind', {
      roomId: bindForm.roomId,
      buiId: bindForm.buiId,
      roomName: room.roomName
    })
    if (res.code === 200) {
      ElMessage.success('绑定成功')
      showCharMsg('宿舍绑定成功！', 'success')
      userStore.userInfo!.roomId = bindForm.roomId
      userStore.userInfo!.buiId = bindForm.buiId
      userStore.userInfo!.roomName = room.roomName
      localStorage.setItem('userInfo', JSON.stringify(userStore.userInfo))
      bindForm.buiId = null
      bindForm.roomId = null
      rooms.value = []
    } else {
      ElMessage.error(res.message || '绑定失败')
    }
  } catch { ElMessage.error('绑定失败') }
  finally { bindLoading.value = false }
}

const handleUnbind = async () => {
  try {
    await ElMessageBox.confirm('确定解绑宿舍吗？解绑后将无法查看电费信息。', '确认解绑', {
      confirmButtonText: '解绑',
      cancelButtonText: '取消',
      type: 'warning'
    })
    unbindLoading.value = true
    const res: any = await request.delete('/electricity/bind')
    if (res.code === 200) {
      ElMessage.success('已解绑')
      userStore.userInfo!.roomId = null
      userStore.userInfo!.buiId = null
      userStore.userInfo!.roomName = null
      localStorage.setItem('userInfo', JSON.stringify(userStore.userInfo))
    }
  } catch (err: any) {
    if (err !== 'cancel') ElMessage.error('解绑失败')
  } finally { unbindLoading.value = false }
}

onMounted(fetchBuildings)

// 人物对话框
const characterMessage = ref('这里是你的个人中心，可以编辑资料和修改密码~')
const characterMessageType = ref<'info' | 'success' | 'error' | 'warning'>('info')

const showCharMsg = (msg: string, type: 'info' | 'success' | 'error' | 'warning' = 'info') => {
  characterMessage.value = msg
  characterMessageType.value = type
}

const uploadHeaders = {
  Authorization: `Bearer ${localStorage.getItem('token') || ''}`
}

const beforeAvatarUpload = (file: File) => {
  const isValidType = ['image/jpeg', 'image/png', 'image/gif'].includes(file.type)
  const isValidSize = file.size <= 20 * 1024 * 1024

  if (!isValidType) {
    ElMessage.error('仅支持 JPG、PNG、GIF 格式图片')
    return false
  }
  if (!isValidSize) {
    ElMessage.error('文件大小不能超过 20MB')
    return false
  }
  return true
}

const handleUploadSuccess = (response: any) => {
  if (response.code === 200) {
    userStore.userInfo!.avatar = response.data
    localStorage.setItem('userInfo', JSON.stringify(userStore.userInfo))
    ElMessage.success('头像上传成功')
    showCharMsg('新头像真好看！', 'success')
  } else {
    ElMessage.error(response.message || '上传失败')
  }
}

const handleUploadError = () => {
  ElMessage.error('头像上传失败，请稍后重试')
}

// ========== 编辑资料 ==========
const editDialogVisible = ref(false)
const editLoading = ref(false)
const editFormRef = ref<FormInstance>()

const editForm = reactive({
  username: '',
  nickname: '',
  phone: '',
  email: '',
  birthday: '',
  gender: ''
})

// 唯一性校验状态
const usernameCheckMsg = ref('')
const usernameCheckOk = ref(false)
const phoneCheckMsg = ref('')
const phoneCheckOk = ref(false)

const editRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度为3-20个字符', trigger: 'blur' }
  ],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ],
  email: [
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }
  ]
}

const checkUsername = async () => {
  const val = editForm.username.trim()
  if (!val || val === userStore.userInfo?.username) {
    usernameCheckMsg.value = ''
    return
  }
  if (val.length < 3 || val.length > 20) return
  try {
    const res: any = await request.get('/user/check-username', { params: { username: val } })
    if (res.code === 200) {
      usernameCheckOk.value = res.data
      usernameCheckMsg.value = res.data ? '该用户名可用' : '该用户名已被使用'
      showCharMsg(res.data ? '用户名可以使用~' : '换个用户名试试', res.data ? 'success' : 'warning')
    }
  } catch {
    usernameCheckMsg.value = ''
  }
}

const checkPhone = async () => {
  const val = editForm.phone.trim()
  if (!val || val === userStore.userInfo?.phone) {
    phoneCheckMsg.value = ''
    return
  }
  if (!/^1[3-9]\d{9}$/.test(val)) return
  try {
    const res: any = await request.get('/user/check-phone', { params: { phone: val } })
    if (res.code === 200) {
      phoneCheckOk.value = res.data
      phoneCheckMsg.value = res.data ? '该手机号可用' : '该手机号已被其他用户使用'
      showCharMsg(res.data ? '手机号没有问题~' : '这个手机号已被使用', res.data ? 'success' : 'warning')
    }
  } catch {
    phoneCheckMsg.value = ''
  }
}

const openEditDialog = () => {
  editForm.username = userStore.userInfo?.username || ''
  editForm.nickname = userStore.userInfo?.nickname || ''
  editForm.phone = userStore.userInfo?.phone || ''
  editForm.email = userStore.userInfo?.email || ''
  editForm.birthday = userStore.userInfo?.birthday || ''
  editForm.gender = userStore.userInfo?.gender || ''
  usernameCheckMsg.value = ''
  phoneCheckMsg.value = ''
  editDialogVisible.value = true
  showCharMsg('修改完记得点确定保存哦', 'info')
}

const submitEditForm = async () => {
  const valid = await editFormRef.value?.validate().catch(() => false)
  if (!valid) return

  // 如果唯一性校验失败，阻止提交
  if (usernameCheckMsg.value && !usernameCheckOk.value) {
    showCharMsg('用户名不可用，请修改', 'error')
    return
  }
  if (phoneCheckMsg.value && !phoneCheckOk.value) {
    showCharMsg('手机号不可用，请修改', 'error')
    return
  }

  editLoading.value = true
  try {
    const res: any = await request.put('/user/profile', {
      username: editForm.username.trim(),
      nickname: editForm.nickname.trim(),
      phone: editForm.phone.trim(),
      email: editForm.email.trim(),
      birthday: editForm.birthday || null,
      gender: editForm.gender || null
    })
    if (res.code === 200) {
      // 如果用户名改了，后端会返回新 token
      if (res.data?.token) {
        userStore.token = res.data.token
        localStorage.setItem('token', res.data.token)
      }
      userStore.userInfo!.username = editForm.username.trim()
      userStore.userInfo!.nickname = editForm.nickname.trim()
      userStore.userInfo!.phone = editForm.phone.trim()
      userStore.userInfo!.email = editForm.email.trim()
      userStore.userInfo!.birthday = editForm.birthday || null
      userStore.userInfo!.gender = editForm.gender || ''
      localStorage.setItem('userInfo', JSON.stringify(userStore.userInfo))
      ElMessage.success('资料更新成功')
      showCharMsg('资料更新成功！', 'success')
      editDialogVisible.value = false
    } else {
      ElMessage.error(res.message || '更新失败')
      showCharMsg(res.message || '更新失败', 'error')
    }
  } catch {
    ElMessage.error('请求失败，请稍后重试')
  } finally {
    editLoading.value = false
  }
}

// ========== 修改密码 ==========
const passwordDialogVisible = ref(false)
const passwordLoading = ref(false)
const passwordFormRef = ref<FormInstance>()

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const validateConfirmPassword = (_rule: any, value: string, callback: any) => {
  if (value !== passwordForm.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const passwordValidator = (_rule: any, value: string, callback: any) => {
  if (!value) return callback(new Error('请输入新密码'))
  if (value.length < 6) return callback(new Error('密码长度不能少于6位'))
  if (!/[A-Z]/.test(value)) return callback(new Error('密码必须包含大写字母'))
  if (!/[a-z]/.test(value)) return callback(new Error('密码必须包含小写字母'))
  if (!/[0-9]/.test(value)) return callback(new Error('密码必须包含数字'))
  if (!/[^A-Za-z0-9]/.test(value)) return callback(new Error('密码必须包含特殊字符'))
  callback()
}

const filterPassword = () => {
  passwordForm.newPassword = passwordForm.newPassword.replace(/[^A-Za-z0-9!@#$%^&*()_+\-=\[\]{}|;:'",.<>?\/`~\\]/g, '')
}

const passwordRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [{ validator: passwordValidator, trigger: 'blur' }],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

const openPasswordDialog = () => {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordDialogVisible.value = true
  showCharMsg('修改密码需要验证旧密码', 'info')
}

const submitPasswordForm = async () => {
  const valid = await passwordFormRef.value?.validate().catch(() => false)
  if (!valid) return

  passwordLoading.value = true
  try {
    const res: any = await request.put('/user/password', {
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    })
    if (res.code === 200) {
      ElMessage.success('密码修改成功，请重新登录')
      showCharMsg('密码修改成功，请重新登录', 'success')
      passwordDialogVisible.value = false
      await userStore.logout()
      router.push('/login')
    } else {
      ElMessage.error(res.message || '修改失败')
      showCharMsg(res.message || '修改失败', 'error')
    }
  } catch {
    ElMessage.error('请求失败，请稍后重试')
  } finally {
    passwordLoading.value = false
  }
}

// ========== 退出登录 ==========
const handleLogout = () => {
  ElMessageBox.confirm(
    '确定要退出登录吗？',
    '提示',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(() => {
    userStore.logout()
    router.push('/login')
    ElMessage.success('已退出登录')
  })
}
</script>

<style scoped lang="scss">
.profile-page {
  min-height: 100vh;
  background-color: #f5f7fa;
}

.profile-wrapper {
  display: flex;
  align-items: flex-start;
  justify-content: center;
  gap: 30px;
  padding: 20px;
  max-width: 1100px;
  margin: 0 auto;
}

.profile-container {
  flex: 1;
  max-width: 800px;
  min-width: 0;
}

.back-btn {
  margin-bottom: 16px;
}

.profile-card {
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  font-size: 18px;
}

.avatar-section {
  text-align: center;
  padding: 30px 0;
}

.avatar-wrapper {
  position: relative;
  display: inline-block;
}

.user-avatar {
  width: 120px;
  height: 120px;
  border-radius: 50%;
  object-fit: cover;
  border: 3px solid #ebeef5;
}

.avatar-uploader {
  position: absolute;
  top: 0;
  left: 0;
  width: 120px;
  height: 120px;
  border-radius: 50%;
  opacity: 0;
  transition: all 0.3s;

  &:hover {
    opacity: 1;
  }

  :deep(.el-upload) {
    width: 100%;
    height: 100%;
  }
}

.upload-mask {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  color: white;
  cursor: pointer;
}

.upload-icon {
  font-size: 28px;
  margin-bottom: 4px;
}

.upload-text {
  font-size: 12px;
}

.upload-hint {
  margin-top: 20px;
  color: #909399;
  font-size: 12px;
  line-height: 1.8;
}

.user-info {
  padding: 10px 0;
}

.action-buttons {
  padding-top: 20px;
  display: flex;
  gap: 12px;
  justify-content: center;
  flex-wrap: wrap;
}

.dorm-section {
  padding: 10px 0;
}

.section-title {
  margin: 0 0 16px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.current-bind {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  padding: 10px 14px;
  background: #f0f9eb;
  border-radius: 8px;
}

.bind-label {
  font-size: 14px;
  color: #606266;
}

.bind-value {
  font-size: 15px;
  font-weight: 600;
  color: #67c23a;
}

.bind-form-row {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}

.status-bar-placeholder {
  height: 60px;
}

// 校验提示
.check-msg {
  font-size: 12px;
  margin-top: 4px;
  line-height: 1.4;

  &.ok {
    color: #67c23a;
  }

  &.fail {
    color: #f56c6c;
  }
}

// 移动端适配
@media screen and (max-width: 768px) {
  .profile-wrapper {
    flex-direction: column;
    align-items: center;
    gap: 10px;
    padding: 15px;
  }

  .profile-container {
    width: 100%;
  }

  .action-buttons {
    flex-direction: column;

    .el-button {
      width: 100%;
    }
  }
}
</style>

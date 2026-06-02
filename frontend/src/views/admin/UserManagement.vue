<template>
  <div class="user-management">
    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <span>用户管理</span>
          <div class="header-actions">
            <el-button type="primary" @click="showCreateDialog">新增用户</el-button>
            <el-button @click="loadUsers">刷新</el-button>
          </div>
        </div>
      </template>

      <!-- 搜索和筛选 -->
      <div class="filter-bar">
         <el-input
           v-model="keyword"
           placeholder="搜索用户名/昵称/邮箱/手机号"
           clearable
           style="width: 280px"
           @keyup.enter="handleSearch"
         >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-select v-model="statusFilter" placeholder="状态筛选" clearable style="width: 120px" @change="handleSearch">
          <el-option label="正常" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
        <el-button type="primary" @click="handleSearch">搜索</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </div>

      <!-- 用户表格 -->
      <el-table :data="users" style="width: 100%" v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="头像" width="80">
          <template #default="{ row }">
            <el-upload
              class="avatar-cell-upload"
              :show-file-list="false"
              :action="`/api/admin/users/${row.id}/avatar`"
              name="file"
              :headers="uploadHeaders"
              :before-upload="beforeAvatarUpload"
              :on-success="(res: any) => handleAvatarSuccess(res, row)"
              :on-error="handleAvatarError"
            >
              <img :src="getAvatarUrl(row.avatar)" alt="头像" class="table-avatar" />
            </el-upload>
          </template>
        </el-table-column>
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="nickname" label="昵称" />
         <el-table-column prop="email" label="邮箱" />
         <el-table-column prop="phone" label="手机号" width="120" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="created_at" label="注册时间" width="180" />
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="showEditDialog(row)">编辑</el-button>
            <el-button
              :type="row.status === 1 ? 'warning' : 'success'"
              size="small"
              @click="toggleStatus(row)"
            >
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
            <el-button type="danger" size="small" @click="deleteUser(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 加载更多 -->
      <div class="load-more" v-if="hasMore">
        <el-button type="primary" @click="loadMore" :loading="loadingMore">加载更多</el-button>
      </div>
      <div class="no-more" v-else-if="users.length > 0">
        <span>没有更多数据了</span>
      </div>
    </el-card>

    <!-- 新增用户对话框 -->
    <el-dialog v-model="createDialogVisible" title="新增用户" width="400px">
      <el-form :model="createForm" :rules="createRules" ref="createFormRef" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="createForm.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="createForm.nickname" placeholder="请输入昵称（可选）" />
        </el-form-item>
         <el-form-item label="邮箱" prop="email">
           <el-input v-model="createForm.email" placeholder="请输入邮箱（可选）" />
         </el-form-item>
         <el-form-item label="手机号" prop="phone">
           <el-input v-model="createForm.phone" placeholder="请输入手机号" />
         </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="createForm.password" type="password" placeholder="请输入密码" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCreate" :loading="creating">确定</el-button>
      </template>
    </el-dialog>

    <!-- 编辑用户对话框 -->
    <el-dialog v-model="editDialogVisible" title="编辑用户" width="400px">
      <el-form :model="editForm" ref="editFormRef" label-width="80px">
        <el-form-item label="用户名">
          <el-input v-model="editForm.username" disabled />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="editForm.nickname" placeholder="请输入昵称" />
        </el-form-item>
         <el-form-item label="邮箱" prop="email">
           <el-input v-model="editForm.email" placeholder="请输入邮箱" />
         </el-form-item>
         <el-form-item label="手机号" prop="phone">
           <el-input v-model="editForm.phone" placeholder="请输入手机号" />
         </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleEdit" :loading="editing">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import request from '@/utils/request'

const uploadHeaders = {
  Authorization: `Bearer ${localStorage.getItem('token') || ''}`
}

const getAvatarUrl = (avatar: string | null) => {
  if (!avatar) return '/default-avatar.jpg'
  return avatar.startsWith('http') ? avatar : `http://localhost:8080${avatar}`
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

const handleAvatarSuccess = (response: any, row: any) => {
  if (response.code === 200) {
    row.avatar = response.data
    ElMessage.success('头像修改成功')
  } else {
    ElMessage.error(response.message || '头像上传失败')
  }
}

const handleAvatarError = () => {
  ElMessage.error('头像上传失败，请稍后重试')
}

const loading = ref(false)
const loadingMore = ref(false)
const users = ref<any[]>([])
const cursor = ref<number>(0)
const hasMore = ref(false)
const size = ref(10)
const keyword = ref('')
const statusFilter = ref<number | null>(null)

// 搜索时重置游标
const handleSearch = () => {
  cursor.value = 0
  users.value = []
  loadUsers()
}

// 重置搜索
const resetSearch = () => {
  keyword.value = ''
  statusFilter.value = null
  cursor.value = 0
  users.value = []
  loadUsers()
}

const loadUsers = async () => {
  if (cursor.value === 0) {
    loading.value = true
  } else {
    loadingMore.value = true
  }
  
  try {
    const params: any = {
      size: size.value,
      cursor: cursor.value
    }
    if (keyword.value) {
      params.keyword = keyword.value
    }
    if (statusFilter.value !== null) {
      params.status = statusFilter.value
    }
    
    const res: any = await request.get('/admin/users', { params })
    if (res.code === 200) {
      const newData = res.data.list || []
      users.value = newData
      hasMore.value = res.data.hasMore
      cursor.value = res.data.nextCursor || 0
    }
  } catch (error: any) {
    ElMessage.error(error.message || '加载用户列表失败')
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

const loadMore = () => {
  loadUsers()
}

const deleteUser = (row: any) => {
  ElMessageBox.confirm(`确定要删除用户 "${row.username}" 吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    try {
      const res: any = await request.delete(`/admin/users/${row.id}`)
      if (res.code === 200) {
        ElMessage.success('删除成功')
        // 从列表中移除
        users.value = users.value.filter(u => u.id !== row.id)
      }
    } catch (error: any) {
      ElMessage.error(error.message || '删除失败')
    }
  }).catch(() => {})
}

const toggleStatus = async (row: any) => {
  const newStatus = row.status === 1 ? 0 : 1
  const action = newStatus === 1 ? '启用' : '禁用'
  
  ElMessageBox.confirm(`确定要${action}用户 "${row.username}" 吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    try {
      const res: any = await request.put(`/admin/users/${row.id}/status?status=${newStatus}`)
      if (res.code === 200) {
        ElMessage.success(`${action}成功`)
        row.status = newStatus
      }
    } catch (error: any) {
      ElMessage.error(error.message || `${action}失败`)
    }
  }).catch(() => {})
}

// 新增用户相关
const createDialogVisible = ref(false)
const creating = ref(false)
const createFormRef = ref<any>(null)
 const createForm = reactive({
   username: '',
   nickname: '',
   email: '',
   phone: '',
   password: ''
 })

 const createRules = {
   username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
   password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
   phone: [
     { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的11位手机号', trigger: 'blur' }
   ]
 }

const showCreateDialog = () => {
  createDialogVisible.value = true
  createForm.username = ''
  createForm.nickname = ''
  createForm.email = ''
  createForm.password = ''
}

const handleCreate = async () => {
  if (!createFormRef.value) return
  
  await createFormRef.value.validate(async (valid: boolean) => {
    if (!valid) return
    
    creating.value = true
    try {
      const res: any = await request.post('/admin/users', createForm)
      if (res.code === 200) {
        ElMessage.success('创建成功')
        createDialogVisible.value = false
        // 刷新列表
        cursor.value = 0
        users.value = []
        loadUsers()
      }
    } catch (error: any) {
      ElMessage.error(error.message || '创建失败')
    } finally {
      creating.value = false
    }
  })
}

// 编辑用户相关
const editDialogVisible = ref(false)
const editing = ref(false)
const editForm = reactive({
   id: 0,
   username: '',
   nickname: '',
   email: '',
   phone: ''
 })

 const showEditDialog = (row: any) => {
   editDialogVisible.value = true
   editForm.id = row.id
   editForm.username = row.username
   editForm.nickname = row.nickname || ''
   editForm.email = row.email || ''
   editForm.phone = row.phone || ''
 }

const handleEdit = async () => {
  editing.value = true
  try {
     const res: any = await request.put(`/admin/users/${editForm.id}`, {
       nickname: editForm.nickname,
       email: editForm.email,
       phone: editForm.phone
     })
    if (res.code === 200) {
      ElMessage.success('更新成功')
      editDialogVisible.value = false
      // 更新列表中的对应项
      const index = users.value.findIndex(u => u.id === editForm.id)
      if (index !== -1) {
       users.value[index].nickname = editForm.nickname
       users.value[index].email = editForm.email
       users.value[index].phone = editForm.phone
      }
    }
  } catch (error: any) {
    ElMessage.error(error.message || '更新失败')
  } finally {
    editing.value = false
  }
}

onMounted(() => {
  loadUsers()
})
</script>

<style scoped>
.user-management {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.filter-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
  align-items: center;
}

.box-card {
  margin-bottom: 20px;
}

.load-more {
  text-align: center;
  margin-top: 20px;
}

.no-more {
  text-align: center;
  margin-top: 20px;
  color: var(--text-secondary);
  font-size: 14px;
}

.avatar-cell-upload {
  display: inline-block;
}

.table-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  object-fit: cover;
  cursor: pointer;
  transition: opacity 0.2s;
}

.table-avatar:hover {
  opacity: 0.7;
}
</style>
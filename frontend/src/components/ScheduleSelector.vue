<template>
  <el-select
    v-model="selectedSemester"
    placeholder="选择学期"
    @change="handleSemesterChange"
  >
    <el-option
      v-for="semester in semesterList"
      :key="semester.semesterId"
      :label="semester.nameZh"
      :value="semester.nameZh"
    >
      <div class="semester-option">
        <span>{{ semester.nameZh }}</span>
        <el-icon v-if="semester.imported" class="imported-icon">
          <Check />
        </el-icon>
      </div>
    </el-option>
  </el-select>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { Check } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useScheduleStore } from '@/stores/schedule'

const scheduleStore = useScheduleStore()
const { semesterList } = storeToRefs(scheduleStore)
const { fetchSemesterList } = scheduleStore

const selectedSemester = ref<string>('')

const handleSemesterChange = async (semester: string) => {
  const selected = semesterList.value.find(s => s.nameZh === semester)
  if (!selected) return

  if (selected.imported) {
    // 已导入的学期，从本地加载
    await scheduleStore.fetchSchedule()
  } else {
    // 未导入的学期，从教务系统获取
    ElMessage.warning('该学期尚未导入，请先通过课表导入页面导入')
  }
}

onMounted(async () => {
  try {
    await fetchSemesterList()
  } catch (error) {
    console.error('获取学期列表失败:', error)
  }
})
</script>

<style scoped>
.semester-option {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.imported-icon {
  color: #67C23A;
}
</style>

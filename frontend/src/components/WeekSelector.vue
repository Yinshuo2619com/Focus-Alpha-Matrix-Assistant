<template>
  <div class="week-selector">
    <el-select v-model="currentWeek" size="small" placeholder="选择周次">
      <el-option :key="PREVIEW_WEEK" label="预览（全部）" :value="PREVIEW_WEEK" />
      <el-option v-for="w in weekList" :key="w" :label="`第${w}周`" :value="w" />
      <el-option :key="END_WEEK" label="结束" :value="END_WEEK" />
    </el-select>
  </div>
</template>

<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { useScheduleStore } from '@/stores/schedule'

const scheduleStore = useScheduleStore()
const { currentWeek, weekList } = storeToRefs(scheduleStore)
const { PREVIEW_WEEK, END_WEEK } = scheduleStore
</script>

<style scoped>
.week-selector {
  margin-bottom: 12px;
}

.week-selector .el-select {
  width: 140px;
}
</style>

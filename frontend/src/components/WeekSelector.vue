<template>
  <div class="week-selector">
    <div
      v-for="option in weekOptions"
      :key="option.week"
      class="week-item"
      :class="{
        'active': currentWeek === option.week,
        'special': option.isSpecial,
        'preview': option.week === PREVIEW_WEEK,
        'end': option.week === END_WEEK
      }"
      @click="selectWeek(option.week)"
    >
      {{ option.label }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useScheduleStore } from '@/stores/schedule'

const scheduleStore = useScheduleStore()
const { currentWeek, maxWeek } = storeToRefs(scheduleStore)
const { PREVIEW_WEEK, END_WEEK } = scheduleStore

const weekOptions = computed(() => {
  const options = []

  // 预览选项（永远第一）
  options.push({
    week: PREVIEW_WEEK,
    label: '预览',
    isSpecial: true
  })

  // 普通周数
  for (let i = 1; i <= maxWeek.value; i++) {
    options.push({
      week: i,
      label: `${i}`,
      isSpecial: false
    })
  }

  // 结束选项（永远最后）
  options.push({
    week: END_WEEK,
    label: '结束',
    isSpecial: true
  })

  return options
})

const selectWeek = (week: number) => {
  currentWeek.value = week
}
</script>

<style scoped>
.week-selector {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.week-item {
  padding: 4px 8px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
  transition: all 0.2s;
  user-select: none;
}

.week-item:hover {
  background: var(--el-fill-color-light);
}

.week-item.active {
  background: var(--el-color-primary);
  color: white;
}

.week-item.special {
  font-weight: bold;
  min-width: 50px;
  text-align: center;
}

.week-item.preview {
  color: #409EFF;
}

.week-item.end {
  color: #909399;
}

.week-item.active.special {
  color: white;
}
</style>

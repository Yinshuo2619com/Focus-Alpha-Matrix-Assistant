# 往期课表功能设计

## 概述

添加"往期课表"按钮，点击后打开新窗口展示历史学期的课表。用户可以通过下拉框选择学期，查看该学期的课程安排。

## 功能需求

### 1. 按钮设计
- **位置**：Home.vue 课表标题右侧
- **样式**：default类型按钮，带图标
- **文字**："往期课表"
- **条件**：仅在用户有课表时显示

### 2. 页面设计
- **路由**：`/historical-schedule`
- **新窗口**：点击按钮后 `window.open` 打开
- **内容**：复用 ScheduleGrid 组件显示完整课表

### 3. 学期选择器
- **组件**：el-select
- **格式**：`20xx-1` 或 `20xx-2`
- **数据源**：semesterList（从教务系统获取）
- **标记**：已导入的学期显示勾选图标

### 4. 数据获取
- **已导入课表**：直接从本地数据库加载
- **未导入课表**：调用 `fetchHistoricalSchedule` 从教务系统获取
- **登录检查**：获取前检查用户是否已登录教务系统

## 实现方案

### 1. 创建 HistoricalSchedule.vue
```vue
<template>
  <div class="historical-schedule">
    <div class="header">
      <h2>往期课表</h2>
      <el-select v-model="selectedSemester" placeholder="选择学期">
        <!-- 学期选项 -->
      </el-select>
    </div>
    <ScheduleGrid v-if="scheduleData" :courses="courses" />
    <el-empty v-else description="请选择学期查看课表" />
  </div>
</template>
```

### 2. 修改 Home.vue
```vue
<el-button v-if="scheduleStore.hasSchedule" size="small" @click="openHistorical">
  <el-icon><Calendar /></el-icon>
  往期课表
</el-button>
```

### 3. 添加路由
```typescript
{
  path: '/historical-schedule',
  name: 'HistoricalSchedule',
  component: () => import('@/views/HistoricalSchedule.vue')
}
```

## 技术细节

### 学期格式转换
- 后端返回 `nameZh`：如 "2025-2026-1"
- 前端显示：如 "2025-1"（取年份部分 + 学期号）

### 数据流
1. 页面加载 → 获取 semesterList
2. 用户选择学期 → 调用 fetchHistoricalSchedule
3. 获取成功 → 更新 scheduleData，显示 ScheduleGrid
4. 获取失败 → 显示错误提示

### 错误处理
- 未登录教务系统：提示先登录
- 获取失败：显示错误信息
- 无课表数据：显示空状态

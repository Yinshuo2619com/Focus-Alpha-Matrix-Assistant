# 课表增强功能设计文档

## 概述

本设计文档描述课表页面的增强功能，包括特殊周数选项、自动选择逻辑、学期切换和历史课表支持。

## 功能需求

### 1. WeekSelector特殊选项

在周数选择器中添加两个特殊选项：

- **预览** (week=-1)：永远在周数选择器最前面
- **结束** (week=-2)：永远在周数选择器最后面

这两个选项不受刷新课表影响，始终存在。

### 2. 自动选择逻辑

根据 `startDate` 和 `maxWeek` 推断当前应该选中哪个选项：

- **今天 < startDate**：选中"预览"（学期未开始）
- **startDate ≤ 今天 ≤ startDate + maxWeek周**：选中当前周（学期进行中）
- **今天 > startDate + maxWeek周**：选中"结束"（学期已结束）

推断逻辑在前端完成，后端只需返回 `startDate` 和 `maxWeek`。

### 3. 显示逻辑

#### 预览模式
- 显示整个学期所有周的课程
- 同一时间段有冲突的课程：最多显示2门（各50%宽度）
- 超过2门冲突的课程：折叠显示，点击可展开查看全部

#### 结束模式
- 清空课表网格
- 中间显示"课程结束"文字提示

#### 普通周数
- 保持现有逻辑不变

### 4. 学期切换

#### 后端接口
- 新增接口：返回教务系统所有可用学期列表
- 每个学期包含：`semesterId`、`nameZh`、`startDate`、`endDate`

#### 前端显示
- ScheduleSelector下拉列表显示所有学期
- 已导入的学期有标记（如勾选图标）
- 未导入的学期可点击后自动从教务获取

#### 数据获取
- 切换学期时：优先读本地数据库
- 本地没有时：尝试从教务系统获取（使用 `semesterId` 查询）

### 5. 历史课表支持

经测试，教务系统支持查询历史学期课表数据：

- 通过 `semesterId` 可查询任意学期的课表
- 测试结果：成功获取2025-2026-2学期的17门课程数据
- 学期信息：startDate=2026-03-02, endDate=2026-07-05

### 6. 数据存储

- 每个学期只保留一份课表（已实现）
- 保存新课表时自动删除同学期旧课表

## 技术实现

### 前端修改

#### WeekSelector组件
```typescript
// 特殊选项常量
const PREVIEW_WEEK = -1
const END_WEEK = -2

// 生成周数列表（包含特殊选项）
const weekOptions = computed(() => {
  const options = []
  
  // 预览选项（永远第一）
  options.push({ week: PREVIEW_WEEK, label: '预览', isSpecial: true })
  
  // 普通周数
  for (let i = 1; i <= maxWeek.value; i++) {
    options.push({ week: i, label: `${i}`, isSpecial: false })
  }
  
  // 结束选项（永远最后）
  options.push({ week: END_WEEK, label: '结束', isSpecial: true })
  
  return options
})
```

#### 自动选择逻辑
```typescript
const autoSelectWeek = () => {
  if (!startDate.value || !maxWeek.value) return
  
  const today = new Date()
  const start = new Date(startDate.value)
  const end = new Date(start)
  end.setDate(end.getDate() + maxWeek.value * 7)
  
  if (today < start) {
    currentWeek.value = PREVIEW_WEEK
  } else if (today > end) {
    currentWeek.value = END_WEEK
  } else {
    // 计算当前周数
    const diffTime = today.getTime() - start.getTime()
    const diffWeeks = Math.floor(diffTime / (7 * 24 * 60 * 60 * 1000)) + 1
    currentWeek.value = Math.min(diffWeeks, maxWeek.value)
  }
}
```

#### 课表显示逻辑
```typescript
const filteredCourses = computed(() => {
  if (currentWeek.value === PREVIEW_WEEK) {
    // 预览模式：显示所有课程
    return allCourses.value
  } else if (currentWeek.value === END_WEEK) {
    // 结束模式：不显示任何课程
    return []
  } else {
    // 普通模式：按周数过滤
    return allCourses.value.filter(course => {
      const weeks = course.weeks.split(',').map(Number)
      return weeks.includes(currentWeek.value)
    })
  }
})
```

#### 冲突处理组件
```vue
<template>
  <div class="course-cell" :class="{ 'has-conflict': hasConflict }">
    <template v-if="visibleCourses.length <= 2">
      <div 
        v-for="course in visibleCourses" 
        :key="course.id"
        class="course-card"
        :style="{ width: `${100 / visibleCourses.length}%` }"
      >
        {{ course.courseName }}
      </div>
    </template>
    <template v-else>
      <div class="course-card" style="width: 50%">
        {{ visibleCourses[0].courseName }}
      </div>
      <div 
        class="course-card conflict-indicator" 
        style="width: 50%"
        @click="expandConflict"
      >
        +{{ visibleCourses.length - 1 }}
      </div>
    </template>
  </div>
</template>
```

### 后端修改

#### 新增学期列表接口
```java
@GetMapping("/semesters")
public Result<List<Map<String, Object>>> getSemesterList() {
    Long userId = getCurrentUserId();
    
    // 从教务系统获取学期列表
    List<Map<String, Object>> semesters = eduProxyService.parseSemestersFromHtml(
        eduProxyService.fetchSchedulePage(userId, "default")
    );
    
    // 获取用户已导入的学期
    List<String> importedSemesters = jdbcTemplate.queryForList(
        "SELECT DISTINCT semester FROM schedule WHERE user_id = ?",
        String.class, userId
    );
    
    // 标记已导入的学期
    for (Map<String, Object> sem : semesters) {
        String nameZh = (String) sem.get("nameZh");
        sem.put("imported", importedSemesters.contains(nameZh));
    }
    
    return Result.success(semesters);
}
```

#### 新增历史课表获取接口
```java
@GetMapping("/history")
public Result<Map<String, Object>> getHistoricalSchedule(
        @RequestParam String semester,
        @RequestParam int semesterId) {
    Long userId = getCurrentUserId();
    
    // 先查本地数据库
    List<Map<String, Object>> schedules = jdbcTemplate.queryForList(
        "SELECT * FROM schedule WHERE user_id = ? AND semester = ?",
        userId, semester
    );
    
    if (!schedules.isEmpty()) {
        // 本地有数据，直接返回
        Long scheduleId = ((Number) schedules.get(0).get("id")).longValue();
        List<Map<String, Object>> courses = jdbcTemplate.queryForList(
            "SELECT * FROM course_entry WHERE schedule_id = ?",
            scheduleId
        );
        
        Map<String, Object> result = new HashMap<>();
        result.put("schedule", schedules.get(0));
        result.put("courses", courses);
        return Result.success(result);
    }
    
    // 本地没有，从教务系统获取
    try {
        String jsonData = eduProxyService.fetchScheduleDataApi(userId, "default", semesterId);
        // ... 解析并保存到数据库 ...
        
        Map<String, Object> result = new HashMap<>();
        result.put("schedule", scheduleData);
        result.put("courses", parsedCourses);
        return Result.success(result);
    } catch (Exception e) {
        return Result.error(500, "获取历史课表失败: " + e.getMessage());
    }
}
```

## 实现步骤

### 阶段1：WeekSelector特殊选项
1. 修改WeekSelector组件，添加预览和结束选项
2. 实现自动选择逻辑
3. 测试特殊选项的显示和选择

### 阶段2：显示逻辑
1. 实现预览模式的课程显示
2. 实现冲突课程的并排显示和折叠逻辑
3. 实现结束模式的空状态显示
4. 测试各种场景

### 阶段3：学期切换
1. 后端新增学期列表接口
2. 前端ScheduleSelector显示所有学期
3. 实现学期切换逻辑（本地优先+教务回退）
4. 测试学期切换功能

### 阶段4：历史课表
1. 后端新增历史课表获取接口
2. 前端实现历史课表加载
3. 测试历史课表功能

## 验收标准

1. WeekSelector始终显示预览和结束选项
2. 自动选择逻辑正确：开学前→预览，学期中→当前周，结课后→结束
3. 预览模式显示所有课程，冲突课程正确并排显示
4. 结束模式显示空网格和"课程结束"提示
5. 学期切换功能正常，支持历史学期查询
6. 每个学期只保留一份课表

## 风险与注意事项

1. **教务系统Session过期**：查询历史课表需要保持教务系统登录状态
2. **数据一致性**：本地数据库和教务系统数据可能不一致
3. **性能考虑**：预览模式显示所有课程可能影响渲染性能
4. **用户体验**：冲突课程的折叠/展开交互需要流畅

## 测试用例

1. 开学前访问课表页面，验证自动选中"预览"
2. 学期中访问课表页面，验证自动选中当前周
3. 学期结束后访问课表页面，验证自动选中"结束"
4. 在预览模式下查看有冲突的课程，验证并排显示
5. 点击折叠的冲突课程，验证展开显示全部
6. 切换到历史学期，验证课表正确加载
7. 刷新课表，验证特殊选项仍然存在

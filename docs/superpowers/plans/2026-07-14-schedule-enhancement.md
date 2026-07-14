# 课表增强功能实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现课表页面的增强功能，包括特殊周数选项、自动选择逻辑、学期切换和历史课表支持

**Architecture:** 前端在WeekSelector组件中添加预览和结束两个特殊选项，后端新增学期列表和历史课表接口，通过混合模式实现本地优先+教务回退的数据获取策略

**Tech Stack:** Vue 3 + TypeScript + Element Plus (前端) / Spring Boot + JdbcTemplate (后端)

## Global Constraints

- 前端使用Vue 3 Composition API with `<script setup lang="ts">`
- 后端使用Spring Boot + JdbcTemplate直接SQL查询
- 统一响应格式：`Result<T>` wrapper (`{code, message, data}`)
- API前缀：`/api/schedule/`
- 每个学期只保留一份课表（已实现）
- 特殊选项week值：预览=-1，结束=-2

---

## 文件结构

### 前端文件
- `frontend/src/components/WeekSelector.vue` - 修改：添加特殊选项
- `frontend/src/components/ScheduleGrid.vue` - 修改：处理预览和结束模式显示
- `frontend/src/components/ScheduleSelector.vue` - 修改：显示所有学期
- `frontend/src/stores/schedule.ts` - 修改：添加学期列表和自动选择逻辑
- `frontend/src/views/Home.vue` - 修改：集成新功能

### 后端文件
- `backend/assistant/src/main/java/com/educate/assistant/controller/ScheduleController.java` - 修改：添加学期列表和历史课表接口
- `backend/assistant/src/main/java/com/educate/assistant/service/EduProxyService.java` - 修改：公开parseSemestersFromHtml方法

---

## Task 1: 后端 - 公开parseSemestersFromHtml方法

**Files:**
- Modify: `backend/assistant/src/main/java/com/educate/assistant/service/EduProxyService.java:586-605`

**Interfaces:**
- Produces: `public List<Map<String, Object>> parseSemestersFromHtml(String html)` - 返回学期列表，每个学期包含id, nameZh, startDate, endDate

- [ ] **Step 1: 修改parseSemestersFromHtml方法为public**

```java
// 将 private java.util.List<java.util.Map<String, Object>> parseSemestersFromHtml(String html)
// 改为 public java.util.List<java.util.Map<String, Object>> parseSemestersFromHtml(String html)
```

```java
public java.util.List<java.util.Map<String, Object>> parseSemestersFromHtml(String html) {
    // Match: var semesters = JSON.parse(\n      '[...]')  — allow whitespace/newline after (
    java.util.regex.Pattern p = java.util.regex.Pattern.compile("var\\s+semesters\\s*=\\s*JSON\\.parse\\(\\s*'(.+?)'\\s*\\)", java.util.regex.Pattern.DOTALL);
    java.util.regex.Matcher m = p.matcher(html);
    if (m.find()) {
        String json = m.group(1);
        // Unescape JavaScript single-quoted string: \' → ', \" → ", \\ → \
        json = json.replace("\\'", "'").replace("\\\"", "\"").replace("\\\\", "\\");
        System.out.println("[EduProxy] Parsed semesters JSON, length: " + json.length());
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.util.Map<String, Object>>>() {});
        } catch (Exception e) {
            System.out.println("[EduProxy] Failed to parse semesters JSON: " + e.getMessage());
        }
    } else {
        System.out.println("[EduProxy] semesters regex did not match, html length: " + html.length());
    }
    return null;
}
```

- [ ] **Step 2: 验证修改**

Run: `cd backend/assistant && ./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add backend/assistant/src/main/java/com/educate/assistant/service/EduProxyService.java
git commit -m "refactor: 公开parseSemestersFromHtml方法供外部调用"
```

---

## Task 2: 后端 - 新增学期列表接口

**Files:**
- Modify: `backend/assistant/src/main/java/com/educate/assistant/controller/ScheduleController.java:44-49` (在getCurrentSchedule方法后添加)

**Interfaces:**
- Produces: `GET /api/schedule/semesters` - 返回学期列表，每个学期包含semesterId, nameZh, startDate, endDate, imported

- [ ] **Step 1: 添加学期列表接口**

```java
@GetMapping("/semesters")
public Result<List<Map<String, Object>>> getSemesterList() {
    Long userId = getCurrentUserId();

    // 检查教务系统session
    Map<String, String> cookies = eduProxyService.getSessionCookies(userId);
    if (cookies.isEmpty()) {
        return Result.error(400, "未登录教务系统，请先登录");
    }

    try {
        // 从教务系统获取学期列表
        String html = eduProxyService.fetchSchedulePage(userId, "default");
        List<Map<String, Object>> semesters = eduProxyService.parseSemestersFromHtml(html);

        if (semesters == null || semesters.isEmpty()) {
            return Result.error(500, "无法获取学期列表");
        }

        // 获取用户已导入的学期
        List<String> importedSemesters = jdbcTemplate.queryForList(
            "SELECT DISTINCT semester FROM schedule WHERE user_id = ?",
            String.class, userId);

        // 标记已导入的学期
        for (Map<String, Object> sem : semesters) {
            String nameZh = (String) sem.get("nameZh");
            sem.put("imported", importedSemesters.contains(nameZh));
        }

        return Result.success(semesters);
    } catch (Exception e) {
        return Result.error(500, "获取学期列表失败: " + e.getMessage());
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `cd backend/assistant && ./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add backend/assistant/src/main/java/com/educate/assistant/controller/ScheduleController.java
git commit -m "feat: 新增学期列表接口GET /api/schedule/semesters"
```

---

## Task 3: 后端 - 新增历史课表获取接口

**Files:**
- Modify: `backend/assistant/src/main/java/com/educate/assistant/controller/ScheduleController.java` (在学期列表接口后添加)

**Interfaces:**
- Produces: `GET /api/schedule/history?semester={semester}&semesterId={semesterId}` - 返回指定学期的课表数据

- [ ] **Step 1: 添加历史课表接口**

```java
@GetMapping("/history")
public Result<Map<String, Object>> getHistoricalSchedule(
        @RequestParam String semester,
        @RequestParam int semesterId) {
    Long userId = getCurrentUserId();

    // 先查本地数据库
    List<Map<String, Object>> schedules = jdbcTemplate.queryForList(
        "SELECT * FROM schedule WHERE user_id = ? AND semester = ?",
        userId, semester);

    if (!schedules.isEmpty()) {
        // 本地有数据，直接返回
        Map<String, Object> schedule = schedules.get(0);
        Long scheduleId = ((Number) schedule.get("id")).longValue();
        List<Map<String, Object>> courses = jdbcTemplate.queryForList(
            "SELECT * FROM course_entry WHERE schedule_id = ? ORDER BY day_of_week, start_section",
            scheduleId);

        Map<String, Object> result = new HashMap<>();
        result.put("schedule", schedule);
        result.put("courses", courses);
        return Result.success(result);
    }

    // 本地没有，从教务系统获取
    Map<String, String> cookies = eduProxyService.getSessionCookies(userId);
    if (cookies.isEmpty()) {
        return Result.error(400, "未登录教务系统，请先登录");
    }

    try {
        // 获取学期信息
        Map<String, Object> semesterInfo = eduProxyService.getSemesterInfo(userId, "default", semester);
        if (semesterInfo == null) {
            return Result.error(400, "无法获取学期信息");
        }

        // 从教务系统获取课表数据
        String jsonData = eduProxyService.fetchScheduleDataApi(userId, "default", semesterId);
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> data = mapper.readValue(jsonData, new TypeReference<Map<String, Object>>() {});

        // 提取课程ID列表
        List<Integer> lessonIds = new ArrayList<>();
        Object lessonsObj = data.get("lessons");
        if (lessonsObj instanceof List) {
            for (Object lesson : (List<?>) lessonsObj) {
                if (lesson instanceof Map) {
                    Object id = ((Map<?, ?>) lesson).get("id");
                    if (id instanceof Number) lessonIds.add(((Number) id).intValue());
                }
            }
        }

        if (lessonIds.isEmpty()) {
            return Result.error(400, "该学期没有课程数据");
        }

        // 获取学生实际课程安排
        int stdPersonId = eduProxyService.getStdPersonId(userId, "default");
        String datumJson = eduProxyService.fetchDatumApi(userId, "default", lessonIds, stdPersonId);
        Map<String, Object> datumRoot = mapper.readValue(datumJson, new TypeReference<Map<String, Object>>() {});

        // 解析课程数据（复用refreshSchedule的逻辑）
        // ... 这里省略详细解析逻辑，实际实现时需要完整复制 ...

        // 保存到数据库
        SaveScheduleRequest request = new SaveScheduleRequest();
        request.setSemester(semester);
        request.setCourses(parsedCourses);
        request.setStartDate((String) semesterInfo.get("startDate"));
        scheduleService.saveSchedule(userId, request);

        // 返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("schedule", request);
        result.put("courses", parsedCourses);
        return Result.success(result);
    } catch (Exception e) {
        return Result.error(500, "获取历史课表失败: " + e.getMessage());
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `cd backend/assistant && ./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add backend/assistant/src/main/java/com/educate/assistant/controller/ScheduleController.java
git commit -m "feat: 新增历史课表获取接口GET /api/schedule/history"
```

---

## Task 4: 前端 - 添加特殊选项常量和类型定义

**Files:**
- Modify: `frontend/src/stores/schedule.ts`

**Interfaces:**
- Produces: `PREVIEW_WEEK = -1`, `END_WEEK = -2` 常量

- [ ] **Step 1: 添加特殊选项常量**

```typescript
// 在文件开头添加常量
const PREVIEW_WEEK = -1
const END_WEEK = -2
```

- [ ] **Step 2: 验证TypeScript编译**

Run: `cd frontend && npm run build`
Expected: 编译成功

- [ ] **Step 3: 提交**

```bash
git add frontend/src/stores/schedule.ts
git commit -m "feat: 添加课表特殊选项常量PREVIEW_WEEK和END_WEEK"
```

---

## Task 5: 前端 - 修改schedule store添加学期列表功能

**Files:**
- Modify: `frontend/src/stores/schedule.ts`

**Interfaces:**
- Produces: `semesterList: Ref<SemesterInfo[]>` - 学期列表
- Produces: `fetchSemesterList()` - 获取学期列表函数
- Produces: `autoSelectWeek()` - 自动选择周数函数

- [ ] **Step 1: 添加学期列表接口类型**

```typescript
interface SemesterInfo {
  semesterId: number
  nameZh: string
  startDate: string
  endDate: string
  imported: boolean
}
```

- [ ] **Step 2: 添加学期列表状态和方法**

```typescript
// 在state部分添加
const semesterList = ref<SemesterInfo[]>([])

// 添加获取学期列表方法
const fetchSemesterList = async () => {
  try {
    const res = await request.get('/api/schedule/semesters')
    semesterList.value = res.data || []
  } catch (error) {
    console.error('获取学期列表失败:', error)
    throw error
  }
}
```

- [ ] **Step 3: 添加自动选择周数方法**

```typescript
const autoSelectWeek = () => {
  if (!startDate.value || !maxWeek.value) {
    currentWeek.value = PREVIEW_WEEK
    return
  }

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

- [ ] **Step 4: 在getScheduleData中调用autoSelectWeek**

```typescript
// 在getScheduleData方法的success分支中添加
if (res.code === 200 && res.data.schedule) {
  // ... 现有代码 ...
  autoSelectWeek()  // 添加这一行
}
```

- [ ] **Step 5: 导出新添加的状态和方法**

```typescript
return {
  // ... 现有导出 ...
  semesterList,
  fetchSemesterList,
  autoSelectWeek,
  PREVIEW_WEEK,
  END_WEEK
}
```

- [ ] **Step 6: 验证TypeScript编译**

Run: `cd frontend && npm run build`
Expected: 编译成功

- [ ] **Step 7: 提交**

```bash
git add frontend/src/stores/schedule.ts
git commit -m "feat: 添加学期列表和自动选择周数功能"
```

---

## Task 6: 前端 - 修改WeekSelector添加特殊选项

**Files:**
- Modify: `frontend/src/components/WeekSelector.vue`

**Interfaces:**
- Consumes: `PREVIEW_WEEK`, `END_WEEK` 常量
- Consumes: `currentWeek` 状态

- [ ] **Step 1: 导入常量**

```typescript
import { useScheduleStore } from '@/stores/schedule'

const scheduleStore = useScheduleStore()
const { currentWeek } = storeToRefs(scheduleStore)
const { PREVIEW_WEEK, END_WEEK } = scheduleStore
```

- [ ] **Step 2: 修改weekOptions计算属性**

```typescript
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
```

- [ ] **Step 3: 修改模板中的样式类**

```vue
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
```

- [ ] **Step 4: 添加特殊选项的样式**

```vue
<style scoped>
.week-item.special {
  font-weight: bold;
  min-width: 50px;
}

.week-item.preview {
  color: #409EFF;
}

.week-item.end {
  color: #909399;
}
</style>
```

- [ ] **Step 5: 验证TypeScript编译**

Run: `cd frontend && npm run build`
Expected: 编译成功

- [ ] **Step 6: 提交**

```bash
git add frontend/src/components/WeekSelector.vue
git commit -m "feat: WeekSelector添加预览和结束特殊选项"
```

---

## Task 7: 前端 - 修改ScheduleGrid处理特殊模式

**Files:**
- Modify: `frontend/src/components/ScheduleGrid.vue`

**Interfaces:**
- Consumes: `PREVIEW_WEEK`, `END_WEEK` 常量
- Consumes: `currentWeek` 状态
- Consumes: `allCourses` 状态

- [ ] **Step 1: 导入常量和状态**

```typescript
import { useScheduleStore } from '@/stores/schedule'

const scheduleStore = useScheduleStore()
const { currentWeek, allCourses } = storeToRefs(scheduleStore)
const { PREVIEW_WEEK, END_WEEK } = scheduleStore
```

- [ ] **Step 2: 添加filteredCourses计算属性**

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

- [ ] **Step 3: 添加课程分组函数（处理冲突）**

```typescript
interface CourseGroup {
  dayOfWeek: number
  startSection: number
  endSection: number
  courses: CourseEntry[]
}

const groupedCourses = computed(() => {
  const groups: Map<string, CourseGroup> = new Map()

  for (const course of filteredCourses.value) {
    const key = `${course.dayOfWeek}-${course.startSection}-${course.endSection}`
    if (!groups.has(key)) {
      groups.set(key, {
        dayOfWeek: course.dayOfWeek,
        startSection: course.startSection,
        endSection: course.endSection,
        courses: []
      })
    }
    groups.get(key)!.courses.push(course)
  }

  return Array.from(groups.values())
})
```

- [ ] **Step 4: 修改模板显示逻辑**

```vue
<template>
  <div class="schedule-grid">
    <!-- 结束模式显示空状态 -->
    <div v-if="currentWeek === END_WEEK" class="empty-state">
      <el-empty description="课程结束" />
    </div>

    <!-- 正常显示课程 -->
    <template v-else>
      <div
        v-for="group in groupedCourses"
        :key="`${group.dayOfWeek}-${group.startSection}`"
        class="course-cell"
        :style="getCellStyle(group)"
      >
        <!-- 最多显示2门课 -->
        <template v-if="group.courses.length <= 2">
          <div
            v-for="course in group.courses"
            :key="course.id"
            class="course-card"
            :style="{ width: `${100 / group.courses.length}%`, backgroundColor: course.color }"
          >
            <div class="course-name">{{ course.courseName }}</div>
            <div class="course-info">{{ course.teacher }}</div>
            <div class="course-info">{{ course.location }}</div>
          </div>
        </template>

        <!-- 超过2门课显示折叠 -->
        <template v-else>
          <div
            class="course-card"
            style="width: 50%"
            :style="{ backgroundColor: group.courses[0].color }"
          >
            <div class="course-name">{{ group.courses[0].courseName }}</div>
            <div class="course-info">{{ group.courses[0].teacher }}</div>
          </div>
          <div
            class="course-card conflict-indicator"
            style="width: 50%"
            @click="expandConflict(group)"
          >
            +{{ group.courses.length - 1 }}
          </div>
        </template>
      </div>
    </template>
  </div>
</template>
```

- [ ] **Step 5: 添加展开冲突的处理函数**

```typescript
const expandConflict = (group: CourseGroup) => {
  // 显示一个对话框，列出所有冲突的课程
  ElMessageBox.alert(
    group.courses.map(c => `${c.courseName} - ${c.teacher} - ${c.location}`).join('\n'),
    '课程冲突',
    { confirmButtonText: '确定' }
  )
}
```

- [ ] **Step 6: 验证TypeScript编译**

Run: `cd frontend && npm run build`
Expected: 编译成功

- [ ] **Step 7: 提交**

```bash
git add frontend/src/components/ScheduleGrid.vue
git commit -m "feat: ScheduleGrid支持预览和结束模式"
```

---

## Task 8: 前端 - 修改ScheduleSelector显示所有学期

**Files:**
- Modify: `frontend/src/components/ScheduleSelector.vue`

**Interfaces:**
- Consumes: `semesterList` 状态
- Consumes: `fetchSemesterList()` 方法
- Consumes: `currentSemester` 状态

- [ ] **Step 1: 导入状态和方法**

```typescript
import { useScheduleStore } from '@/stores/schedule'

const scheduleStore = useScheduleStore()
const { semesterList, currentSemester } = storeToRefs(scheduleStore)
const { fetchSemesterList } = scheduleStore
```

- [ ] **Step 2: 在onMounted中获取学期列表**

```typescript
onMounted(async () => {
  try {
    await fetchSemesterList()
  } catch (error) {
    console.error('获取学期列表失败:', error)
  }
})
```

- [ ] **Step 3: 修改模板显示学期列表**

```vue
<template>
  <el-select
    v-model="currentSemester"
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
```

- [ ] **Step 4: 添加学期切换处理函数**

```typescript
const handleSemesterChange = async (semester: string) => {
  const selected = semesterList.value.find(s => s.nameZh === semester)
  if (!selected) return

  if (selected.imported) {
    // 已导入的学期，从本地加载
    await scheduleStore.getScheduleData()
  } else {
    // 未导入的学期，从教务系统获取
    try {
      await scheduleStore.fetchHistoricalSchedule(semester, selected.semesterId)
    } catch (error) {
      ElMessage.error('获取历史课表失败')
    }
  }
}
```

- [ ] **Step 5: 添加样式**

```vue
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
```

- [ ] **Step 6: 验证TypeScript编译**

Run: `cd frontend && npm run build`
Expected: 编译成功

- [ ] **Step 7: 提交**

```bash
git add frontend/src/components/ScheduleSelector.vue
git commit -m "feat: ScheduleSelector显示所有学期并标记已导入"
```

---

## Task 9: 前端 - 在schedule store中添加fetchHistoricalSchedule方法

**Files:**
- Modify: `frontend/src/stores/schedule.ts`

**Interfaces:**
- Produces: `fetchHistoricalSchedule(semester: string, semesterId: number)` - 获取历史课表

- [ ] **Step 1: 添加fetchHistoricalSchedule方法**

```typescript
const fetchHistoricalSchedule = async (semester: string, semesterId: number) => {
  loading.value = true
  try {
    const res = await request.get('/api/schedule/history', {
      params: { semester, semesterId }
    })
    if (res.code === 200) {
      const { schedule, courses } = res.data
      currentSemester.value = semester
      startDate.value = schedule.startDate
      allCourses.value = courses
      weekList.value = generateWeekList(courses)
      maxWeek.value = weekList.value.length
      autoSelectWeek()
    } else {
      throw new Error(res.message)
    }
  } catch (error) {
    console.error('获取历史课表失败:', error)
    throw error
  } finally {
    loading.value = false
  }
}
```

- [ ] **Step 2: 导出新方法**

```typescript
return {
  // ... 现有导出 ...
  fetchHistoricalSchedule
}
```

- [ ] **Step 3: 验证TypeScript编译**

Run: `cd frontend && npm run build`
Expected: 编译成功

- [ ] **Step 4: 提交**

```bash
git add frontend/src/stores/schedule.ts
git commit -m "feat: 添加fetchHistoricalSchedule方法获取历史课表"
```

---

## Task 10: 前端 - 修改Home.vue集成新功能

**Files:**
- Modify: `frontend/src/views/Home.vue`

**Interfaces:**
- Consumes: `semesterList`, `fetchSemesterList` from schedule store

- [ ] **Step 1: 导入新功能**

```typescript
import { useScheduleStore } from '@/stores/schedule'

const scheduleStore = useScheduleStore()
const { semesterList } = storeToRefs(scheduleStore)
const { fetchSemesterList } = scheduleStore
```

- [ ] **Step 2: 在onMounted中获取学期列表**

```typescript
onMounted(async () => {
  // ... 现有代码 ...
  try {
    await fetchSemesterList()
  } catch (error) {
    console.error('获取学期列表失败:', error)
  }
})
```

- [ ] **Step 3: 验证TypeScript编译**

Run: `cd frontend && npm run build`
Expected: 编译成功

- [ ] **Step 4: 提交**

```bash
git add frontend/src/views/Home.vue
git commit -m "feat: Home页面集成学期列表功能"
```

---

## Task 11: 测试 - 完整功能测试

**Files:**
- 无文件修改

- [ ] **Step 1: 启动后端服务**

Run: `cd backend/assistant && ./mvnw spring-boot:run`
Expected: 服务启动成功

- [ ] **Step 2: 启动前端服务**

Run: `cd frontend && npm run dev`
Expected: 服务启动成功

- [ ] **Step 3: 测试特殊选项显示**

1. 打开课表页面
2. 验证WeekSelector显示"预览"和"结束"选项
3. 验证"预览"在最前面，"结束"在最后面

- [ ] **Step 4: 测试自动选择逻辑**

1. 如果当前日期在学期开始前，验证自动选中"预览"
2. 如果当前日期在学期内，验证自动选中当前周
3. 如果当前日期在学期结束后，验证自动选中"结束"

- [ ] **Step 5: 测试预览模式**

1. 选中"预览"选项
2. 验证显示所有周的课程
3. 验证冲突课程并排显示（最多2门）

- [ ] **Step 6: 测试结束模式**

1. 选中"结束"选项
2. 验证课表网格清空
3. 验证显示"课程结束"提示

- [ ] **Step 7: 测试学期切换**

1. 点击学期选择器
2. 验证显示所有学期
3. 验证已导入的学期有勾选标记
4. 切换到未导入的学期，验证自动从教务获取

- [ ] **Step 8: 提交测试结果**

```bash
git commit -m "test: 完成课表增强功能测试" --allow-empty
```

---

## 验收标准检查

- [ ] WeekSelector始终显示预览和结束选项
- [ ] 自动选择逻辑正确：开学前→预览，学期中→当前周，结课后→结束
- [ ] 预览模式显示所有课程，冲突课程正确并排显示
- [ ] 结束模式显示空网格和"课程结束"提示
- [ ] 学期切换功能正常，支持历史学期查询
- [ ] 每个学期只保留一份课表

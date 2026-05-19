package com.educate.assistant.controller;

import com.educate.assistant.common.Result;
import com.educate.assistant.dto.CourseEntryDTO;
import com.educate.assistant.dto.EduLoginRequest;
import com.educate.assistant.dto.ExtractRequest;
import com.educate.assistant.dto.ParseHtmlRequest;
import com.educate.assistant.dto.SaveScheduleRequest;
import com.educate.assistant.service.ScheduleService;
import com.educate.assistant.service.EduProxyService;
import com.educate.assistant.service.ScheduleParser;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/schedule")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final EduProxyService eduProxyService;
    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/current")
    public Result<Map<String, Object>> getCurrentSchedule() {
        Long userId = getCurrentUserId();
        Map<String, Object> data = scheduleService.getScheduleWithCourses(userId);
        return Result.success(data);
    }

    @PostMapping("/login")
    public Result<String> loginToEduSystem(@RequestBody EduLoginRequest request) {
        Long userId = getCurrentUserId();
        try {
            eduProxyService.loginToSchool(userId, request.getUsername(), request.getPassword(), request.getSchoolId());
            return Result.success("登录成功");
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PostMapping("/extract")
    public Result<Map<String, Object>> extractSchedule(@RequestBody ExtractRequest request) {
        Long userId = getCurrentUserId();

        Map<String, String> cookies = eduProxyService.getSessionCookies(userId);
        if (cookies.isEmpty()) {
            return Result.error(400, "未登录教务系统，请先登录");
        }

        String html = eduProxyService.fetchSchedulePage(userId, request.getSchoolId());
        List<Map<String, Object>> courses = ScheduleParser.parse(html);

        Map<String, Object> result = new HashMap<>();
        result.put("semester", request.getSemester());
        result.put("courses", courses);
        return Result.success(result);
    }

    @PostMapping("/parse-html")
    public Result<Map<String, Object>> parseHtml(@RequestBody ParseHtmlRequest request) {
        List<Map<String, Object>> courses = ScheduleParser.parse(request.getHtml());
        Map<String, Object> result = new HashMap<>();
        result.put("semester", request.getSemester());
        result.put("courses", courses);
        return Result.success(result);
    }

    @PostMapping("/save")
    public Result<Void> saveSchedule(@RequestBody SaveScheduleRequest request) {
        Long userId = getCurrentUserId();
        scheduleService.saveSchedule(userId, request);
        return Result.success(null);
    }

    @DeleteMapping
    public Result<Void> deleteSchedule(@RequestParam String semester) {
        Long userId = getCurrentUserId();
        scheduleService.deleteSchedule(userId, semester);
        return Result.success(null);
    }

    @PostMapping("/refresh")
    public Result<String> refreshSchedule() {
        Long userId = getCurrentUserId();

        // 检查教务系统 session
        Map<String, String> cookies = eduProxyService.getSessionCookies(userId);
        if (cookies.isEmpty()) {
            return Result.error(400, "未登录教务系统，请先登录课表导入页面");
        }

        // 获取当前学期的 schedule
        Map<String, Object> data = scheduleService.getScheduleWithCourses(userId);
        @SuppressWarnings("unchecked")
        Map<String, Object> schedule = (Map<String, Object>) data.get("schedule");
        if (schedule == null) {
            return Result.error(400, "暂无课表数据，请先导入课表");
        }

        String semester = (String) schedule.get("semester");

        // 从教务系统 JSON API 获取课表数据
        String jsonData = eduProxyService.fetchScheduleDataApi(userId, "default", 307);
        List<CourseEntryDTO> courses = parseScheduleJson(jsonData);

        // 如果解析到 0 门课程，不保存（避免清空已有数据）
        if (courses.isEmpty()) {
            return Result.error(400, "未解析到课程数据");
        }

        // 提取教务系统返回的当前周次和起始日期
        Integer currentWeek = parseCurrentWeek(jsonData);
        String startDate = parseStartDate(jsonData);

        // 保存到数据库
        SaveScheduleRequest request = new SaveScheduleRequest();
        request.setSemester(semester);
        request.setCourses(courses);
        request.setCurrentWeek(currentWeek);
        request.setStartDate(startDate);
        scheduleService.saveSchedule(userId, request);

        return Result.success("刷新成功，共 " + courses.size() + " 门课程");
    }

    private static final Pattern ENTRY_PATTERN = Pattern.compile(
        "(?:#\\d+\\s*)?(\\S+?)周\\s*(周[一二三四五六日])\\s*第([一二三四五六七八九十]+)节~第([一二三四五六七八九十]+)节\\s*(.*)");

    private static final Map<String, Integer> DAY_MAP = Map.of(
        "周一", 1, "周二", 2, "周三", 3, "周四", 4, "周五", 5, "周六", 6, "周日", 7
    );

    @SuppressWarnings("unchecked")
    private List<CourseEntryDTO> parseScheduleJson(String jsonData) {
        List<CourseEntryDTO> courses = new ArrayList<>();
        if (jsonData == null || jsonData.isEmpty()) return courses;

        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> root = mapper.readValue(jsonData, new TypeReference<Map<String, Object>>() {});

            Object lessonsObj = root.get("lessons");
            if (!(lessonsObj instanceof List)) return courses;
            List<Map<String, Object>> lessons = (List<Map<String, Object>>) lessonsObj;

            for (Map<String, Object> lesson : lessons) {
                // 课程名在 lesson.course.nameZh，lesson.nameZh 是班级名
                String courseName = null;
                Object courseObj = lesson.get("course");
                if (courseObj instanceof Map) {
                    courseName = getStringValue((Map<String, Object>) courseObj, "nameZh");
                }
                if (courseName == null) {
                    courseName = getStringValue(lesson, "nameZh");
                }
                if (courseName == null) continue;

                Object scheduleTextObj = lesson.get("scheduleText");
                if (!(scheduleTextObj instanceof Map)) continue;
                Map<String, Object> scheduleText = (Map<String, Object>) scheduleTextObj;

                // dateTimePlacePersonText 包含完整信息：周次、星期、节次、地点、教师
                // 可能包含多条记录，用 ; 分隔
                String fullText = getNestedText(scheduleText, "dateTimePlacePersonText");
                if (fullText == null || fullText.isBlank()) continue;

                String lastLocation = null;
                String lastTeacher = null;

                // 按 ; 拆分多条课程安排
                String[] entries = fullText.split(";\\s*");
                for (String entry : entries) {
                    entry = entry.trim();
                    if (entry.isEmpty()) continue;

                    Matcher m = ENTRY_PATTERN.matcher(entry);
                    if (!m.find()) continue;

                    String weekStr = m.group(1);
                    Integer dayOfWeek = DAY_MAP.get(m.group(2));
                    Integer startSection = chineseToInt(m.group(3));
                    Integer endSection = chineseToInt(m.group(4));
                    String remainder = m.group(5).trim();
                    if (dayOfWeek == null || startSection == null || endSection == null) continue;

                    String weeks = parseWeekString(weekStr);

                    // remainder = "龙山校区 A507 邹璐" 或 "邹璐"（无地点）
                    // 教师名在最后，地点在中间
                    String location = null;
                    String teacher = null;
                    if (!remainder.isEmpty()) {
                        String[] parts = remainder.split("\\s+");
                        if (parts.length >= 2) {
                            // 有地点和教师：最后一个是教师，前面是地点
                            teacher = parts[parts.length - 1];
                            location = String.join(" ", java.util.Arrays.copyOfRange(parts, 0, parts.length - 1));
                        } else if (parts.length == 1) {
                            // 只有一个词，可能是教师（地点沿用上一条）
                            teacher = parts[0];
                        }
                    }

                    // 沿用上一条的地点/教师
                    if (location == null || location.isEmpty()) location = lastLocation;
                    if (teacher == null || teacher.isEmpty()) teacher = lastTeacher;
                    if (location != null) lastLocation = location;
                    if (teacher != null) lastTeacher = teacher;

                    CourseEntryDTO dto = new CourseEntryDTO();
                    dto.setCourseName(truncate(courseName, 100));
                    dto.setTeacher(truncate(teacher, 50));
                    dto.setLocation(truncate(location, 50));
                    dto.setDayOfWeek(dayOfWeek);
                    dto.setStartSection(startSection);
                    dto.setEndSection(endSection);
                    dto.setWeeks(truncate(weeks, 200));
                    courses.add(dto);
                }
            }

        } catch (Exception e) {
            System.out.println("[ScheduleController] Failed to parse schedule JSON: " + e.getMessage());
        }

        return courses;
    }

    private Integer parseCurrentWeek(String jsonData) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> root = mapper.readValue(jsonData, new TypeReference<Map<String, Object>>() {});
            Object val = root.get("currentWeek");
            if (val instanceof Number) return ((Number) val).intValue();
        } catch (Exception ignored) {}
        return null;
    }

    private String parseStartDate(String jsonData) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> root = mapper.readValue(jsonData, new TypeReference<Map<String, Object>>() {});
            // 教务系统 JSON 可能返回 startDate、firstDay 或 schoolCalendar.startDate
            Object val = root.get("startDate");
            if (val instanceof String) return (String) val;
            // 尝试从 schoolCalendar 中获取
            Object calendar = root.get("schoolCalendar");
            if (calendar instanceof Map) {
                Object firstDay = ((Map<?, ?>) calendar).get("startDate");
                if (firstDay instanceof String) return (String) firstDay;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String getNestedText(Map<String, Object> parent, String key) {
        Object obj = parent.get(key);
        if (!(obj instanceof Map)) return null;
        Object textZh = ((Map<?, ?>) obj).get("textZh");
        return textZh != null ? textZh.toString() : null;
    }

    private String getStringValue(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }

    private String parseWeekString(String weekStr) {
        // 支持格式: "7", "11~12", "1-16", "1,3,5", "1~4,7~17", "1~5,10"
        // 先用 ~ 或 - 作为范围分隔符，再用 , 拆分
        String normalized = weekStr.replace("~", "-");
        String[] parts = normalized.split(",");
        List<String> weeks = new ArrayList<>();
        for (String part : parts) {
            part = part.trim();
            if (part.contains("-")) {
                String[] range = part.split("-");
                if (range.length == 2) {
                    try {
                        int start = Integer.parseInt(range[0].trim());
                        int end = Integer.parseInt(range[1].trim());
                        for (int i = start; i <= end; i++) {
                            weeks.add(String.valueOf(i));
                        }
                    } catch (NumberFormatException ignored) {
                        weeks.add(part);
                    }
                } else {
                    weeks.add(part);
                }
            } else {
                weeks.add(part);
            }
        }
        return String.join(",", weeks);
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }

    private Integer chineseToInt(String chinese) {
        if (chinese == null || chinese.isEmpty()) return null;
        int result = 0;
        for (int i = 0; i < chinese.length(); i++) {
            char c = chinese.charAt(i);
            switch (c) {
                case '一': result += 1; break;
                case '二': result += 2; break;
                case '三': result += 3; break;
                case '四': result += 4; break;
                case '五': result += 5; break;
                case '六': result += 6; break;
                case '七': result += 7; break;
                case '八': result += 8; break;
                case '九': result += 9; break;
                case '十':
                    result = (result == 0 ? 1 : result) * 10;
                    break;
                default: return null;
            }
        }
        return result;
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        return jdbcTemplate.queryForObject(
            "SELECT id FROM user WHERE username = ?", Long.class, username);
    }
}

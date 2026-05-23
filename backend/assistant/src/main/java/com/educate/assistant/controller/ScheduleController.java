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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

    @PostMapping("/share")
    public Result<Map<String, String>> generateShareToken() {
        Long userId = getCurrentUserId();
        try {
            String token = scheduleService.generateShareToken(userId);
            Map<String, String> data = new HashMap<>();
            data.put("token", token);
            data.put("url", "/schedule/share/" + token);
            return Result.success(data);
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @GetMapping("/share/{token}")
    public Result<Map<String, Object>> getSharedSchedule(@PathVariable String token) {
        Map<String, Object> data = scheduleService.getSharedSchedule(token);
        if (data == null) {
            return Result.error(404, "分享链接不存在或已失效");
        }
        return Result.success(data);
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/import-shared")
    public Result<String> importSharedSchedule(@RequestBody Map<String, String> body) {
        Long userId = getCurrentUserId();
        String token = body.get("token");
        if (token == null || token.isEmpty()) {
            return Result.error(400, "缺少分享令牌");
        }

        Map<String, Object> data = scheduleService.getSharedSchedule(token);
        if (data == null) {
            return Result.error(404, "分享链接不存在或已失效");
        }

        Map<String, Object> schedule = (Map<String, Object>) data.get("schedule");
        List<Map<String, Object>> courseMaps = (List<Map<String, Object>>) data.get("courses");

        List<CourseEntryDTO> courses = new ArrayList<>();
        for (Map<String, Object> cm : courseMaps) {
            CourseEntryDTO dto = new CourseEntryDTO();
            dto.setCourseName((String) cm.get("courseName"));
            dto.setTeacher((String) cm.get("teacher"));
            dto.setLocation((String) cm.get("location"));
            dto.setDayOfWeek(cm.get("dayOfWeek") instanceof Number ? ((Number) cm.get("dayOfWeek")).intValue() : null);
            dto.setStartSection(cm.get("startSection") instanceof Number ? ((Number) cm.get("startSection")).intValue() : null);
            dto.setEndSection(cm.get("endSection") instanceof Number ? ((Number) cm.get("endSection")).intValue() : null);
            dto.setWeeks((String) cm.get("weeks"));
            dto.setColor((String) cm.get("color"));
            courses.add(dto);
        }

        SaveScheduleRequest request = new SaveScheduleRequest();
        request.setSemester((String) schedule.get("semester"));
        Object startDateObj = schedule.get("startDate");
        if (startDateObj instanceof java.sql.Date) {
            request.setStartDate(((java.sql.Date) startDateObj).toString());
        } else if (startDateObj instanceof String) {
            request.setStartDate((String) startDateObj);
        }
        request.setCourses(courses);
        scheduleService.saveSchedule(userId, request);

        return Result.success("导入成功");
    }

    private static final Pattern ENTRY_PATTERN = Pattern.compile(
        "(?:#(\\d+)\\s*)?(\\S+?)周\\s*(周[一二三四五六日])\\s*第([一二三四五六七八九十]+)节~第([一二三四五六七八九十]+)节\\s*(.*)");

    private static final Map<String, Integer> DAY_MAP = Map.of(
        "周一", 1, "周二", 2, "周三", 3, "周四", 4, "周五", 5, "周六", 6, "周日", 7
    );

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

        try {
        // Step 1: 从 get-data API 获取 lessons 列表
        String jsonData = eduProxyService.fetchScheduleDataApi(userId, "default", 307);
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> getDataRoot = mapper.readValue(jsonData, new TypeReference<Map<String, Object>>() {});

        // 提取免听课程 ID
        Set<Integer> notAttendIds = new HashSet<>();
        Object notAttendObj = getDataRoot.get("notAttendLessonIds");
        if (notAttendObj instanceof List) {
            for (Object id : (List<?>) notAttendObj) {
                if (id instanceof Number) notAttendIds.add(((Number) id).intValue());
            }
        }
        System.out.println("[Schedule] notAttendLessonIds: " + notAttendIds);

        List<Integer> lessonIds = new ArrayList<>();
        Object lessonsObj = getDataRoot.get("lessons");
        if (lessonsObj instanceof List) {
            for (Object lesson : (List<?>) lessonsObj) {
                if (lesson instanceof Map) {
                    Object id = ((Map<?, ?>) lesson).get("id");
                    if (id instanceof Number) lessonIds.add(((Number) id).intValue());
                }
            }
        }
        if (lessonIds.isEmpty()) {
            return Result.error(400, "未获取到课程 ID 列表");
        }

        // Step 2: 调用 datum API，收集学生实际的 (lessonId, weekday) 组合 + startDate
        int stdPersonId = eduProxyService.getStdPersonId(userId, "default");
        String datumJson = eduProxyService.fetchDatumApi(userId, "default", lessonIds, stdPersonId);
        Map<String, Object> datumRoot = mapper.readValue(datumJson, new TypeReference<Map<String, Object>>() {});

        // 收集学生实际的 (lessonId, weekday) 组合 + 教师/教室信息
        Set<String> studentSlots = new HashSet<>();
        // key: "lessonId-weekday" → value: [teacher, location]
        Map<String, String[]> slotInfo = new HashMap<>();
        String startDate = null;
        Object resultObj = datumRoot.get("result");
        if (resultObj instanceof Map) {
            Object scheduleListObj = ((Map<?, ?>) resultObj).get("scheduleList");
            if (scheduleListObj instanceof List) {
                for (Object item : (List<?>) scheduleListObj) {
                    if (item instanceof Map) {
                        Map<?, ?> e = (Map<?, ?>) item;
                        Object lidObj = e.get("lessonId");
                        Object wdObj = e.get("weekday");
                        if (lidObj instanceof Number && wdObj instanceof Number) {
                            int lid = ((Number) lidObj).intValue();
                            int wd = ((Number) wdObj).intValue();
                            String key = lid + "-" + wd;
                            studentSlots.add(key);

                            // 提取教师和教室（只需存一次）
                            if (!slotInfo.containsKey(key)) {
                                String teacher = e.get("personName") != null ? e.get("personName").toString() : "";
                                String location = "";
                                Object roomObj = e.get("room");
                                if (roomObj instanceof Map) {
                                    Map<?, ?> room = (Map<?, ?>) roomObj;
                                    String roomName = room.get("nameZh") != null ? room.get("nameZh").toString() : "";
                                    String campus = "";
                                    Object building = room.get("building");
                                    if (building instanceof Map) {
                                        Object campusObj = ((Map<?, ?>) building).get("campus");
                                        if (campusObj instanceof Map) {
                                            campus = ((Map<?, ?>) campusObj).get("nameZh") != null
                                                ? ((Map<?, ?>) campusObj).get("nameZh").toString() : "";
                                        }
                                    }
                                    location = (campus + " " + roomName).trim();
                                }
                                slotInfo.put(key, new String[]{teacher, location});
                            }
                        }
                        // 提取 startDate
                        Object weekIdx = e.get("weekIndex");
                        Object dateObj = e.get("date");
                        if (weekIdx instanceof Number && ((Number) weekIdx).intValue() == 1
                            && dateObj instanceof String) {
                            String d = (String) dateObj;
                            if (!d.isEmpty() && (startDate == null || d.compareTo(startDate) < 0)) {
                                startDate = d;
                            }
                        }
                    }
                }
            }
        }
        System.out.println("[Schedule] studentSlots size: " + studentSlots.size() + ", startDate: " + startDate);

        // Step 3: 从文本提取节次，教师/教室从 datum API 取
        List<CourseEntryDTO> courses = new ArrayList<>();
        if (lessonsObj instanceof List) {
            for (Object lesson : (List<?>) lessonsObj) {
                if (!(lesson instanceof Map)) continue;
                Map<?, ?> lm = (Map<?, ?>) lesson;
                Object idObj = lm.get("id");
                if (!(idObj instanceof Number)) continue;
                int lessonId = ((Number) idObj).intValue();

                // 跳过免听课程
                if (notAttendIds.contains(lessonId)) continue;

                // 课程名
                String courseName = null;
                Object courseObj = lm.get("course");
                if (courseObj instanceof Map) {
                    Object n = ((Map<?, ?>) courseObj).get("nameZh");
                    if (n != null) courseName = n.toString();
                }
                if (courseName == null) {
                    Object n = lm.get("nameZh");
                    if (n != null) courseName = n.toString();
                }
                if (courseName == null) continue;

                // 解析 dateTimeText（只需星期和节次，不需要地点/教师）
                Object scheduleTextObj = lm.get("scheduleText");
                if (!(scheduleTextObj instanceof Map)) continue;
                // 用 dateTimeText 而非 dateTimePlacePersonText，更简洁
                Object dtObj = ((Map<?, ?>) scheduleTextObj).get("dateTimeText");
                String fullText = null;
                if (dtObj instanceof Map) {
                    Object t = ((Map<?, ?>) dtObj).get("textZh");
                    if (t != null) fullText = t.toString();
                }
                if (fullText == null || fullText.isBlank()) continue;

                String[] entries = fullText.split(";\\s*");
                for (String entry : entries) {
                    entry = entry.trim();
                    if (entry.isEmpty()) continue;

                    Matcher m = ENTRY_PATTERN.matcher(entry);
                    if (!m.find()) continue;

                    Integer dayOfWeek = DAY_MAP.get(m.group(3));
                    if (dayOfWeek == null) continue;

                    // 用 datum API 过滤
                    String slotKey = lessonId + "-" + dayOfWeek;
                    if (!studentSlots.contains(slotKey)) continue;

                    Integer startSection = chineseToInt(m.group(4));
                    Integer endSection = chineseToInt(m.group(5));
                    if (startSection == null || endSection == null) continue;

                    String weeks = parseWeekString(m.group(2));

                    // 教师和教室从 datum API 取
                    String[] info = slotInfo.getOrDefault(slotKey, new String[]{"", ""});

                    CourseEntryDTO dto = new CourseEntryDTO();
                    dto.setCourseName(courseName);
                    dto.setTeacher(info[0]);
                    dto.setLocation(info[1]);
                    dto.setDayOfWeek(dayOfWeek);
                    dto.setStartSection(startSection);
                    dto.setEndSection(endSection);
                    dto.setWeeks(weeks);
                    courses.add(dto);
                }
            }
        }

        // 去重合并：相同 (courseName, teacher, dayOfWeek, startSection, endSection) 的条目合并 weeks
        Map<String, CourseEntryDTO> merged = new LinkedHashMap<>();
        for (CourseEntryDTO dto : courses) {
            String key = dto.getCourseName() + "|" + dto.getTeacher() + "|"
                + dto.getDayOfWeek() + "|" + dto.getStartSection() + "|" + dto.getEndSection();
            CourseEntryDTO existing = merged.get(key);
            if (existing == null) {
                merged.put(key, dto);
            } else {
                Set<Integer> allWeeks = new TreeSet<>();
                for (String w : existing.getWeeks().split(",")) {
                    w = w.trim();
                    if (!w.isEmpty()) allWeeks.add(Integer.parseInt(w));
                }
                for (String w : dto.getWeeks().split(",")) {
                    w = w.trim();
                    if (!w.isEmpty()) allWeeks.add(Integer.parseInt(w));
                }
                existing.setWeeks(allWeeks.stream().map(String::valueOf).collect(Collectors.joining(",")));
            }
        }
        courses = new ArrayList<>(merged.values());

        if (courses.isEmpty()) {
            return Result.error(400, "未解析到课程数据");
        }

        System.out.println("[Schedule] Refreshed: " + courses.size() + " courses, startDate: " + startDate);

        // 保存到数据库
        SaveScheduleRequest request = new SaveScheduleRequest();
        request.setSemester(semester);
        request.setCourses(courses);
        request.setStartDate(startDate);
        scheduleService.saveSchedule(userId, request);

        return Result.success("刷新成功，共 " + courses.size() + " 门课程");

        } catch (Exception e) {
            System.out.println("[Schedule] Refresh failed: " + e.getMessage());
            e.printStackTrace();
            return Result.error(500, "刷新失败: " + e.getMessage());
        }
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        return jdbcTemplate.queryForObject(
            "SELECT id FROM user WHERE username = ?", Long.class, username);
    }

    private String parseWeekString(String weekStr) {
        String normalized = weekStr.replace("~", "-");
        String[] parts = normalized.split(",");
        List<String> weeks = new ArrayList<>();
        for (String part : parts) {
            part = part.trim();
            String parity = null;
            // 统一处理全角 （单）（双） 和半角 (单)(双) 括号
            if (part.matches(".*[（(]单[）)]")) {
                parity = "odd";
                part = part.replaceAll("[（(]单[）)]", "");
            } else if (part.matches(".*[（(]双[）)]")) {
                parity = "even";
                part = part.replaceAll("[（(]双[）)]", "");
            }
            if (part.contains("-")) {
                String[] range = part.split("-");
                if (range.length == 2) {
                    try {
                        int start = Integer.parseInt(range[0].trim());
                        int end = Integer.parseInt(range[1].trim());
                        for (int i = start; i <= end; i++) {
                            if (parity == null
                                || ("odd".equals(parity) && i % 2 == 1)
                                || ("even".equals(parity) && i % 2 == 0)) {
                                weeks.add(String.valueOf(i));
                            }
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

    private Integer chineseToInt(String s) {
        if (s == null || s.isEmpty()) return null;
        Map<Character, Integer> map = Map.of(
            '一', 1, '二', 2, '三', 3, '四', 4, '五', 5,
            '六', 6, '七', 7, '八', 8, '九', 9
        );
        if (s.length() == 1) return map.getOrDefault(s.charAt(0), null);
        if ("十".equals(s)) return 10;
        if (s.startsWith("十")) {
            Integer r = map.getOrDefault(s.charAt(1), null);
            return r != null ? 10 + r : null;
        }
        if (s.endsWith("十")) {
            Integer l = map.getOrDefault(s.charAt(0), null);
            return l != null ? l * 10 : null;
        }
        if (s.length() == 2 && s.charAt(1) == '十') {
            Integer l = map.getOrDefault(s.charAt(0), null);
            return l != null ? l * 10 : null;
        }
        if (s.length() == 2 && s.charAt(0) == '十') {
            Integer r = map.getOrDefault(s.charAt(1), null);
            return r != null ? 10 + r : null;
        }
        return null;
    }

}

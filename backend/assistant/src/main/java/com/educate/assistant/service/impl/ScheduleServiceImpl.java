package com.educate.assistant.service.impl;

import com.educate.assistant.dto.CourseEntryDTO;
import com.educate.assistant.dto.SaveScheduleRequest;
import com.educate.assistant.service.ScheduleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class ScheduleServiceImpl implements ScheduleService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String[] COLORS = {
        "#409EFF", "#67C23A", "#E6A23C", "#F56C6C", "#909399",
        "#B37FEB", "#36CFC9", "#FF85C0", "#597EF7", "#FFC53D"
    };

    @Override
    public Map<String, Object> getScheduleWithCourses(Long userId) {
        List<Map<String, Object>> schedules = jdbcTemplate.queryForList(
            "SELECT * FROM schedule WHERE user_id = ? ORDER BY updated_at DESC LIMIT 1",
            userId);

        Map<String, Object> result = new HashMap<>();
        if (schedules.isEmpty()) {
            result.put("schedule", null);
            result.put("courses", List.of());
            return result;
        }

        Map<String, Object> schedule = toCamelCase(schedules.get(0));
        Long scheduleId = ((Number) schedules.get(0).get("id")).longValue();

        List<Map<String, Object>> courses = jdbcTemplate.queryForList(
            "SELECT * FROM course_entry WHERE schedule_id = ? ORDER BY day_of_week, start_section",
            scheduleId).stream().map(this::toCamelCase).toList();

        result.put("schedule", schedule);
        result.put("courses", courses);
        return result;
    }

    private Map<String, Object> toCamelCase(Map<String, Object> row) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String key = entry.getKey();
            if (key.contains("_")) {
                StringBuilder sb = new StringBuilder();
                boolean upper = false;
                for (char c : key.toCharArray()) {
                    if (c == '_') {
                        upper = true;
                    } else {
                        sb.append(upper ? Character.toUpperCase(c) : c);
                        upper = false;
                    }
                }
                key = sb.toString();
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    @Override
    @Transactional
    public void saveSchedule(Long userId, SaveScheduleRequest request) {
        // Derive academicYear from semester if not provided (e.g. "2025-2026-2" → "2025-2026")
        String academicYear = request.getAcademicYear();
        if (academicYear == null || academicYear.isEmpty()) {
            String sem = request.getSemester();
            int lastDash = sem.lastIndexOf('-');
            academicYear = lastDash > 0 ? sem.substring(0, lastDash) : sem;
        }

        // Delete existing schedule for this semester
        jdbcTemplate.update(
            "DELETE FROM schedule WHERE user_id = ? AND semester = ?",
            userId, request.getSemester());

        // Insert new schedule
        java.sql.Date startDateSql = null;
        if (request.getStartDate() != null && !request.getStartDate().isEmpty()) {
            startDateSql = java.sql.Date.valueOf(request.getStartDate());
        }
        jdbcTemplate.update(
            "INSERT INTO schedule (user_id, school_id, semester, academic_year, start_date) VALUES (?, ?, ?, ?, ?)",
            userId, request.getSchoolId(), request.getSemester(), academicYear, startDateSql);

        Long scheduleId = jdbcTemplate.queryForObject(
            "SELECT LAST_INSERT_ID()", Long.class);

        // Batch insert course entries
        String insertSql = "INSERT INTO course_entry " +
            "(schedule_id, course_name, teacher, location, day_of_week, start_section, end_section, weeks, color) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        List<Object[]> batchArgs = new ArrayList<>();
        int colorIdx = 0;
        for (CourseEntryDTO c : request.getCourses()) {
            String color = COLORS[colorIdx % COLORS.length];
            batchArgs.add(new Object[]{
                scheduleId, c.getCourseName(), c.getTeacher(), c.getLocation(),
                c.getDayOfWeek(), c.getStartSection(), c.getEndSection(),
                c.getWeeks(), color
            });
            colorIdx++;
        }
        jdbcTemplate.batchUpdate(insertSql, batchArgs);
    }

    @Override
    public void deleteSchedule(Long userId, String semester) {
        jdbcTemplate.update(
            "DELETE FROM schedule WHERE user_id = ? AND semester = ?", userId, semester);
    }

    @Override
    public String generateShareToken(Long userId) {
        // 查找用户最新的课表
        List<Map<String, Object>> schedules = jdbcTemplate.queryForList(
            "SELECT id, share_token FROM schedule WHERE user_id = ? ORDER BY updated_at DESC LIMIT 1",
            userId);
        if (schedules.isEmpty()) {
            throw new RuntimeException("暂无课表数据，请先导入课表");
        }

        Map<String, Object> schedule = schedules.get(0);
        Long scheduleId = ((Number) schedule.get("id")).longValue();
        String existingToken = (String) schedule.get("share_token");

        // 如果已有 token，直接返回
        if (existingToken != null && !existingToken.isEmpty()) {
            return existingToken;
        }

        // 生成 8 位 Base62 token
        String token = generateBase62Token(8);
        jdbcTemplate.update("UPDATE schedule SET share_token = ? WHERE id = ?", token, scheduleId);
        return token;
    }

    @Override
    public Map<String, Object> getSharedSchedule(String token) {
        List<Map<String, Object>> schedules = jdbcTemplate.queryForList(
            "SELECT * FROM schedule WHERE share_token = ?", token);
        if (schedules.isEmpty()) {
            return null;
        }

        Map<String, Object> schedule = toCamelCase(schedules.get(0));
        Long scheduleId = ((Number) schedules.get(0).get("id")).longValue();

        List<Map<String, Object>> courses = jdbcTemplate.queryForList(
            "SELECT * FROM course_entry WHERE schedule_id = ? ORDER BY day_of_week, start_section",
            scheduleId).stream().map(this::toCamelCase).toList();

        Map<String, Object> result = new HashMap<>();
        result.put("schedule", schedule);
        result.put("courses", courses);
        return result;
    }

    private String generateBase62Token(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}

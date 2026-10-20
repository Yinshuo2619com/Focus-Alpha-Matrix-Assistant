package com.educate.assistant.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 往期课表只读语义的回归测试。
 *
 * <p>前端 ScheduleGrid 依赖 camelCase 字段名，而 JdbcTemplate 返回的是数据库原始列名
 * （snake_case）。这里同时锁住"字段名正确"和"只读"两个不变量。
 */
class ScheduleServiceImplTest {

    /** 桩：记录执行过的 SQL，并按表名返回预置数据行 */
    private static class StubJdbcTemplate extends JdbcTemplate {
        private final List<Map<String, Object>> scheduleRows;
        private final List<Map<String, Object>> courseRows;
        private final List<String> executedSql = new ArrayList<>();

        StubJdbcTemplate(List<Map<String, Object>> scheduleRows, List<Map<String, Object>> courseRows) {
            this.scheduleRows = scheduleRows;
            this.courseRows = courseRows;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            executedSql.add(sql);
            if (sql.contains("FROM schedule")) {
                return scheduleRows;
            }
            if (sql.contains("FROM course_entry")) {
                return courseRows;
            }
            return List.of();
        }
    }

    private static Map<String, Object> row(Object... keyValues) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            row.put((String) keyValues[i], keyValues[i + 1]);
        }
        return row;
    }

    private static ScheduleServiceImpl serviceWith(StubJdbcTemplate jdbcTemplate) {
        ScheduleServiceImpl service = new ScheduleServiceImpl();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        return service;
    }

    @Test
    void importedSemesterReturnsCamelCaseSchedule() {
        Map<String, Object> scheduleRow = row(
            "id", 7L, "user_id", 1L, "semester", "2025-2026-1",
            "academic_year", "2025-2026", "start_date", java.sql.Date.valueOf("2025-09-01"));
        Map<String, Object> courseRow = row(
            "id", 11L, "schedule_id", 7L, "course_name", "高等数学",
            "teacher", "张三", "location", "A101", "day_of_week", 1,
            "start_section", 1, "end_section", 2, "weeks", "1-16", "color", "#409EFF");

        StubJdbcTemplate jdbcTemplate = new StubJdbcTemplate(List.of(scheduleRow), List.of(courseRow));

        Map<String, Object> result = serviceWith(jdbcTemplate).getScheduleBySemester(1L, "2025-2026-1");

        @SuppressWarnings("unchecked")
        Map<String, Object> schedule = (Map<String, Object>) result.get("schedule");
        assertNotNull(schedule);
        assertEquals("2025-2026-1", schedule.get("semester"));
        assertEquals("2025-2026", schedule.get("academicYear"));
        assertEquals(java.sql.Date.valueOf("2025-09-01"), schedule.get("startDate"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> courses = (List<Map<String, Object>>) result.get("courses");
        assertEquals(1, courses.size());
        Map<String, Object> course = courses.get(0);
        assertEquals("高等数学", course.get("courseName"));
        assertEquals("张三", course.get("teacher"));
        assertEquals("A101", course.get("location"));
        assertEquals(1, course.get("dayOfWeek"));
        assertEquals(1, course.get("startSection"));
        assertEquals(2, course.get("endSection"));
        assertEquals("1-16", course.get("weeks"));
        assertNull(course.get("course_name"), "不应残留 snake_case 字段，前端读不到");
        assertNull(course.get("day_of_week"), "不应残留 snake_case 字段，前端读不到");
    }

    @Test
    void notImportedSemesterReturnsEmptyResultInsteadOfFailing() {
        StubJdbcTemplate jdbcTemplate = new StubJdbcTemplate(List.of(), List.of());

        Map<String, Object> result = serviceWith(jdbcTemplate).getScheduleBySemester(1L, "2024-2025-2");

        assertNull(result.get("schedule"));
        assertEquals(List.of(), result.get("courses"));
    }

    @Test
    void readingHistoricalScheduleIssuesNoWrites() {
        Map<String, Object> scheduleRow = row("id", 7L, "user_id", 1L, "semester", "2025-2026-1");
        StubJdbcTemplate jdbcTemplate = new StubJdbcTemplate(List.of(scheduleRow), List.of());

        serviceWith(jdbcTemplate).getScheduleBySemester(1L, "2025-2026-1");

        List<String> writes = jdbcTemplate.executedSql.stream()
            .filter(sql -> {
                String normalized = sql.trim().toUpperCase(Locale.ROOT);
                return normalized.startsWith("INSERT")
                    || normalized.startsWith("UPDATE")
                    || normalized.startsWith("DELETE")
                    || normalized.startsWith("REPLACE");
            })
            .toList();
        assertTrue(writes.isEmpty(), "查看往期课表不应写库，实际执行了: " + writes);
    }
}

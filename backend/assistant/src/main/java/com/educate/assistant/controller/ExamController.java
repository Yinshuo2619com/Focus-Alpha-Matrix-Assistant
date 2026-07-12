package com.educate.assistant.controller;

import com.educate.assistant.common.Result;
import com.educate.assistant.service.EduProxyService;
import com.educate.assistant.service.ExamParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/exam")
@RequiredArgsConstructor
public class ExamController {

    private final EduProxyService eduProxyService;
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String CACHE_KEY_PREFIX = "exam:";
    private static final int EXPIRE_DAYS_AFTER_LAST_EXAM = 30;

    @GetMapping("/list")
    public Result<List<Map<String, String>>> getExamList(
            @RequestParam(defaultValue = "false") boolean forceRefresh) {
        Long userId = getCurrentUserId();
        String cacheKey = CACHE_KEY_PREFIX + userId;

        try {
            // 非强制刷新时，尝试从缓存读取
            if (!forceRefresh) {
                String cached = redisTemplate.opsForValue().get(cacheKey);
                if (cached != null && !cached.isEmpty()) {
                    List<Map<String, String>> exams = objectMapper.readValue(
                        cached, new TypeReference<List<Map<String, String>>>() {});
                    System.out.println("[Exam] Cache hit for user " + userId + ", " + exams.size() + " exams");
                    return Result.success(exams);
                }
            }

            // 从教务系统获取
            System.out.println("[Exam] Fetching from edu system for user " + userId);
            String html = eduProxyService.fetchExamPage(userId, "default");
            List<Map<String, String>> exams = ExamParser.parse(html);

            // 计算TTL并存入Redis
            long ttlSeconds = calculateTtlSeconds(exams);
            if (ttlSeconds > 0) {
                String json = objectMapper.writeValueAsString(exams);
                redisTemplate.opsForValue().set(cacheKey, json, ttlSeconds, TimeUnit.SECONDS);
                System.out.println("[Exam] Cached " + exams.size() + " exams for user " + userId
                    + ", TTL: " + ttlSeconds + "s (" + (ttlSeconds / 86400) + " days)");
            }

            return Result.success(exams);
        } catch (Exception e) {
            // 静默处理错误，返回空列表
            System.out.println("[Exam] Failed to get exam list: " + e.getMessage());
            return Result.success(List.of());
        }
    }

    /**
     * 计算缓存TTL（秒）
     * 策略：最后考试日期 +30天 - 当前时间
     */
    private long calculateTtlSeconds(List<Map<String, String>> exams) {
        if (exams == null || exams.isEmpty()) {
            return 0;
        }

        // 提取所有考试日期
        List<LocalDate> dates = exams.stream()
            .map(e -> e.get("dateTime"))
            .filter(d -> d != null && !d.isEmpty())
            .map(this::parseDate)
            .filter(d -> d != null)
            .collect(Collectors.toList());

        if (dates.isEmpty()) {
            return 0;
        }

        // 获取最后考试日期
        LocalDate lastExamDate = dates.stream().max(LocalDate::compareTo).orElse(null);
        if (lastExamDate == null) {
            return 0;
        }

        // 计算过期时间：最后考试日期 +30天
        LocalDate expireDate = lastExamDate.plusDays(EXPIRE_DAYS_AFTER_LAST_EXAM);
        LocalDate today = LocalDate.now();

        // 如果已过期，返回0（不缓存）
        if (today.isAfter(expireDate)) {
            return 0;
        }

        // 计算剩余秒数
        long daysUntilExpire = ChronoUnit.DAYS.between(today, expireDate);
        return daysUntilExpire * 86400; // 转换为秒
    }

    /**
     * 解析日期字符串，支持 "2026-06-29" 和 "2026年6月29日" 格式
     */
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }

        // 尝试解析 "2026-06-29" 格式
        try {
            return LocalDate.parse(dateStr.split(" ")[0], DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception ignored) {}

        // 尝试解析 "2026年6月29日" 格式
        try {
            String cleaned = dateStr.replace("年", "-").replace("月", "-").replace("日", "");
            String[] parts = cleaned.split("-");
            if (parts.length >= 3) {
                return LocalDate.of(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2])
                );
            }
        } catch (Exception ignored) {}

        return null;
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        return jdbcTemplate.queryForObject(
            "SELECT id FROM user WHERE username = ?", Long.class, username);
    }
}

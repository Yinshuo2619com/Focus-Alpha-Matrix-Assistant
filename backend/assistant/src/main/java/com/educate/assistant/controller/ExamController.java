package com.educate.assistant.controller;

import com.educate.assistant.common.Result;
import com.educate.assistant.service.EduProxyService;
import com.educate.assistant.service.ExamParser;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/exam")
@RequiredArgsConstructor
public class ExamController {

    private final EduProxyService eduProxyService;
    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/list")
    public Result<List<Map<String, String>>> getExamList() {
        Long userId = getCurrentUserId();

        try {
            String html = eduProxyService.fetchExamPage(userId, "default");
            List<Map<String, String>> exams = ExamParser.parse(html);
            return Result.success(exams);
        } catch (Exception e) {
            // 静默处理错误，返回空列表
            System.out.println("[Exam] Failed to get exam list: " + e.getMessage());
            return Result.success(List.of());
        }
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        return jdbcTemplate.queryForObject(
            "SELECT id FROM user WHERE username = ?", Long.class, username);
    }
}

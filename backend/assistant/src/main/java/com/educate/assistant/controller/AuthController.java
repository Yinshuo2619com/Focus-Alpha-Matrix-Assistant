package com.educate.assistant.controller;

import com.educate.assistant.common.JwtUtil;
import com.educate.assistant.common.JwtBlacklistService;
import com.educate.assistant.common.Result;
import com.educate.assistant.dto.LoginRequest;
import com.educate.assistant.dto.RegisterRequest;
import com.educate.assistant.service.UserService;
import com.educate.assistant.service.EduProxyService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final JwtBlacklistService jwtBlacklistService;
    private final EduProxyService eduProxyService;
    private final JdbcTemplate jdbcTemplate;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequest loginRequest){
        try {
            Map<String, Object> result = userService.login(loginRequest);
            return Result.success(result);
        } catch (RuntimeException e){
            return Result.fail(e.getMessage());
        }
    }

@PostMapping("/register")
    public Result<String> register(@RequestBody RegisterRequest registerRequest){
        try {
            userService.register(registerRequest);
            return Result.success("注册成功");
        } catch (RuntimeException e){
            return Result.fail(e.getMessage());
        }
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            String token = bearerToken.substring(7);
            long remaining = jwtUtil.getRemainingExpiration(token);
            if (remaining > 0) {
                jwtBlacklistService.blacklist(token, remaining);
            }
            // 清除教务系统 session
            try {
                String username = jwtUtil.getUsernameFromToken(token);
                Long userId = jdbcTemplate.queryForObject(
                    "SELECT id FROM user WHERE username = ?", Long.class, username);
                if (userId != null) {
                    eduProxyService.clearSession(userId);
                }
            } catch (Exception ignored) {}
        }
        return Result.success(null);
    }
}

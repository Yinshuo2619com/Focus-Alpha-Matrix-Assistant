package com.educate.assistant.controller;

import com.educate.assistant.common.Result;
import com.educate.assistant.dto.ChangePasswordRequest;
import com.educate.assistant.dto.UpdateProfileRequest;
import com.educate.assistant.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/avatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();

            // 从数据库获取用户ID
            Long userId = userService.findUserIdByUsername(username);

            String avatarUrl = userService.uploadAvatar(userId, username, file);

            return Result.success(avatarUrl);
        } catch (RuntimeException e) {
            return Result.error(500, e.getMessage());
        }
    }

    @PutMapping("/profile")
    public Result<Map<String, Object>> updateProfile(@RequestBody UpdateProfileRequest request) {
        try {
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            String newToken = userService.updateProfile(username, request);
            Map<String, Object> data = new HashMap<>();
            if (newToken != null) {
                data.put("token", newToken);
            }
            return Result.success(data.isEmpty() ? null : data);
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }

    @PutMapping("/password")
    public Result<Void> changePassword(@RequestBody ChangePasswordRequest request) {
        try {
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            userService.changePassword(username, request);
            return Result.success(null);
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }

    @GetMapping("/check-username")
    public Result<Boolean> checkUsername(@RequestParam String username) {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        boolean available = userService.checkUsernameAvailable(username, currentUsername);
        return Result.success(available);
    }

    @GetMapping("/check-phone")
    public Result<Boolean> checkPhone(@RequestParam String phone) {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        boolean available = userService.checkPhoneAvailable(phone, currentUsername);
        return Result.success(available);
    }
}
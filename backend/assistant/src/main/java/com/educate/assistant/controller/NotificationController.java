package com.educate.assistant.controller;

import com.educate.assistant.common.Result;
import com.educate.assistant.service.NotificationService;
import com.educate.assistant.service.RecommendService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final RecommendService recommendService;

    @GetMapping("/notifications")
    public Result<List<Map<String, Object>>> getNotifications() {
        Long userId = getCurrentUserId();
        return Result.success(notificationService.getNotifications(userId));
    }

    @GetMapping("/notifications/unread-count")
    public Result<Integer> getUnreadCount() {
        Long userId = getCurrentUserId();
        return Result.success(notificationService.getUnreadCount(userId));
    }

    @PostMapping("/notifications/read-all")
    public Result<Void> markAllAsRead() {
        Long userId = getCurrentUserId();
        notificationService.markAllAsRead(userId);
        return Result.success(null);
    }

    @PostMapping("/notifications/read")
    public Result<Void> markGroupAsRead(@RequestBody Map<String, Object> body) {
        Long userId = getCurrentUserId();
        String type = (String) body.get("type");
        Long targetId = Long.valueOf(body.get("targetId").toString());
        notificationService.markGroupAsRead(userId, type, targetId);
        return Result.success(null);
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        return recommendService.getUserIdByUsername(username);
    }
}

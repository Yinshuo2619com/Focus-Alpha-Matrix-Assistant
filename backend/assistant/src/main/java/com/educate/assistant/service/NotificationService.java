package com.educate.assistant.service;

import com.educate.assistant.websocket.NotificationWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final JdbcTemplate jdbcTemplate;

    public void createNotification(Long userId, Long actorId, String type, Long targetId, String targetType) {
        if (userId.equals(actorId)) return;
        String sql = "INSERT INTO user_notification (user_id, actor_id, type, target_id, target_type, is_read, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, FALSE, NOW()) " +
                     "ON DUPLICATE KEY UPDATE created_at = NOW(), is_read = FALSE";
        jdbcTemplate.update(sql, userId, actorId, type, targetId, targetType);

        int unreadCount = getUnreadCount(userId);
        NotificationWebSocketHandler.sendUnreadCount(userId, unreadCount);
    }

    public List<Map<String, Object>> getNotifications(Long userId) {
        String sql = "SELECT n.id, n.type, n.target_id, n.target_type, n.is_read, n.created_at, " +
                     "u.id AS actor_id, u.nickname AS actor_name, u.avatar AS actor_avatar " +
                     "FROM user_notification n " +
                     "LEFT JOIN user u ON n.actor_id = u.id " +
                     "WHERE n.user_id = ? " +
                     "ORDER BY n.created_at DESC";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, userId);

        Map<String, Map<String, Object>> groupMap = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String type = (String) row.get("type");
            Long targetId = ((Number) row.get("target_id")).longValue();
            String targetType = (String) row.get("target_type");
            String groupKey = type + ":" + targetId + ":" + targetType;

            Map<String, Object> group = groupMap.get(groupKey);
            if (group == null) {
                group = new LinkedHashMap<>();
                group.put("type", type);
                group.put("targetId", targetId);
                group.put("targetType", targetType);
                group.put("isRead", row.get("is_read"));
                group.put("latestTime", row.get("created_at"));
                group.put("actors", new ArrayList<>());
                group.put("preview", getPreview(targetId, targetType));
                if ("COMMENT".equals(targetType)) {
                    Long recommendId = getRecommendIdByCommentId(targetId);
                    group.put("recommendId", recommendId);
                } else {
                    group.put("recommendId", targetId);
                }
                groupMap.put(groupKey, group);
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> actors = (List<Map<String, Object>>) group.get("actors");
            Map<String, Object> actor = new HashMap<>();
            actor.put("id", row.get("actor_id"));
            actor.put("nickname", row.get("actor_name"));
            actor.put("avatar", row.get("actor_avatar"));
            actors.add(actor);

            if ((Boolean) row.get("is_read")) {
                group.put("isRead", true);
            } else {
                group.put("isRead", false);
            }
        }

        return new ArrayList<>(groupMap.values());
    }

    public int getUnreadCount(Long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_notification WHERE user_id = ? AND is_read = FALSE",
                Integer.class, userId);
        return count != null ? count : 0;
    }

    public void markAllAsRead(Long userId) {
        jdbcTemplate.update("UPDATE user_notification SET is_read = TRUE WHERE user_id = ? AND is_read = FALSE", userId);
    }

    public void markGroupAsRead(Long userId, String type, Long targetId) {
        jdbcTemplate.update("UPDATE user_notification SET is_read = TRUE WHERE user_id = ? AND type = ? AND target_id = ? AND is_read = FALSE",
                userId, type, targetId);
    }

    public void deleteByTarget(Long targetId, String targetType) {
        jdbcTemplate.update("DELETE FROM user_notification WHERE target_id = ? AND target_type = ?", targetId, targetType);
    }

    private String getPreview(Long targetId, String targetType) {
        if ("COMMENT".equals(targetType)) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT content FROM user_comment WHERE id = ?", targetId);
            if (!rows.isEmpty()) {
                String content = (String) rows.get(0).get("content");
                return content != null && content.length() > 50 ? content.substring(0, 50) + "..." : content;
            }
        } else if ("ARTICLE".equals(targetType)) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT title FROM user_recommendation WHERE id = ?", targetId);
            if (!rows.isEmpty()) {
                return (String) rows.get(0).get("title");
            }
        }
        return null;
    }

    private Long getRecommendIdByCommentId(Long commentId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT recommend_id FROM user_comment WHERE id = ?", commentId);
        if (!rows.isEmpty()) {
            return ((Number) rows.get(0).get("recommend_id")).longValue();
        }
        return null;
    }
}

package com.educate.assistant.service;

import com.educate.assistant.dto.RecommendationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class RecommendService {

    private final JdbcTemplate jdbcTemplate;
    private final CosService cosService;

    public List<Map<String, Object>> getList() {
        String sql = "SELECT r.id, r.title, r.summary, r.cover_url, r.views, r.likes, r.status, " +
                     "r.created_at, r.updated_at, u.nickname AS author_name " +
                     "FROM user_recommendation r " +
                     "LEFT JOIN user u ON r.user_id = u.id " +
                     "WHERE r.status = 1 " +
                     "ORDER BY r.created_at DESC";
        return jdbcTemplate.queryForList(sql);
    }

    public List<Map<String, Object>> getDrafts(Long userId) {
        String sql = "SELECT r.id, r.title, r.summary, r.cover_url, r.views, r.likes, r.status, " +
                     "r.created_at, r.updated_at, u.nickname AS author_name " +
                     "FROM user_recommendation r " +
                     "LEFT JOIN user u ON r.user_id = u.id " +
                     "WHERE r.user_id = ? AND r.status = 0 " +
                     "ORDER BY r.updated_at DESC";
        return jdbcTemplate.queryForList(sql, userId);
    }

    public Map<String, Object> getDetail(Long id) {
        // 增加浏览量
        jdbcTemplate.update("UPDATE user_recommendation SET views = views + 1 WHERE id = ?", id);

        String sql = "SELECT r.*, u.nickname AS author_name " +
                     "FROM user_recommendation r " +
                     "LEFT JOIN user u ON r.user_id = u.id " +
                     "WHERE r.id = ?";
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, id);
        if (results.isEmpty()) {
            return null;
        }
        return results.get(0);
    }

    @Transactional
    public Long create(Long userId, RecommendationRequest request, String contentUrl) {
        int status = request.getStatus() != null ? request.getStatus() : 1;
        String sql = "INSERT INTO user_recommendation (user_id, title, summary, cover_url, content_url, views, likes, status) " +
                     "VALUES (?, ?, ?, ?, ?, 0, 0, ?)";
        jdbcTemplate.update(sql, userId, request.getTitle(), request.getSummary(),
                           request.getCoverUrl(), contentUrl, status);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @Transactional
    public void update(Long id, Long userId, RecommendationRequest request, String contentUrl) {
        // 验证是否是作者
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_recommendation WHERE id = ? AND user_id = ?",
            Integer.class, id, userId);
        if (count == null || count == 0) {
            throw new RuntimeException("无权修改此推荐");
        }

        int status = request.getStatus() != null ? request.getStatus() : 1;
        String sql = "UPDATE user_recommendation SET title = ?, summary = ?, cover_url = ?, content_url = ?, status = ?, updated_at = NOW() WHERE id = ?";
        jdbcTemplate.update(sql, request.getTitle(), request.getSummary(),
                           request.getCoverUrl(), contentUrl, status, id);
    }

    @Transactional
    public void delete(Long id, Long userId) {
        // 获取 content_url 用于删除 COS 文件
        String contentUrl = jdbcTemplate.queryForObject(
            "SELECT content_url FROM user_recommendation WHERE id = ? AND user_id = ?",
            String.class, id, userId);
        if (contentUrl == null) {
            throw new RuntimeException("无权删除此推荐或推荐不存在");
        }

        // 删除 COS 文件
        String objectKey = cosService.extractObjectKey(contentUrl);
        if (objectKey != null) {
            cosService.deleteFile(objectKey);
        }

        // 删除封面图
        String coverUrl = jdbcTemplate.queryForObject(
            "SELECT cover_url FROM user_recommendation WHERE id = ?", String.class, id);
        if (coverUrl != null) {
            String coverKey = cosService.extractObjectKey(coverUrl);
            if (coverKey != null) {
                cosService.deleteFile(coverKey);
            }
        }

        jdbcTemplate.update("DELETE FROM user_recommendation WHERE id = ? AND user_id = ?", id, userId);
    }

    public void incrementLikes(Long id) {
        jdbcTemplate.update("UPDATE user_recommendation SET likes = likes + 1 WHERE id = ?", id);
    }

    public Long getUserIdByUsername(String username) {
        return jdbcTemplate.queryForObject("SELECT id FROM user WHERE username = ?", Long.class, username);
    }
}

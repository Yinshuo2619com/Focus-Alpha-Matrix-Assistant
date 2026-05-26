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
    private final NotificationService notificationService;

    public List<Map<String, Object>> getList() {
        String sql = "SELECT r.id, r.title, r.summary, r.cover_url, r.views, r.likes, r.favorites, r.status, r.type, " +
                     "r.created_at, r.updated_at, u.nickname AS author_name, u.avatar AS author_avatar " +
                     "FROM user_recommendation r " +
                     "LEFT JOIN user u ON r.user_id = u.id " +
                     "WHERE r.status = 1 AND r.type = 0 " +
                     "ORDER BY r.created_at DESC";
        return jdbcTemplate.queryForList(sql);
    }

    public List<Map<String, Object>> getDrafts(Long userId) {
        String sql = "SELECT r.id, r.title, r.summary, r.cover_url, r.views, r.likes, r.favorites, r.status, r.type, " +
                     "r.created_at, r.updated_at, u.nickname AS author_name, u.avatar AS author_avatar " +
                     "FROM user_recommendation r " +
                     "LEFT JOIN user u ON r.user_id = u.id " +
                     "WHERE r.user_id = ? AND r.status = 0 AND r.type = 0 " +
                     "ORDER BY r.updated_at DESC";
        return jdbcTemplate.queryForList(sql, userId);
    }

    public List<Map<String, Object>> getMyPublished(Long userId) {
        String sql = "SELECT r.id, r.title, r.summary, r.cover_url, r.views, r.likes, r.favorites, r.status, r.type, " +
                     "r.created_at, r.updated_at, u.nickname AS author_name, u.avatar AS author_avatar " +
                     "FROM user_recommendation r " +
                     "LEFT JOIN user u ON r.user_id = u.id " +
                     "WHERE r.user_id = ? AND r.status = 1 AND r.type = 0 " +
                     "ORDER BY r.updated_at DESC";
        return jdbcTemplate.queryForList(sql, userId);
    }

    // ========== 小工具 ==========

    public List<Map<String, Object>> getTools() {
        String sql = "SELECT r.id, r.title, r.summary, r.cover_url, r.content_url, r.views, r.likes, r.favorites, r.status, r.type, " +
                     "r.created_at, r.updated_at, u.nickname AS author_name, u.avatar AS author_avatar " +
                     "FROM user_recommendation r " +
                     "LEFT JOIN user u ON r.user_id = u.id " +
                     "WHERE r.status >= 1 AND r.type = 1 " +
                     "ORDER BY r.updated_at DESC";
        return jdbcTemplate.queryForList(sql);
    }

    public void reorderTools(List<Long> toolIds) {
        long now = System.currentTimeMillis();
        for (int i = 0; i < toolIds.size(); i++) {
            java.sql.Timestamp ts = new java.sql.Timestamp(now - i * 1000L);
            jdbcTemplate.update("UPDATE user_recommendation SET updated_at = ? WHERE id = ? AND type = 1",
                    ts, toolIds.get(i));
        }
    }

    public List<Map<String, Object>> getToolDrafts(Long userId) {
        String sql = "SELECT r.id, r.title, r.summary, r.cover_url, r.views, r.likes, r.favorites, r.status, r.type, " +
                     "r.created_at, r.updated_at, u.nickname AS author_name, u.avatar AS author_avatar " +
                     "FROM user_recommendation r " +
                     "LEFT JOIN user u ON r.user_id = u.id " +
                     "WHERE r.user_id = ? AND r.status = 0 AND r.type = 1 " +
                     "ORDER BY r.updated_at DESC";
        return jdbcTemplate.queryForList(sql, userId);
    }

    public List<Map<String, Object>> getMyTools(Long userId) {
        String sql = "SELECT r.id, r.title, r.summary, r.cover_url, r.views, r.likes, r.favorites, r.status, r.type, " +
                     "r.created_at, r.updated_at, u.nickname AS author_name, u.avatar AS author_avatar " +
                     "FROM user_recommendation r " +
                     "LEFT JOIN user u ON r.user_id = u.id " +
                     "WHERE r.user_id = ? AND r.status >= 1 AND r.type = 1 " +
                     "ORDER BY r.updated_at DESC";
        return jdbcTemplate.queryForList(sql, userId);
    }

    public Map<String, Object> getDetail(Long id) {
        // 增加浏览量
        jdbcTemplate.update("UPDATE user_recommendation SET views = views + 1 WHERE id = ?", id);

        String sql = "SELECT r.*, u.nickname AS author_name, u.avatar AS author_avatar " +
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
        int type = request.getType() != null ? request.getType() : 0;
        String sql = "INSERT INTO user_recommendation (user_id, title, summary, cover_url, content_url, views, likes, status, type) " +
                     "VALUES (?, ?, ?, ?, ?, 0, 0, ?, ?)";
        jdbcTemplate.update(sql, userId, request.getTitle(), request.getSummary(),
                           request.getCoverUrl(), contentUrl, status, type);
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
        // 获取记录
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
            "SELECT content_url, cover_url FROM user_recommendation WHERE id = ? AND user_id = ?",
            id, userId);
        if (records.isEmpty()) {
            throw new RuntimeException("无权删除此推荐或推荐不存在");
        }
        Map<String, Object> record = records.get(0);

        // 删除内容文件（代理路径格式：/api/recommend/content/xxx.md）
        String contentUrl = (String) record.get("content_url");
        if (contentUrl != null && !contentUrl.startsWith("http://") && !contentUrl.startsWith("https://")) {
            String objectKey = proxyUrlToObjectKey(contentUrl, "recommend/content/");
            if (objectKey != null) {
                cosService.deleteFile(objectKey);
            }
        }

        // 删除封面图（代理路径格式：/api/recommend/cover/xxx）
        String coverUrl = (String) record.get("cover_url");
        if (coverUrl != null) {
            String coverKey = proxyUrlToObjectKey(coverUrl, "recommend/covers/");
            if (coverKey != null) {
                cosService.deleteFile(coverKey);
            }
        }

        // 清理相关通知
        jdbcTemplate.update("DELETE FROM user_notification WHERE target_id = ? AND target_type = 'ARTICLE'", id);
        jdbcTemplate.update("DELETE FROM user_notification WHERE target_id IN (SELECT id FROM user_comment WHERE recommend_id = ?) AND target_type = 'COMMENT'", id);

        jdbcTemplate.update("DELETE FROM user_recommendation WHERE id = ? AND user_id = ?", id, userId);
    }

    private String proxyUrlToObjectKey(String url, String cosPrefix) {
        if (url == null) return null;
        // 代理路径：/api/recommend/content/xxx.md → recommend/content/xxx.md
        if (url.startsWith("/api/recommend/")) {
            int lastSlash = url.lastIndexOf('/');
            if (lastSlash >= 0) {
                return cosPrefix + url.substring(lastSlash + 1);
            }
        }
        // 兼容旧数据：直接使用 COS URL
        return cosService.extractObjectKey(url);
    }

    public void incrementLikes(Long id) {
        jdbcTemplate.update("UPDATE user_recommendation SET likes = likes + 1 WHERE id = ?", id);
    }

    public void incrementViews(Long id) {
        jdbcTemplate.update("UPDATE user_recommendation SET views = views + 1 WHERE id = ?", id);
    }

    public Long getUserIdByUsername(String username) {
        return jdbcTemplate.queryForObject("SELECT id FROM user WHERE username = ?", Long.class, username);
    }

    public void addFavorite(Long userId, Long recommendId) {
        int inserted = jdbcTemplate.update("INSERT IGNORE INTO user_favorite (user_id, recommend_id) VALUES (?, ?)",
                userId, recommendId);
        jdbcTemplate.update("UPDATE user_recommendation SET favorites = favorites + 1 WHERE id = ?", recommendId);

        if (inserted > 0) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT user_id FROM user_recommendation WHERE id = ?", recommendId);
            if (!rows.isEmpty()) {
                Long articleAuthorId = ((Number) rows.get(0).get("user_id")).longValue();
                notificationService.createNotification(articleAuthorId, userId, "ARTICLE_FAVORITE", recommendId, "ARTICLE");
            }
        }
    }

    public void removeFavorite(Long userId, Long recommendId) {
        int rows = jdbcTemplate.update("DELETE FROM user_favorite WHERE user_id = ? AND recommend_id = ?",
                userId, recommendId);
        if (rows > 0) {
            jdbcTemplate.update("UPDATE user_recommendation SET favorites = GREATEST(favorites - 1, 0) WHERE id = ?", recommendId);
        }
    }

    public boolean isFavorited(Long userId, Long recommendId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_favorite WHERE user_id = ? AND recommend_id = ?",
                Integer.class, userId, recommendId);
        return count != null && count > 0;
    }

    public List<Map<String, Object>> getFavorites(Long userId) {
        String sql = "SELECT r.id, r.title, r.summary, r.cover_url, r.views, r.likes, r.favorites, " +
                     "r.created_at, r.updated_at, u.nickname AS author_name, u.avatar AS author_avatar " +
                     "FROM user_favorite f " +
                     "INNER JOIN user_recommendation r ON f.recommend_id = r.id " +
                     "LEFT JOIN user u ON r.user_id = u.id " +
                     "WHERE f.user_id = ? AND r.status = 1 " +
                     "ORDER BY f.created_at DESC";
        return jdbcTemplate.queryForList(sql, userId);
    }

    // ========== 评论功能 ==========

    public Long addComment(Long userId, Long recommendId, Long parentId, String content) {
        String sql = "INSERT INTO user_comment (user_id, recommend_id, parent_id, content) VALUES (?, ?, ?, ?)";
        jdbcTemplate.update(sql, userId, recommendId, parentId, content);
        Long commentId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        if (parentId != null) {
            List<Map<String, Object>> parentRows = jdbcTemplate.queryForList(
                    "SELECT user_id FROM user_comment WHERE id = ?", parentId);
            if (!parentRows.isEmpty()) {
                Long parentAuthorId = ((Number) parentRows.get(0).get("user_id")).longValue();
                notificationService.createNotification(parentAuthorId, userId, "COMMENT_REPLY", commentId, "COMMENT");
            }
        }

        return commentId;
    }

    public List<Map<String, Object>> getCommentsByRecommendId(Long recommendId) {
        String sql = "SELECT c.id, c.user_id, c.recommend_id, c.parent_id, c.content, c.likes, c.created_at, " +
                     "u.nickname AS author_name, u.avatar AS author_avatar " +
                     "FROM user_comment c " +
                     "LEFT JOIN user u ON c.user_id = u.id " +
                     "WHERE c.recommend_id = ? " +
                     "ORDER BY c.created_at ASC";
        return jdbcTemplate.queryForList(sql, recommendId);
    }

    public List<Map<String, Object>> getMyComments(Long userId) {
        String sql = "SELECT c.id, c.content, c.likes, c.created_at, c.recommend_id, " +
                     "r.title AS recommend_title " +
                     "FROM user_comment c " +
                     "LEFT JOIN user_recommendation r ON c.recommend_id = r.id " +
                     "WHERE c.user_id = ? " +
                     "ORDER BY c.created_at DESC";
        return jdbcTemplate.queryForList(sql, userId);
    }

    public void deleteComment(Long commentId, Long userId) {
        int rows = jdbcTemplate.update("DELETE FROM user_comment WHERE id = ? AND user_id = ?", commentId, userId);
        if (rows == 0) {
            throw new RuntimeException("无权删除此评论或评论不存在");
        }
        notificationService.deleteByTarget(commentId, "COMMENT");
    }

    public void likeComment(Long userId, Long commentId) {
        int inserted = jdbcTemplate.update("INSERT IGNORE INTO user_comment_like (user_id, comment_id) VALUES (?, ?)",
                userId, commentId);
        jdbcTemplate.update("UPDATE user_comment SET likes = likes + 1 WHERE id = ?", commentId);

        if (inserted > 0) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT user_id FROM user_comment WHERE id = ?", commentId);
            if (!rows.isEmpty()) {
                Long commentAuthorId = ((Number) rows.get(0).get("user_id")).longValue();
                notificationService.createNotification(commentAuthorId, userId, "COMMENT_LIKE", commentId, "COMMENT");
            }
        }
    }

    public void unlikeComment(Long userId, Long commentId) {
        int rows = jdbcTemplate.update("DELETE FROM user_comment_like WHERE user_id = ? AND comment_id = ?",
                userId, commentId);
        if (rows > 0) {
            jdbcTemplate.update("UPDATE user_comment SET likes = GREATEST(likes - 1, 0) WHERE id = ?", commentId);
        }
    }

    public boolean isCommentLiked(Long userId, Long commentId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_comment_like WHERE user_id = ? AND comment_id = ?",
                Integer.class, userId, commentId);
        return count != null && count > 0;
    }

    public Map<String, Object> getCommentLikeStatuses(Long userId, List<Long> commentIds) {
        if (commentIds.isEmpty()) return Collections.emptyMap();
        String placeholders = String.join(",", Collections.nCopies(commentIds.size(), "?"));
        String sql = "SELECT comment_id FROM user_comment_like WHERE user_id = ? AND comment_id IN (" + placeholders + ")";
        List<Object> params = new ArrayList<>();
        params.add(userId);
        params.addAll(commentIds);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
        Map<String, Object> result = new HashMap<>();
        for (Long id : commentIds) {
            result.put(String.valueOf(id), false);
        }
        for (Map<String, Object> row : rows) {
            Long cid = ((Number) row.get("comment_id")).longValue();
            result.put(String.valueOf(cid), true);
        }
        return result;
    }
}

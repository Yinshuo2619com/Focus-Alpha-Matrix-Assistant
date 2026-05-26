package com.educate.assistant.controller;

import com.educate.assistant.common.Result;
import com.educate.assistant.dto.RecommendationRequest;
import com.educate.assistant.service.CosService;
import com.educate.assistant.service.RecommendService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RecommendController {

    private final RecommendService recommendService;
    private final CosService cosService;

    @GetMapping("/recommendations")
    public Result<List<Map<String, Object>>> getList() {
        List<Map<String, Object>> list = recommendService.getList();
        return Result.success(toCamelCaseList(list));
    }

    @GetMapping("/recommendations/drafts")
    public Result<List<Map<String, Object>>> getDrafts() {
        Long userId = getCurrentUserId();
        List<Map<String, Object>> drafts = recommendService.getDrafts(userId);
        return Result.success(toCamelCaseList(drafts));
    }

    @GetMapping("/recommendations/mine")
    public Result<List<Map<String, Object>>> getMyPublished() {
        Long userId = getCurrentUserId();
        List<Map<String, Object>> list = recommendService.getMyPublished(userId);
        return Result.success(toCamelCaseList(list));
    }

    @GetMapping("/recommendations/{id}")
    public Result<Map<String, Object>> getDetail(@PathVariable Long id) {
        Map<String, Object> detail = recommendService.getDetail(id);
        if (detail == null) {
            return Result.error(404, "推荐内容不存在");
        }
        return Result.success(toCamelCase(detail));
    }

    @PostMapping("/recommendations")
    public Result<Long> create(@RequestBody RecommendationRequest request) {
        try {
            Long userId = getCurrentUserId();
            boolean isDraft = request.getStatus() != null && request.getStatus() == 0;

            // 草稿允许内容为空
            String contentUrl = null;
            if (request.getContent() != null && !request.getContent().isEmpty()) {
                contentUrl = uploadContentToCos(request.getContent());
            } else if (!isDraft) {
                return Result.fail("内容不能为空");
            }

            Long id = recommendService.create(userId, request, contentUrl);
            return Result.success(id);
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }

    @PutMapping("/recommendations/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody RecommendationRequest request) {
        try {
            Long userId = getCurrentUserId();
            boolean isDraft = request.getStatus() != null && request.getStatus() == 0;

            // 草稿允许内容为空
            String contentUrl = null;
            if (request.getContent() != null && !request.getContent().isEmpty()) {
                contentUrl = uploadContentToCos(request.getContent());
            } else if (!isDraft) {
                return Result.fail("内容不能为空");
            }

            recommendService.update(id, userId, request, contentUrl);
            return Result.success(null);
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }

    @DeleteMapping("/recommendations/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            Long userId = getCurrentUserId();
            recommendService.delete(id, userId);
            return Result.success(null);
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }

    @PostMapping("/recommendations/{id}/like")
    public Result<Void> like(@PathVariable Long id) {
        recommendService.incrementLikes(id);
        return Result.success(null);
    }

    @PostMapping("/recommendations/{id}/view")
    public Result<Void> incrementViews(@PathVariable Long id) {
        recommendService.incrementViews(id);
        return Result.success(null);
    }

    @PostMapping("/recommendations/{id}/favorite")
    public Result<Void> addFavorite(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        recommendService.addFavorite(userId, id);
        return Result.success(null);
    }

    @DeleteMapping("/recommendations/{id}/favorite")
    public Result<Void> removeFavorite(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        recommendService.removeFavorite(userId, id);
        return Result.success(null);
    }

    @GetMapping("/recommendations/favorites")
    public Result<List<Map<String, Object>>> getFavorites() {
        Long userId = getCurrentUserId();
        List<Map<String, Object>> list = recommendService.getFavorites(userId);
        return Result.success(toCamelCaseList(list));
    }

    @GetMapping("/recommendations/{id}/favorite-status")
    public Result<Boolean> getFavoriteStatus(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        return Result.success(recommendService.isFavorited(userId, id));
    }

    // ========== 评论接口 ==========

    @PostMapping("/recommendations/{id}/comments")
    public Result<Long> addComment(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long userId = getCurrentUserId();
        Long parentId = body.get("parentId") != null ? Long.valueOf(body.get("parentId").toString()) : null;
        String content = (String) body.get("content");
        if (content == null || content.trim().isEmpty()) {
            return Result.fail("评论内容不能为空");
        }
        Long commentId = recommendService.addComment(userId, id, parentId, content.trim());
        return Result.success(commentId);
    }

    @GetMapping("/recommendations/{id}/comments")
    public Result<List<Map<String, Object>>> getComments(@PathVariable Long id) {
        List<Map<String, Object>> comments = recommendService.getCommentsByRecommendId(id);
        Long userId = null;
        try {
            userId = getCurrentUserId();
        } catch (Exception ignored) {}
        if (userId != null) {
            List<Long> commentIds = comments.stream()
                    .map(c -> ((Number) c.get("id")).longValue())
                    .toList();
            Map<String, Object> likeStatuses = recommendService.getCommentLikeStatuses(userId, commentIds);
            for (Map<String, Object> comment : comments) {
                String cid = String.valueOf(comment.get("id"));
                comment.put("liked", likeStatuses.getOrDefault(cid, false));
            }
        }
        return Result.success(toCamelCaseList(comments));
    }

    @DeleteMapping("/comments/{id}")
    public Result<Void> deleteComment(@PathVariable Long id) {
        try {
            Long userId = getCurrentUserId();
            recommendService.deleteComment(id, userId);
            return Result.success(null);
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }

    @PostMapping("/comments/{id}/like")
    public Result<Void> likeComment(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        recommendService.likeComment(userId, id);
        return Result.success(null);
    }

    @DeleteMapping("/comments/{id}/like")
    public Result<Void> unlikeComment(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        recommendService.unlikeComment(userId, id);
        return Result.success(null);
    }

    @GetMapping("/comments/mine")
    public Result<List<Map<String, Object>>> getMyComments() {
        Long userId = getCurrentUserId();
        List<Map<String, Object>> comments = recommendService.getMyComments(userId);
        return Result.success(toCamelCaseList(comments));
    }

    // ========== 小工具接口 ==========

    @GetMapping("/tools")
    public Result<List<Map<String, Object>>> getTools() {
        List<Map<String, Object>> list = recommendService.getTools();
        return Result.success(toCamelCaseList(list));
    }

    @GetMapping("/tools/drafts")
    public Result<List<Map<String, Object>>> getToolDrafts() {
        if (!isAdmin()) return Result.error(403, "无权访问");
        Long userId = getCurrentUserId();
        List<Map<String, Object>> drafts = recommendService.getToolDrafts(userId);
        return Result.success(toCamelCaseList(drafts));
    }

    @GetMapping("/tools/mine")
    public Result<List<Map<String, Object>>> getMyTools() {
        if (!isAdmin()) return Result.error(403, "无权访问");
        Long userId = getCurrentUserId();
        List<Map<String, Object>> list = recommendService.getMyTools(userId);
        return Result.success(toCamelCaseList(list));
    }

    @PostMapping("/tools")
    public Result<Long> createTool(@RequestBody RecommendationRequest request) {
        if (!isAdmin()) return Result.error(403, "仅管理员可发布工具");
        try {
            Long userId = getCurrentUserId();
            request.setType(1);
            boolean isDraft = request.getStatus() != null && request.getStatus() == 0;

            boolean isRedirect = request.getStatus() != null && request.getStatus() == 2;

            String contentUrl = null;
            if (request.getContent() != null && !request.getContent().isEmpty()) {
                if (isRedirect) {
                    contentUrl = request.getContent().trim();
                } else {
                    contentUrl = uploadContentToCos(request.getContent());
                }
            } else if (!isDraft) {
                return Result.fail("内容不能为空");
            }

            Long id = recommendService.create(userId, request, contentUrl);
            return Result.success(id);
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }

    @PutMapping("/tools/{id}")
    public Result<Void> updateTool(@PathVariable Long id, @RequestBody RecommendationRequest request) {
        if (!isAdmin()) return Result.error(403, "仅管理员可编辑工具");
        try {
            Long userId = getCurrentUserId();
            request.setType(1);
            boolean isDraft = request.getStatus() != null && request.getStatus() == 0;
            boolean isRedirect = request.getStatus() != null && request.getStatus() == 2;

            String contentUrl = null;
            if (request.getContent() != null && !request.getContent().isEmpty()) {
                if (isRedirect) {
                    contentUrl = request.getContent().trim();
                } else {
                    contentUrl = uploadContentToCos(request.getContent());
                }
            } else if (!isDraft) {
                return Result.fail("内容不能为空");
            }

            recommendService.update(id, userId, request, contentUrl);
            return Result.success(null);
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }

    @PutMapping("/tools/reorder")
    public Result<Void> reorderTools(@RequestBody List<Long> ids) {
        if (!isAdmin()) return Result.error(403, "仅管理员可排序");
        recommendService.reorderTools(ids);
        return Result.success(null);
    }

    @DeleteMapping("/tools/{id}")
    public Result<Void> deleteTool(@PathVariable Long id) {
        if (!isAdmin()) return Result.error(403, "仅管理员可删除工具");
        try {
            Long userId = getCurrentUserId();
            recommendService.delete(id, userId);
            return Result.success(null);
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @PostMapping("/cos/upload")
    public Result<String> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", defaultValue = "cover") String type) {
        try {
            String ext = getExtension(file.getOriginalFilename());
            String filename = UUID.randomUUID() + ext;
            boolean isCover = "cover".equals(type);
            String dir = isCover ? "recommend/covers/" : "recommend/images/";
            String objectKey = dir + filename;
            cosService.uploadFile(file, objectKey);
            // 返回代理 URL，不暴露 COS 地址
            String proxyUrl = isCover ? "/api/recommend/cover/" + filename : "/api/recommend/image/" + filename;
            return Result.success(proxyUrl);
        } catch (IOException e) {
            return Result.fail("文件上传失败: " + e.getMessage());
        }
    }

    @GetMapping("/recommend/image/{filename}")
    public ResponseEntity<byte[]> getImage(@PathVariable String filename) {
        try {
            String objectKey = "recommend/images/" + filename;
            byte[] data = cosService.downloadFile(objectKey);
            String contentType = cosService.getContentType(objectKey);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=604800")
                    .body(data);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/recommend/cover/{filename}")
    public ResponseEntity<byte[]> getCover(@PathVariable String filename) {
        try {
            String objectKey = "recommend/covers/" + filename;
            byte[] data = cosService.downloadFile(objectKey);
            String contentType = cosService.getContentType(objectKey);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=604800")
                    .body(data);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/recommend/content/{filename}")
    public ResponseEntity<byte[]> getContent(@PathVariable String filename) {
        try {
            String objectKey = "recommend/content/" + filename;
            byte[] data = cosService.downloadFile(objectKey);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "text/plain; charset=utf-8")
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=604800")
                    .body(data);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        return recommendService.getUserIdByUsername(username);
    }

    private String uploadContentToCos(String content) {
        if (content == null || content.isEmpty()) {
            throw new RuntimeException("内容不能为空");
        }
        try {
            String filename = UUID.randomUUID() + ".md";
            String objectKey = "recommend/content/" + filename;
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            cosService.uploadBytes(bytes, objectKey);
            // 返回代理 URL，不暴露 COS 地址
            return "/api/recommend/content/" + filename;
        } catch (Exception e) {
            throw new RuntimeException("内容上传失败: " + e.getMessage());
        }
    }

    private String getExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }

    private Map<String, Object> toCamelCase(Map<String, Object> snakeCaseMap) {
        Map<String, Object> camelCaseMap = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : snakeCaseMap.entrySet()) {
            camelCaseMap.put(snakeToCamel(entry.getKey()), entry.getValue());
        }
        return camelCaseMap;
    }

    private List<Map<String, Object>> toCamelCaseList(List<Map<String, Object>> list) {
        return list.stream().map(this::toCamelCase).toList();
    }

    private String snakeToCamel(String snake) {
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = false;
        for (char c : snake.toCharArray()) {
            if (c == '_') {
                nextUpper = true;
            } else {
                sb.append(nextUpper ? Character.toUpperCase(c) : c);
                nextUpper = false;
            }
        }
        return sb.toString();
    }
}

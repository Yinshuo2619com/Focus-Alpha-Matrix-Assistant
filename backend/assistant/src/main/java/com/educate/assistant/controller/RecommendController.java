package com.educate.assistant.controller;

import com.educate.assistant.common.Result;
import com.educate.assistant.dto.RecommendationRequest;
import com.educate.assistant.service.CosService;
import com.educate.assistant.service.RecommendService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
        return Result.success(list);
    }

    @GetMapping("/recommendations/{id}")
    public Result<Map<String, Object>> getDetail(@PathVariable Long id) {
        Map<String, Object> detail = recommendService.getDetail(id);
        if (detail == null) {
            return Result.error(404, "推荐内容不存在");
        }
        return Result.success(detail);
    }

    @PostMapping("/recommendations")
    public Result<Long> create(@RequestBody RecommendationRequest request) {
        try {
            Long userId = getCurrentUserId();

            // 上传 MD 内容到 COS
            String contentUrl = uploadContentToCos(request.getContent());

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

            String contentUrl = uploadContentToCos(request.getContent());

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

    @PostMapping("/cos/upload")
    public Result<String> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            String ext = getExtension(file.getOriginalFilename());
            String objectKey = "recommend/covers/" + UUID.randomUUID() + ext;
            String url = cosService.uploadFile(file, objectKey);
            return Result.success(url);
        } catch (IOException e) {
            return Result.fail("文件上传失败: " + e.getMessage());
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
            String objectKey = "recommend/content/" + UUID.randomUUID() + ".md";
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            // 使用 CosService 的底层方法上传字节数组
            // 需要包装为 MultipartFile 或直接使用 COS SDK
            // 这里通过临时方式：创建一个简单的包装
            String url = cosService.uploadBytes(bytes, objectKey);
            return url;
        } catch (Exception e) {
            throw new RuntimeException("内容上传失败: " + e.getMessage());
        }
    }

    private String getExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }
}

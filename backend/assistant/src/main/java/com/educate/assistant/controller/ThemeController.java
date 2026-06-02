package com.educate.assistant.controller;

import com.educate.assistant.common.Result;
import com.educate.assistant.service.CosService;
import com.educate.assistant.service.ThemeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/theme")
public class ThemeController {

    @Autowired
    private ThemeService themeService;

    @Autowired
    private CosService cosService;

    @GetMapping
    public Result<Map<String, Object>> getTheme() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Map<String, Object> config = themeService.getThemeByUsername(username);
        return Result.success(config);
    }

    @PostMapping
    public Result<Void> saveTheme(@RequestBody Map<String, Object> config) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        themeService.saveTheme(username, config);
        return Result.success(null);
    }

    @DeleteMapping
    public Result<Void> resetTheme() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        themeService.deleteTheme(username);
        return Result.success(null);
    }

    @PostMapping("/upload")
    public Result<String> uploadImage(@RequestParam("file") MultipartFile file) throws IOException {
        if (!cosService.isEnabled()) return Result.error(500, "COS 未配置");
        String ext = file.getOriginalFilename();
        ext = ext != null && ext.contains(".") ? ext.substring(ext.lastIndexOf('.')) : ".png";
        String objectKey = "theme/" + UUID.randomUUID() + ext;
        String url = cosService.uploadFile(file, objectKey);
        return Result.success(url);
    }

    @GetMapping(value = "/image/{filename}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public byte[] getImage(@PathVariable String filename) {
        return cosService.downloadFile("theme/" + filename);
    }
}

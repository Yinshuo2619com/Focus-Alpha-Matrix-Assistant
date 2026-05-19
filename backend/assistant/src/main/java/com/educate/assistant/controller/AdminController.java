package com.educate.assistant.controller;

import com.educate.assistant.common.FileValidator;
import com.educate.assistant.common.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final JdbcTemplate jdbcTemplate;

    @Value("${avatar.upload-dir:uploads/avatar}")
    private String avatarUploadDir;

    public AdminController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 获取用户列表（游标分页）
     * 
     * @param size    每页数量（默认10，最大100）
     * @param cursor  游标值（上一页最后一条记录的ID，首页为0）
     * @param keyword 搜索关键词（可选，搜索用户名/昵称/邮箱）
     * @param status  状态筛选（可选，1-正常，0-禁用）
     */
    @GetMapping("/users")
    public Result<Map<String, Object>> getUsers(
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "0") Long cursor,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        
        // 限制最大每页数量
        size = Math.min(size, 100);
        
        // 构建查询条件
        List<Object> params = new ArrayList<>();
        StringBuilder whereClause = new StringBuilder();
        
        // 游标条件
        if (cursor > 0) {
            whereClause.append(" AND id > ?");
            params.add(cursor);
        }
        
        // 搜索条件
        if (StringUtils.hasText(keyword)) {
            whereClause.append(" AND (username LIKE ? OR nickname LIKE ? OR email LIKE ? OR phone LIKE ?)");
            String likeKeyword = "%" + keyword + "%";
            params.add(likeKeyword);
            params.add(likeKeyword);
            params.add(likeKeyword);
            params.add(likeKeyword);
        }
        
        // 状态筛选
        if (status != null) {
            whereClause.append(" AND status = ?");
            params.add(status);
        }
        
        // 查询列表（多取一条用于判断是否有更多数据）
        int querySize = size + 1;
        String listSql = "SELECT id, username, nickname, email, phone, status, avatar, created_at FROM user WHERE 1=1" 
                + whereClause + " ORDER BY id ASC LIMIT ?";
        params.add(querySize);
        
        List<Map<String, Object>> list = jdbcTemplate.queryForList(listSql, params.toArray());
        
        // 判断是否有更多数据
        boolean hasMore = list.size() > size;
        
        // 如果有多余的数据，移除最后一条（仅用于判断hasMore）
        if (hasMore) {
            list.remove(list.size() - 1);
        }
        
        // 获取下一页的游标
        Long nextCursor = null;
        if (!list.isEmpty()) {
            nextCursor = ((Number) list.get(list.size() - 1).get("id")).longValue();
        }
        
        // 构建返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("nextCursor", hasMore ? nextCursor : null);
        result.put("hasMore", hasMore);
        result.put("size", size);
        
        return Result.success(result);
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/users/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        // 不允许删除admin用户
        String checkSql = "SELECT username FROM user WHERE id = ?";
        String username = jdbcTemplate.queryForObject(checkSql, String.class, id);
        
        if (username == null) {
            return Result.error(404, "用户不存在");
        }
        
        if ("admin".equals(username)) {
            return Result.error(403, "不允许删除管理员账户");
        }
        
        String deleteSql = "DELETE FROM user WHERE id = ?";
        jdbcTemplate.update(deleteSql, id);
        return Result.success(null);
    }

    /**
     * 更新用户状态
     */
    @PutMapping("/users/{id}/status")
    public Result<Void> updateUserStatus(@PathVariable Long id, @RequestParam Integer status) {
        String checkSql = "SELECT username FROM user WHERE id = ?";
        String username = jdbcTemplate.queryForObject(checkSql, String.class, id);
        
        if (username == null) {
            return Result.error(404, "用户不存在");
        }
        
        if ("admin".equals(username)) {
            return Result.error(403, "不允许修改管理员账户状态");
        }
        
        String sql = "UPDATE user SET status = ?, updated_at = NOW() WHERE id = ?";
        jdbcTemplate.update(sql, status, id);
        return Result.success(null);
    }
    
    /**
     * 创建新用户
     */
    @PostMapping("/users")
    public Result<Void> createUser(@RequestBody Map<String, Object> user) {
        String username = (String) user.get("username");
        String nickname = (String) user.get("nickname");
        String email = (String) user.get("email");
        String password = (String) user.get("password");
        
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            return Result.error(400, "用户名和密码不能为空");
        }
        
        // 检查用户名是否已存在
        String checkSql = "SELECT COUNT(*) FROM user WHERE username = ?";
        Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, username);
        
        if (count != null && count > 0) {
            return Result.error(400, "用户名已存在");
        }
        
        // 加密密码
        org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder encoder = 
            new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        String encodedPassword = encoder.encode(password);
        
        String insertSql = "INSERT INTO user (username, password, nickname, email, status, avatar, created_at, updated_at) VALUES (?, ?, ?, ?, 1, NULL, NOW(), NOW())";
        jdbcTemplate.update(insertSql, username, encodedPassword, 
            StringUtils.hasText(nickname) ? nickname : username, 
            email);
        
        return Result.success(null);
    }
    
    /**
     * 更新用户信息
     */
    @PutMapping("/users/{id}")
    public Result<Void> updateUser(@PathVariable Long id, @RequestBody Map<String, Object> user) {
        String nickname = (String) user.get("nickname");
        String email = (String) user.get("email");
        String phone = (String) user.get("phone");
        
        String sql = "UPDATE user SET nickname = ?, email = ?, phone = ?, updated_at = NOW() WHERE id = ?";
        jdbcTemplate.update(sql, nickname, email, phone, id);
        return Result.success(null);
    }

    /**
     * 管理员上传用户头像 - 完全无限制
     * 不受每日3次限制，不受大小限制，可修改任意用户头像
     */
    @PostMapping("/users/{id}/avatar")
    public Result<String> uploadUserAvatar(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        try {
            String username = jdbcTemplate.queryForObject(
                    "SELECT username FROM user WHERE id = ?",
                    String.class, id);

            if (username == null) {
                return Result.error(404, "用户不存在");
            }

            // 管理员不受文件类型限制
            String fileExt = FileValidator.getRealImageType(file);
            if (fileExt == null) {
                fileExt = "png"; // 默认格式
            }

            // 管理员不受次数限制，使用独立命名
            String fileName = username + "_admin_" + System.currentTimeMillis() + "." + fileExt;

            // 保存文件
            Path uploadDir = Paths.get(avatarUploadDir);
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            Path filePath = uploadDir.resolve(fileName);
            Files.copy(file.getInputStream(), filePath);

            // 更新用户头像
            jdbcTemplate.update(
                    "UPDATE user SET avatar = ?, updated_at = NOW() WHERE id = ?",
                    "/avatar/" + fileName, id);

            return Result.success("/avatar/" + fileName);

        } catch (IOException e) {
            return Result.error(500, "文件上传失败: " + e.getMessage());
        }
    }
}

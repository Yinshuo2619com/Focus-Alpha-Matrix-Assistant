package com.educate.assistant.service.impl;

import com.educate.assistant.common.FileValidator;
import com.educate.assistant.common.JwtUtil;
import com.educate.assistant.dto.ChangePasswordRequest;
import com.educate.assistant.dto.LoginRequest;
import com.educate.assistant.dto.RegisterRequest;
import com.educate.assistant.dto.UpdateProfileRequest;
import com.educate.assistant.entity.User;
import com.educate.assistant.service.CosService;
import com.educate.assistant.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CosService cosService;

    @Value("${avatar.upload-dir:uploads/avatar}")
    private String avatarUploadDir;

    @Override
    public Map<String, Object> login(LoginRequest loginRequest) {
        // 查询用户（支持用户名或手机号登录）
        String sql = "SELECT * FROM user WHERE username = ? OR phone = ?";
        List<User> users = jdbcTemplate.query(sql, (rs, rowNum) -> {
            User u = new User();
            u.setId(rs.getLong("id"));
            u.setUsername(rs.getString("username"));
            u.setPassword(rs.getString("password"));
            u.setNickname(rs.getString("nickname"));
            u.setEmail(rs.getString("email"));
            u.setPhone(rs.getString("phone"));
            u.setStatus(rs.getInt("status"));
            u.setAvatar(rs.getString("avatar"));
            u.setBirthday(rs.getDate("birthday") != null ? rs.getDate("birthday").toLocalDate() : null);
            u.setGender(rs.getString("gender"));
            return u;
        }, loginRequest.getUsername(), loginRequest.getUsername());

        if (users.isEmpty()) {
            throw new RuntimeException("用户不存在");
        }
        
        User user = users.get(0);

        // 验证密码
        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            throw new RuntimeException("密码错误");
        }

        // 生成 Token（包含角色信息）
        String role = user.getUsername().equals("admin") ? "ADMIN" : "USER";
        String token = jwtUtil.generateToken(user.getUsername(), role);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("userId", user.getId());
        result.put("username", user.getUsername());
        result.put("nickname", user.getNickname());
        result.put("avatar", user.getAvatar());
        result.put("role", role);
        result.put("birthday", user.getBirthday());
        result.put("gender", user.getGender());

        return result;
    }

    @Override
    public void register(RegisterRequest registerRequest) {
        // 检查用户名是否已存在
        String checkUsernameSql = "SELECT COUNT(*) FROM user WHERE username = ?";
        Integer usernameCount = jdbcTemplate.queryForObject(checkUsernameSql, Integer.class, registerRequest.getUsername());

        if (usernameCount != null && usernameCount > 0) {
            throw new RuntimeException("用户名已存在");
        }

        // 检查手机号是否已存在
        String checkPhoneSql = "SELECT COUNT(*) FROM user WHERE phone = ?";
        Integer phoneCount = jdbcTemplate.queryForObject(checkPhoneSql, Integer.class, registerRequest.getPhone());

        if (phoneCount != null && phoneCount > 0) {
            throw new RuntimeException("该手机号已被注册");
        }

        // 校验密码复杂度
        validatePassword(registerRequest.getPassword());

        // 加密密码
        String encodedPassword = passwordEncoder.encode(registerRequest.getPassword());

        // 插入新用户
        String insertSql = "INSERT INTO user (username, password, nickname, email, phone, status, avatar, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 1, NULL, NOW(), NOW())";
        jdbcTemplate.update(insertSql,
            registerRequest.getUsername(),
            encodedPassword,
            registerRequest.getNickname(),
            registerRequest.getEmail(),
            registerRequest.getPhone()
        );
    }

    @Override
    public String uploadAvatar(Long userId, String username, MultipartFile file) {
        // 1. 检查每日上传次数限制 (修复NULL值崩溃BUG)
        LocalDate today = LocalDate.now();
        Map<String, Object> userData = jdbcTemplate.queryForMap(
                "SELECT upload_count, last_upload_date FROM user WHERE id = ?",
                userId);
        
        Integer count = userData.get("upload_count") != null ? 
                        (Integer) userData.get("upload_count") : 0;
        java.sql.Date lastUploadSql = userData.get("last_upload_date") instanceof java.sql.Date ?
                        (java.sql.Date) userData.get("last_upload_date") : null;
        LocalDate lastUpload = lastUploadSql != null ? lastUploadSql.toLocalDate() : null;

        if (lastUpload != null && lastUpload.equals(today)) {
            if (count >= 3) {
                throw new RuntimeException("今日头像上传已达上限(3次)，请明日再试");
            }
        } else {
            count = 0; // 新的一天重置计数
        }

        // 2. 文件大小校验
        if (file.getSize() > FileValidator.MAX_FILE_SIZE) {
            throw new RuntimeException("文件大小不能超过20MB");
        }

        // 3. 文件类型校验
        String fileExt;
        try {
            fileExt = FileValidator.getRealImageType(file);
            if (fileExt == null) {
                throw new RuntimeException("仅支持 jpg, png, gif 格式图片");
            }
        } catch (IOException e) {
            throw new RuntimeException("文件校验失败");
        }

        // 4. 计算当前序号 (1-3循环)
        int sequence = count % 3 + 1;
        String fileName = username + sequence + "." + fileExt;

        // 5. 删除旧文件
        String oldAvatar = jdbcTemplate.queryForObject(
                "SELECT avatar FROM user WHERE id = ?", String.class, userId);
        if (oldAvatar != null && oldAvatar.startsWith("http")) {
            try {
                cosService.deleteFile(cosService.extractObjectKey(oldAvatar));
            } catch (Exception ignored) {
            }
        }

        String avatarUrl;

        if (cosService.isEnabled()) {
            // 6a. 上传到 COS
            try {
                String objectKey = "avatar/" + fileName;
                avatarUrl = cosService.uploadFile(file, objectKey);
            } catch (IOException e) {
                throw new RuntimeException("文件上传失败: " + e.getMessage());
            }
        } else {
            // 6b. 本地存储（开发环境）
            Path uploadDir = Paths.get(avatarUploadDir);
            try {
                if (!Files.exists(uploadDir)) {
                    Files.createDirectories(uploadDir);
                }
                Path filePath = uploadDir.resolve(fileName);
                Files.deleteIfExists(filePath);
                Files.copy(file.getInputStream(), filePath);
                avatarUrl = "/avatar/" + fileName;
            } catch (IOException e) {
                throw new RuntimeException("文件保存失败: " + e.getMessage());
            }
        }

        // 7. 更新用户信息
        jdbcTemplate.update(
                "UPDATE user SET avatar = ?, upload_count = ?, last_upload_date = ?, updated_at = NOW() WHERE id = ?",
                avatarUrl, count + 1, today, userId);

        return avatarUrl;
    }

    @Override
    public Long findUserIdByUsername(String username) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM user WHERE username = ?",
                Long.class, username);
    }

    private void validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new RuntimeException("请输入密码");
        }
        if (password.length() < 6) {
            throw new RuntimeException("密码长度不能少于6位");
        }
        if (password.matches(".*[\\u4e00-\\u9fa5].*")) {
            throw new RuntimeException("密码不能包含中文");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new RuntimeException("密码必须包含大写字母");
        }
        if (!password.matches(".*[a-z].*")) {
            throw new RuntimeException("密码必须包含小写字母");
        }
        if (!password.matches(".*[0-9].*")) {
            throw new RuntimeException("密码必须包含数字");
        }
        if (!password.matches(".*[^A-Za-z0-9].*")) {
            throw new RuntimeException("密码必须包含特殊字符");
        }
    }

    @Override
    public String updateProfile(String username, UpdateProfileRequest request) {
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            throw new RuntimeException("用户名不能为空");
        }
        if (request.getNickname() == null || request.getNickname().trim().isEmpty()) {
            throw new RuntimeException("昵称不能为空");
        }
        if (request.getPhone() == null || request.getPhone().trim().isEmpty()) {
            throw new RuntimeException("手机号不能为空");
        }

        Long currentUserId = findUserIdByUsername(username);

        // 检查用户名是否被其他用户占用
        boolean usernameChanged = !request.getUsername().trim().equals(username);
        if (usernameChanged) {
            Integer usernameCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM user WHERE username = ? AND id != ?",
                    Integer.class, request.getUsername().trim(), currentUserId);
            if (usernameCount != null && usernameCount > 0) {
                throw new RuntimeException("该用户名已被使用");
            }
        }

        // 检查手机号是否被其他用户占用
        Integer phoneCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user WHERE phone = ? AND id != ?",
                Integer.class, request.getPhone().trim(), currentUserId);
        if (phoneCount != null && phoneCount > 0) {
            throw new RuntimeException("该手机号已被其他用户使用");
        }

        String email = (request.getEmail() != null) ? request.getEmail().trim() : "";
        java.sql.Date birthday = null;
        if (request.getBirthday() != null && !request.getBirthday().trim().isEmpty()) {
            birthday = java.sql.Date.valueOf(request.getBirthday().trim());
        }
        String gender = (request.getGender() != null && !request.getGender().trim().isEmpty())
                ? request.getGender().trim() : null;

        jdbcTemplate.update(
                "UPDATE user SET username = ?, nickname = ?, phone = ?, email = ?, birthday = ?, gender = ?, updated_at = NOW() WHERE id = ?",
                request.getUsername().trim(),
                request.getNickname().trim(),
                request.getPhone().trim(),
                email,
                birthday,
                gender,
                currentUserId);

        // 用户名变更后生成新 token
        if (usernameChanged) {
            String role = request.getUsername().trim().equals("admin") ? "ADMIN" : "USER";
            return jwtUtil.generateToken(request.getUsername().trim(), role);
        }
        return null;
    }

    @Override
    public boolean checkUsernameAvailable(String newUsername, String currentUsername) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user WHERE username = ? AND username != ?",
                Integer.class, newUsername, currentUsername);
        return count == null || count == 0;
    }

    @Override
    public boolean checkPhoneAvailable(String phone, String currentUsername) {
        Long currentUserId = findUserIdByUsername(currentUsername);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user WHERE phone = ? AND id != ?",
                Integer.class, phone, currentUserId);
        return count == null || count == 0;
    }

    @Override
    public void changePassword(String username, ChangePasswordRequest request) {
        if (request.getOldPassword() == null || request.getOldPassword().isEmpty()) {
            throw new RuntimeException("请输入旧密码");
        }
        validatePassword(request.getNewPassword());

        // 查询当前密码
        String currentPassword = jdbcTemplate.queryForObject(
                "SELECT password FROM user WHERE username = ?",
                String.class, username);

        if (!passwordEncoder.matches(request.getOldPassword(), currentPassword)) {
            throw new RuntimeException("旧密码错误");
        }

        String encodedNewPassword = passwordEncoder.encode(request.getNewPassword());
        jdbcTemplate.update(
                "UPDATE user SET password = ?, updated_at = NOW() WHERE username = ?",
                encodedNewPassword, username);
    }
}

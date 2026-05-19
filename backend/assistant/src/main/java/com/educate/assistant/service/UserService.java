package com.educate.assistant.service;

import com.educate.assistant.dto.ChangePasswordRequest;
import com.educate.assistant.dto.LoginRequest;
import com.educate.assistant.dto.RegisterRequest;
import com.educate.assistant.dto.UpdateProfileRequest;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

public interface UserService {

    // 登录方法，返回一个包含用户信息和token的Map
    Map<String, Object> login(LoginRequest loginRequest);

    // 注册方法
    void register(RegisterRequest registerRequest);

    // 上传头像
    String uploadAvatar(Long userId, String username, MultipartFile file);

    // 根据用户名查询用户ID
    Long findUserIdByUsername(String username);

    // 更新个人资料，用户名变更时返回新 token
    String updateProfile(String username, UpdateProfileRequest request);

    // 修改密码
    void changePassword(String username, ChangePasswordRequest request);

    // 检查用户名是否可用（排除当前用户）
    boolean checkUsernameAvailable(String newUsername, String currentUsername);

    // 检查手机号是否可用（排除当前用户）
    boolean checkPhoneAvailable(String phone, String currentUsername);
}

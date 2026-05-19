package com.educate.assistant.dto;

import lombok.Data;

@Data
public class UpdateProfileRequest {
    private String username;
    private String nickname;
    private String phone;
    private String email;
    private String birthday;
    private String gender;
}

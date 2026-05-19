package com.educate.assistant.dto;

import lombok.Data;

@Data
public class RegisterRequest {
    private String username;
    private String password;
    private String nickname;
    private String phone;
    private String email;
}
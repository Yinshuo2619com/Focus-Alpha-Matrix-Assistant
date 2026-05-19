package com.educate.assistant.dto;

import lombok.Data;

@Data
public class EduLoginRequest {
    private String username;
    private String password;
    private String schoolId = "default";
}

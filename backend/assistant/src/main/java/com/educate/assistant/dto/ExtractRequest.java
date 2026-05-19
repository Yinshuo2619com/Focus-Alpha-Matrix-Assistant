package com.educate.assistant.dto;

import lombok.Data;

@Data
public class ExtractRequest {
    private String semester;
    private String schoolId = "default";
}

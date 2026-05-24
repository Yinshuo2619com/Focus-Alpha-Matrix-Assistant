package com.educate.assistant.dto;

import lombok.Data;

@Data
public class RecommendationRequest {

    private String title;

    private String summary;

    private String coverUrl;

    private String content;
}

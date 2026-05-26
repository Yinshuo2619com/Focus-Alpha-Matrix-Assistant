package com.educate.assistant.dto;

import lombok.Data;

@Data
public class RecommendationRequest {

    private String title;

    private String summary;

    private String coverUrl;

    private String content;

    private Integer status; // 0=草稿, 1=已发布

    private Integer type; // 0=推荐, 1=工具
}

package com.educate.assistant.dto;

import lombok.Data;

@Data
public class CourseEntryDTO {
    private String courseName;
    private String teacher;
    private String location;
    private Integer dayOfWeek;
    private Integer startSection;
    private Integer endSection;
    private String weeks;
    private String color;
}

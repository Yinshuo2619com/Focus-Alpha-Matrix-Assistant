package com.educate.assistant.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("course_entry")
public class CourseEntry {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long scheduleId;
    private String courseName;
    private String teacher;
    private String location;
    private Integer dayOfWeek;
    private Integer startSection;
    private Integer endSection;
    private String weeks;
    private String color;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

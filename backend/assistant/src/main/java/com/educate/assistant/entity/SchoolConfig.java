package com.educate.assistant.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("school_config")
public class SchoolConfig {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String schoolId;
    private String schoolName;
    private String baseUrl;
    private String loginPath;
    private String schedulePath;
    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

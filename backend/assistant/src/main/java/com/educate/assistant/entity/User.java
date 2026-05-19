package com.educate.assistant.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("user")
public class User{
    @TableId(type = IdType.AUTO) //主键，自增
    private Long id;

    private String username;
    private String password;
    private String nickname;
    private String email;
    private LocalDate birthday;
    private String gender;
    private Integer status;
private String avatar;
private String phone;

    @TableField(fill = FieldFill.INSERT) //新增时自动填入时间
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE) //新增和更新时自动填入时间
    private LocalDateTime updatedAt;

   
}
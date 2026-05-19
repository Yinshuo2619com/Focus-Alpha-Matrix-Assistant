package com.educate.assistant.common;

import lombok.Data;

@Data
public class Result<T> {
    private Integer code;
    private String message;
    private T data;

    //if success
    public static <T> Result<T> success(T data){
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage("操作成功");
        result.setData(data);

        return result;
    }

    //if fail
    public static <T> Result<T> fail(String message){
        Result<T> result = new Result<>();
        result.setCode(500);
        result.setMessage(message);

        return result;
    }
    
    //if fail with custom code
    public static <T> Result<T> error(Integer code, String message){
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);

        return result;
    }
}
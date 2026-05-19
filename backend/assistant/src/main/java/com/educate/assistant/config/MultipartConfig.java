package com.educate.assistant.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.multipart.support.MultipartFilter;

@Configuration
public class MultipartConfig {

    /**
     * 修复Spring Boot 3.x 文件上传404问题
     * 手动注册MultipartFilter保证文件上传请求被正确解析
     */
    @Bean
    public FilterRegistrationBean<MultipartFilter> multipartFilter() {
        FilterRegistrationBean<MultipartFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new MultipartFilter());
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(-100); // 优先级设置为低于Spring Security，确保先解析文件
        return registrationBean;
    }
}
package com.educate.assistant.service.impl;

import com.educate.assistant.service.ThemeService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ThemeServiceImpl implements ThemeService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, Object> getThemeByUsername(String username) {
        try {
            String json = jdbcTemplate.queryForObject(
                "SELECT ut.config FROM user_theme ut JOIN user u ON ut.user_id = u.id WHERE u.username = ?",
                String.class, username);
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    @Override
    public void saveTheme(String username, Map<String, Object> config) {
        try {
            String json = objectMapper.writeValueAsString(config);
            jdbcTemplate.update(
                "INSERT INTO user_theme (user_id, config) VALUES ((SELECT id FROM user WHERE username = ?), ?) " +
                "ON DUPLICATE KEY UPDATE config = VALUES(config), updated_at = NOW()",
                username, json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("主题配置序列化失败", e);
        }
    }

    @Override
    public void deleteTheme(String username) {
        jdbcTemplate.update(
            "DELETE FROM user_theme WHERE user_id = (SELECT id FROM user WHERE username = ?)",
            username);
    }
}

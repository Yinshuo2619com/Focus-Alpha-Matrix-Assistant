package com.educate.assistant.service;

import java.util.Map;

public interface ThemeService {
    Map<String, Object> getThemeByUsername(String username);
    void saveTheme(String username, Map<String, Object> config);
    void deleteTheme(String username);
}

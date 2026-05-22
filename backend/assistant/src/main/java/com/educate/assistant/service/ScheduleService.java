package com.educate.assistant.service;

//import com.educate.assistant.dto.CourseEntryDTO;
import com.educate.assistant.dto.SaveScheduleRequest;

//import java.util.List;
import java.util.Map;

public interface ScheduleService {

    Map<String, Object> getScheduleWithCourses(Long userId);

    void saveSchedule(Long userId, SaveScheduleRequest request);

    void deleteSchedule(Long userId, String semester);

    String generateShareToken(Long userId);

    Map<String, Object> getSharedSchedule(String token);
}

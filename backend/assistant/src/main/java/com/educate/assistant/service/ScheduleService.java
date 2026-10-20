package com.educate.assistant.service;

//import com.educate.assistant.dto.CourseEntryDTO;
import com.educate.assistant.dto.SaveScheduleRequest;

//import java.util.List;
import java.util.Map;

public interface ScheduleService {

    Map<String, Object> getScheduleWithCourses(Long userId);

    /**
     * 按学期查询已导入的课表，返回结构与 {@link #getScheduleWithCourses} 一致。
     * 该学期未导入时返回 {schedule: null, courses: []}。
     */
    Map<String, Object> getScheduleBySemester(Long userId, String semester);

    void saveSchedule(Long userId, SaveScheduleRequest request);

    void deleteSchedule(Long userId, String semester);

    String generateShareToken(Long userId);

    Map<String, Object> getSharedSchedule(String token);
}

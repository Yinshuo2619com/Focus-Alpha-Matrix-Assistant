package com.educate.assistant.dto;

import lombok.Data;
import java.util.List;

@Data
public class SaveScheduleRequest {
    private String semester;
    private String academicYear;
    private String schoolId = "default";
    private String startDate;
    private List<CourseEntryDTO> courses;
}

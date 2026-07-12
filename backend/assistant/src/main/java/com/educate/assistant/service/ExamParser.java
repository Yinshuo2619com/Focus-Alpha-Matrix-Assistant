package com.educate.assistant.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.*;

/**
 * 考试安排页面解析器（纯工具类，无需 Spring 管理）
 */
public class ExamParser {

    /**
     * 解析考试安排页面 HTML
     * @param html 页面 HTML
     * @return 考试列表，每项包含 courseName, dateTime, room, building, campus
     */
    public static List<Map<String, String>> parse(String html) {
        List<Map<String, String>> exams = new ArrayList<>();

        if (html == null || html.isBlank()) {
            return exams;
        }

        try {
            Document doc = Jsoup.parse(html);

            // 查找考试表格（根据教务系统页面结构）
            // 注意：实际选择器需要根据教务系统页面 HTML 结构调整
            Elements rows = doc.select("table.exam-table tbody tr, table tbody tr");

            for (Element row : rows) {
                Elements cells = row.select("td");
                if (cells.size() < 5) continue;

                String courseName = cells.get(0).text().trim();
                String dateTime = cells.get(1).text().trim();
                String room = cells.get(2).text().trim();
                String building = cells.get(3).text().trim();
                String campus = cells.get(4).text().trim();

                if (courseName.isEmpty() || dateTime.isEmpty()) {
                    continue;
                }

                Map<String, String> exam = new HashMap<>();
                exam.put("courseName", courseName);
                exam.put("dateTime", dateTime);
                exam.put("room", room);
                exam.put("building", building);
                exam.put("campus", campus);

                exams.add(exam);
            }
        } catch (Exception e) {
            System.out.println("[ExamParser] Failed to parse exam HTML: " + e.getMessage());
            return new ArrayList<>();
        }

        return exams;
    }
}

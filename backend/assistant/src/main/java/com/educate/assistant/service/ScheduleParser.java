package com.educate.assistant.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ScheduleParser {

    // Match patterns like "1 (5,6)" or "3 (10,11)"
    private static final Pattern DAY_SECTION_PATTERN = Pattern.compile("(\\d+)\\s*\\(([^)]+)\\)");
    // Match week pattern like "(9周)" or "(1-16周)"
    private static final Pattern WEEKS_PATTERN = Pattern.compile("[（(]([^）)]*周)[）)]");

    public static List<Map<String, Object>> parse(String html) {
        List<Map<String, Object>> courses = new ArrayList<>();
        Document doc = Jsoup.parse(html);

        // Find all weekday columns
        Elements weekdayColumns = doc.select("div.columns.weekday");
        if (weekdayColumns.isEmpty()) {
            // Fallback: try old table-based parser
            return parseOldFormat(html);
        }

        for (int dayIndex = 0; dayIndex < weekdayColumns.size(); dayIndex++) {
            int dayOfWeek = dayIndex + 1; // 1=Monday .. 7=Sunday
            if (dayOfWeek > 7) break;

            Element column = weekdayColumns.get(dayIndex);
            Elements cards = column.select("div.card-view");

            for (Element card : cards) {
                Element info = card.select("p.card-content-info").first();
                if (info == null) continue;

                String text = info.html().replace("&nbsp;", " ").trim();
                String[] lines = text.split("<br\\s*/?>");

                Map<String, Object> entry = new HashMap<>();
                entry.put("dayOfWeek", dayOfWeek);

                // First line: course name (may have trailing HTML)
                if (lines.length > 0) {
                    String courseName = Jsoup.parse(lines[0]).text().trim();
                    entry.put("courseName", courseName);
                }

                // Second line: location
                if (lines.length > 1) {
                    String location = Jsoup.parse(lines[1]).text().trim();
                    if (!location.isEmpty()) {
                        entry.put("location", location);
                    }
                }

                // Parse remaining lines for weeks, day+section, teacher
                for (int i = 2; i < lines.length; i++) {
                    String line = Jsoup.parse(lines[i]).text().trim();
                    if (line.isEmpty()) continue;

                    // Week pattern: "(9周)"
                    Matcher weekMatcher = WEEKS_PATTERN.matcher(line);
                    if (weekMatcher.find()) {
                        entry.put("weeks", weekMatcher.group(1));
                        continue;
                    }

                    // Day + section pattern: "1 (5,6)" or "3 (10,11)"
                    Matcher dsMatcher = DAY_SECTION_PATTERN.matcher(line);
                    if (dsMatcher.find()) {
                        // int parsedDay = Integer.parseInt(dsMatcher.group(1)); // already know from column
                        String sections = dsMatcher.group(2);
                        String[] parts = sections.split(",");
                        if (parts.length >= 2) {
                            int startSection = Integer.parseInt(parts[0].trim());
                            int endSection = Integer.parseInt(parts[parts.length - 1].trim());
                            entry.put("startSection", startSection);
                            entry.put("endSection", endSection);
                        } else if (parts.length == 1) {
                            int section = Integer.parseInt(parts[0].trim());
                            entry.put("startSection", section);
                            entry.put("endSection", section);
                        }
                        continue;
                    }

                    // Skip group/capacity info
                    if (line.contains("上课组") || line.contains("人数")) continue;

                    // Remaining line might be teacher
                    if (!entry.containsKey("teacher") && !line.isEmpty()) {
                        entry.put("teacher", line);
                    }
                }

                if (entry.containsKey("courseName") && entry.get("courseName") != null
                    && !entry.get("courseName").toString().isEmpty()
                    && entry.containsKey("startSection")) {
                    courses.add(entry);
                }
            }
        }
        return courses;
    }

    /**
     * Fallback parser for old table-based format
     */
    private static List<Map<String, Object>> parseOldFormat(String html) {
        List<Map<String, Object>> courses = new ArrayList<>();
        Document doc = Jsoup.parse(html);

        Element table = doc.select("table#kbtable").first();
        if (table == null) {
            table = doc.select("table").first();
        }
        if (table == null) {
            return courses;
        }

        Elements rows = table.select("tr");
        int sectionIndex = 0;

        for (Element row : rows) {
            Elements cells = row.select("td");
            if (cells.isEmpty()) continue;

            sectionIndex++;
            int dayOfWeek = 0;

            for (Element cell : cells) {
                dayOfWeek++;
                if (dayOfWeek > 7) break;

                String cellText = cell.text().trim();
                if (cellText.isEmpty()) continue;

                String cellHtml = cell.html();
                String[] blocks = cellHtml.split("(?=<font|<div|<span|<p|<a)");

                for (String block : blocks) {
                    if (block.trim().isEmpty()) continue;
                    Element blockEl = Jsoup.parse(block);
                    String text = blockEl.text().trim();
                    if (text.isEmpty()) continue;

                    Map<String, Object> entry = new HashMap<>();
                    entry.put("dayOfWeek", dayOfWeek);

                    Element nameEl = blockEl.select("b, strong, font[color]").first();
                    if (nameEl != null) {
                        entry.put("courseName", nameEl.text().trim());
                    } else {
                        String[] lines = text.split("[\\n\\r]+");
                        if (lines.length > 0) {
                            entry.put("courseName", lines[0].trim());
                        }
                    }

                    String weeks = extractWeeks(text);
                    entry.put("weeks", weeks);

                    String[] lines = text.split("[\\n\\r]+");
                    for (String line : lines) {
                        String trimmed = line.trim();
                        if (trimmed.isEmpty()) continue;
                        if (trimmed.equals(entry.get("courseName"))) continue;
                        if (trimmed.contains("周")) continue;

                        if (trimmed.contains("教室") || trimmed.contains("楼") || trimmed.contains("室") || trimmed.contains("栋")) {
                            entry.put("location", trimmed);
                        } else if (!entry.containsKey("teacher") && !trimmed.isEmpty()) {
                            entry.put("teacher", trimmed);
                        }
                    }

                    int startSection = (sectionIndex - 1) * 2 + 1;
                    int endSection = startSection + 1;
                    entry.put("startSection", startSection);
                    entry.put("endSection", endSection);

                    if (entry.containsKey("courseName") && entry.get("courseName") != null
                        && !entry.get("courseName").toString().isEmpty()) {
                        courses.add(entry);
                    }
                }
            }
        }
        return courses;
    }

    private static String extractWeeks(String text) {
        Matcher m = WEEKS_PATTERN.matcher(text);
        if (m.find()) {
            return m.group(1).replace("周", "").trim();
        }
        return "";
    }
}

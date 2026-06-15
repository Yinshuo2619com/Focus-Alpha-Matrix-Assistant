package com.educate.assistant.controller;

import com.educate.assistant.common.Result;
import com.educate.assistant.service.ElectricityService;
import com.educate.assistant.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/electricity")
public class ElectricityController {

    @Autowired
    private ElectricityService electricityService;

    @Autowired
    private UserService userService;

    /**
     * 获取楼栋列表
     */
    @GetMapping("/buildings")
    public Result<List<Map<String, Object>>> getBuildings() {
        List<Map<String, Object>> list = ElectricityService.BUILDINGS.entrySet().stream()
            .map(e -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("buiId", e.getKey());
                m.put("name", e.getValue());
                return m;
            })
            .collect(Collectors.toList());
        return Result.success(list);
    }

    /**
     * 获取某楼栋的房间列表
     */
    @GetMapping("/rooms")
    public Result<List<Map<String, Object>>> getRooms(@RequestParam int buiId) {
        List<Map<String, Object>> rooms = electricityService.getRooms(buiId);
        return Result.success(rooms);
    }

    /**
     * 绑定宿舍
     */
    @PostMapping("/bind")
    public Result<Void> bind(@RequestBody Map<String, Object> body) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Long userId = userService.findUserIdByUsername(username);
        int roomId = ((Number) body.get("roomId")).intValue();
        int buiId = ((Number) body.get("buiId")).intValue();
        String roomName = (String) body.get("roomName");
        electricityService.bindRoom(userId, roomId, buiId, roomName);
        return Result.success(null);
    }

    /**
     * 解绑宿舍
     */
    @DeleteMapping("/bind")
    public Result<Void> unbind() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Long userId = userService.findUserIdByUsername(username);
        electricityService.unbindRoom(userId);
        return Result.success(null);
    }

    /**
     * 获取电费概览（余额、昨日耗电、宿舍名）
     */
    @GetMapping("/summary")
    public Result<Map<String, Object>> getSummary() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Long userId = userService.findUserIdByUsername(username);
        Map<String, Object> summary = electricityService.getSummary(userId);
        return Result.success(summary);
    }

    /**
     * 获取历史用电数据（含楼栋平均）
     */
    @GetMapping("/history")
    public Result<Map<String, Object>> getHistory(@RequestParam(defaultValue = "7") int days) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Long userId = userService.findUserIdByUsername(username);
        // 限制最多查看30天
        if (days > 30) days = 30;
        Map<String, Object> history = electricityService.getHistory(userId, days);
        return Result.success(history);
    }

    /**
     * 实时查询当前余额（直接调用外部API，不走DB）
     */
    @GetMapping("/realtime")
    public Result<Map<String, Object>> getRealtime() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Long userId = userService.findUserIdByUsername(username);
        Map<String, Object> data = electricityService.getRealtimeBalance(userId);
        return Result.success(data);
    }

    /**
     * 保存/更新充值记录
     */
    @PostMapping("/recharge")
    public Result<Void> saveRecharge(@RequestBody Map<String, Object> body) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Long userId = userService.findUserIdByUsername(username);
        LocalDate recordDate = LocalDate.parse((String) body.get("recordDate"));
        BigDecimal kwh = new BigDecimal(body.get("kwh").toString());
        BigDecimal price = body.get("price") != null ? new BigDecimal(body.get("price").toString()) : null;
        electricityService.saveRecharge(userId, recordDate, kwh, price);
        return Result.success(null);
    }

    /**
     * 获取充值记录列表
     */
    @GetMapping("/recharge")
    public Result<List<Map<String, Object>>> getRecharges() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Long userId = userService.findUserIdByUsername(username);
        List<Map<String, Object>> recharges = electricityService.getRecharges(userId);
        return Result.success(recharges);
    }

    /**
     * 获取楼栋用电排行榜
     * @param type "top"（耗电最高）或 "bottom"（耗电最低）
     */
    @GetMapping("/ranking")
    public Result<Map<String, Object>> getRanking(@RequestParam(defaultValue = "top") String type) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Long userId = userService.findUserIdByUsername(username);
        Map<String, Object> ranking = electricityService.getRanking(userId, type);
        return Result.success(ranking);
    }

    /**
     * 手动触发采集（管理员）
     */
    @PostMapping("/collect")
    public Result<String> manualCollect() {
        if (!isAdmin()) return Result.error(403, "仅管理员可操作");
        new Thread(() -> {
            List<Integer> buiIds = electricityService.collectAll();
            LocalDate today = LocalDate.now();
            for (int buiId : buiIds) {
                try {
                    electricityService.computeAndCacheRanking(buiId, today);
                    electricityService.computeAndCacheRanking(buiId, today.minusDays(1));
                } catch (Exception e) {
                    // ignore
                }
            }
        }).start();
        return Result.success("采集任务已触发");
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}

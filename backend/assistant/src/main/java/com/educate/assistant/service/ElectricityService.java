package com.educate.assistant.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@SuppressWarnings("unchecked")
public class ElectricityService {

    private static final Logger log = LoggerFactory.getLogger(ElectricityService.class);
    private static final String BASE_URL = "https://card.aqnu.edu.cn/epay/wxpage/wanxiao";

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;

    // 楼栋列表（buiId → 楼栋名称）
    public static final Map<Integer, String> BUILDINGS = new LinkedHashMap<>();
    static {
        BUILDINGS.put(85, "1号楼A"); BUILDINGS.put(86, "1号楼B");
        BUILDINGS.put(87, "2号楼A"); BUILDINGS.put(88, "2号楼B");
        BUILDINGS.put(89, "3号楼A"); BUILDINGS.put(90, "3号楼B");
        BUILDINGS.put(91, "4号楼A"); BUILDINGS.put(92, "4号楼B");
        BUILDINGS.put(93, "5号楼A"); BUILDINGS.put(94, "5号楼B");
        BUILDINGS.put(95, "6号楼A"); BUILDINGS.put(96, "6号楼B");
        BUILDINGS.put(97, "7号楼A"); BUILDINGS.put(98, "7号楼B");
        BUILDINGS.put(99, "8号楼A"); BUILDINGS.put(100, "8号楼B");
        BUILDINGS.put(101, "9号楼A"); BUILDINGS.put(102, "9号楼B");
        BUILDINGS.put(8, "10号楼A"); BUILDINGS.put(9, "10号楼B");
        BUILDINGS.put(10, "11号楼A"); BUILDINGS.put(11, "11号楼B");
        BUILDINGS.put(12, "12号楼A"); BUILDINGS.put(13, "12号楼B");
        BUILDINGS.put(14, "13号楼A"); BUILDINGS.put(15, "13号楼B");
        BUILDINGS.put(16, "14号楼A"); BUILDINGS.put(17, "14号楼B");
        BUILDINGS.put(1, "15号楼A"); BUILDINGS.put(78, "15号楼B");
        BUILDINGS.put(233, "16号楼A"); BUILDINGS.put(234, "16号楼B");
        BUILDINGS.put(199, "27号楼"); BUILDINGS.put(200, "29号楼");
        BUILDINGS.put(201, "30号楼"); BUILDINGS.put(202, "31号楼");
        BUILDINGS.put(203, "32号楼"); BUILDINGS.put(247, "33号楼");
    }

    public ElectricityService(JdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.httpClient = createHttpClient();
    }

    private HttpClient createHttpClient() {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    // ==================== 房间列表缓存 ====================

    // buiId → List<{roomId, roomName}>
    private final Map<Integer, List<Map<String, Object>>> roomCache = new ConcurrentHashMap<>();

    /**
     * 获取某楼栋所有房间列表（调用外部API，带缓存）
     */
    public List<Map<String, Object>> getRooms(int buiId) {
        return roomCache.computeIfAbsent(buiId, id -> {
            try {
                String form = "sysid=1&areaid=1&districtid=1&buildid=" + id + "&floorid=1";
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/getroom.json"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .timeout(Duration.ofSeconds(15))
                    .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                // 响应格式: {"list": [{"roomId":"6332","roomName":"6B-101  ..."}, ...]}
                Map<String, Object> resp = objectMapper.readValue(response.body(), new TypeReference<>() {});
                List<Map<String, Object>> rawList = (List<Map<String, Object>>) resp.get("list");
                if (rawList == null) return Collections.emptyList();
                // 转换字段类型：roomId String→int，roomName 去空格
                List<Map<String, Object>> result = new ArrayList<>();
                for (Map<String, Object> raw : rawList) {
                    Map<String, Object> room = new LinkedHashMap<>();
                    room.put("roomId", Integer.parseInt((String) raw.get("roomId")));
                    room.put("roomName", ((String) raw.get("roomName")).trim());
                    result.add(room);
                }
                return result;
            } catch (Exception e) {
                log.error("获取楼栋 {} 房间列表失败: {}", id, e.getMessage());
                return Collections.emptyList();
            }
        });
    }

    // ==================== 电费查询 ====================

    /**
     * 查询单个房间的当前余额（调用校园卡API，解析HTML）
     */
    public BigDecimal queryBalance(int roomId, int buiId) {
        try {
            String url = BASE_URL + "/eleresult?sysid=1&roomid=" + roomId + "&areaid=1&buildid=" + buiId;
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return parseBalance(response.body());
        } catch (Exception e) {
            log.debug("查询房间 {} 余额失败: {}", roomId, e.getMessage());
            return null;
        }
    }

    /**
     * 从 eleresult HTML 中解析剩余电量
     * HTML 结构: <div class="weui-cell__bd"><label class="weui-label">剩余电量</label></div><div>519.91度</div>
     */
    private BigDecimal parseBalance(String html) {
        try {
            Document doc = Jsoup.parse(html);
            // 方法1: 通过 weui-label "剩余电量" 定位
            Elements labels = doc.select("label.weui-label:contains(剩余电量)");
            if (!labels.isEmpty()) {
                Element parentCell = labels.first().parent();
                if (parentCell != null) {
                    Element sibling = parentCell.nextElementSibling();
                    if (sibling != null) {
                        String text = sibling.text().trim();
                        java.util.regex.Matcher m = java.util.regex.Pattern
                            .compile("(\\d+\\.?\\d*)")
                            .matcher(text);
                        if (m.find()) {
                            return new BigDecimal(m.group(1));
                        }
                    }
                }
            }
            // 方法2: fallback - 全文匹配 "数字度"
            java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d+\\.\\d+)\\s*度")
                .matcher(html);
            if (m.find()) {
                return new BigDecimal(m.group(1));
            }
        } catch (Exception e) {
            log.debug("解析余额HTML失败: {}", e.getMessage());
        }
        return null;
    }

    // ==================== 采集 ====================

    /**
     * 执行电费采集（由定时任务调用）
     * 凌晨4点采集，record_date 为当天
     */
    public List<Integer> collectAll() {
        LocalDate today = LocalDate.now();
        log.info("开始电费采集，日期: {}", today);
        List<Integer> collectedBuiIds = new ArrayList<>();

        // 1. 查出今日已采集的 roomId 集合
        Set<Integer> collectedToday = new HashSet<>(
            jdbcTemplate.queryForList(
                "SELECT room_id FROM electricity_balance WHERE record_date = ?",
                Integer.class, today)
        );

        // 2. 查出所有绑定了宿舍的 buiId 去重
        List<Integer> boundBuiIds = jdbcTemplate.queryForList(
            "SELECT DISTINCT bui_id FROM user WHERE bui_id IS NOT NULL",
            Integer.class);

        if (boundBuiIds.isEmpty()) {
            log.info("没有用户绑定宿舍，跳过采集");
            return collectedBuiIds;
        }

        int totalCollected = 0;
        for (int buiId : boundBuiIds) {
            List<Map<String, Object>> rooms = getRooms(buiId);
            if (rooms.isEmpty()) continue;

            // 过滤掉今日已采集的
            List<Map<String, Object>> toCollect = rooms.stream()
                .filter(r -> !collectedToday.contains(((Number) r.get("roomId")).intValue()))
                .collect(Collectors.toList());

            if (toCollect.isEmpty()) continue;

            log.info("采集楼栋 {} ({}), 共 {} 间房间", BUILDINGS.getOrDefault(buiId, String.valueOf(buiId)), buiId, toCollect.size());

            // 并发采集：Future 返回 Object[]{roomId, roomName, balance}
            ExecutorService executor = Executors.newFixedThreadPool(10);
            List<Future<Object[]>> futures = new ArrayList<>();

            for (Map<String, Object> room : toCollect) {
                int roomId = ((Number) room.get("roomId")).intValue();
                String roomName = (String) room.get("roomName");
                futures.add(executor.submit(() -> {
                    BigDecimal balance = queryBalance(roomId, buiId);
                    if (balance != null) {
                        return new Object[]{roomId, roomName, balance};
                    }
                    return null;
                }));
            }

            // 收集结果并批量插入
            List<Object[]> batchArgs = new ArrayList<>();
            List<Object[]> rechargeArgs = new ArrayList<>();
            for (Future<Object[]> future : futures) {
                try {
                    Object[] result = future.get(15, TimeUnit.SECONDS);
                    if (result != null) {
                        int roomId = (int) result[0];
                        String roomName = (String) result[1];
                        BigDecimal balance = (BigDecimal) result[2];

                        // 检测余额上升（可能充值）
                        BigDecimal rechargeKwh = detectRecharge(roomId, today, balance);
                        if (rechargeKwh != null) {
                            rechargeArgs.add(new Object[]{roomId, today, rechargeKwh});
                            log.info("检测到房间 {} 余额上升 {} 度，标记为待确认充值", roomId, rechargeKwh);
                        }

                        // consumption 先存 null，由 recalcYesterday 在第二天回填
                        batchArgs.add(new Object[]{roomId, buiId, roomName, balance, null, today});
                        totalCollected++;
                    }
                } catch (Exception e) {
                    log.debug("采集任务异常: {}", e.getMessage());
                }
            }

            executor.shutdown();

            if (!batchArgs.isEmpty()) {
                String sql = "INSERT IGNORE INTO electricity_balance (room_id, bui_id, room_name, balance, consumption, record_date) VALUES (?, ?, ?, ?, ?, ?)";
                jdbcTemplate.batchUpdate(sql, batchArgs);
                log.info("楼栋 {} 采集完成，插入 {} 条记录", BUILDINGS.getOrDefault(buiId, String.valueOf(buiId)), batchArgs.size());

                // 回算昨天的 consumption（用今天的 balance 作为昨天的结束余额）
                for (Object[] args : batchArgs) {
                    int roomId = (int) args[0];
                    BigDecimal todayBalance = (BigDecimal) args[3];
                    recalcYesterday(roomId, today, todayBalance);
                }
            }

            if (!rechargeArgs.isEmpty()) {
                String rechargeSql = "INSERT IGNORE INTO electricity_recharge (room_id, record_date, kwh, confirmed) VALUES (?, ?, ?, 0)";
                jdbcTemplate.batchUpdate(rechargeSql, rechargeArgs);
                log.info("楼栋 {} 检测到 {} 条充值", BUILDINGS.getOrDefault(buiId, String.valueOf(buiId)), rechargeArgs.size());
            }

            collectedBuiIds.add(buiId);
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        }

        // 清理6个月前的数据
        LocalDate sixMonthsAgo = today.minusMonths(6);
        int deleted = jdbcTemplate.update("DELETE FROM electricity_balance WHERE record_date < ?", sixMonthsAgo);
        if (deleted > 0) {
            log.info("清理过期数据 {} 条", deleted);
        }

        log.info("电费采集完成，共采集 {} 条", totalCollected);
        return collectedBuiIds;
    }

    /**
     * 检测余额上升（可能充值）
     * @return 充值度数，如果没有上升返回 null
     */
    private BigDecimal detectRecharge(int roomId, LocalDate today, BigDecimal todayBalance) {
        try {
            BigDecimal yesterdayBalance = jdbcTemplate.queryForObject(
                "SELECT balance FROM electricity_balance WHERE room_id = ? AND record_date = ?",
                BigDecimal.class, roomId, today.minusDays(1));
            if (yesterdayBalance != null && todayBalance.compareTo(yesterdayBalance) > 0) {
                return todayBalance.subtract(yesterdayBalance);
            }
        } catch (Exception e) {
            // 无昨日记录
        }
        return null;
    }

    /**
     * 回算昨天的 consumption（用今天的 balance 作为昨天的结束余额）
     * 两次采集都是凌晨4点，所以 todayBalance 是昨天结束时的实际余额
     */
    private void recalcYesterday(int roomId, LocalDate today, BigDecimal todayBalance) {
        LocalDate yesterday = today.minusDays(1);
        try {
            BigDecimal yesterdayBalance = jdbcTemplate.queryForObject(
                "SELECT balance FROM electricity_balance WHERE room_id = ? AND record_date = ?",
                BigDecimal.class, roomId, yesterday);
            if (yesterdayBalance == null) return;

            // 检查昨天是否有已确认的充值
            BigDecimal rechargeKwh = BigDecimal.ZERO;
            try {
                Map<String, Object> recharge = jdbcTemplate.queryForMap(
                    "SELECT kwh, confirmed FROM electricity_recharge WHERE room_id = ? AND record_date = ?",
                    roomId, yesterday);
                if (((Number) recharge.get("confirmed")).intValue() == 1) {
                    rechargeKwh = (BigDecimal) recharge.get("kwh");
                }
            } catch (Exception ignored) {}

            // consumption = 昨日余额 + 充值 - 今日余额（今日余额即昨日结束余额）
            BigDecimal consumption = yesterdayBalance.add(rechargeKwh).subtract(todayBalance);
            if (consumption.signum() < 0) consumption = BigDecimal.ZERO;

            jdbcTemplate.update(
                "UPDATE electricity_balance SET consumption = ? WHERE room_id = ? AND record_date = ?",
                consumption, roomId, yesterday);
        } catch (Exception ignored) {}
    }

    // ==================== 查询接口 ====================

    /**
     * 获取用户电费概览
     */
    public Map<String, Object> getSummary(Long userId) {
        Map<String, Object> user = jdbcTemplate.queryForMap(
            "SELECT room_id, bui_id, room_name FROM user WHERE id = ?", userId);
        Integer roomId = user.get("room_id") != null ? ((Number) user.get("room_id")).intValue() : null;
        if (roomId == null) return null;

        Map<String, Object> result = new HashMap<>();
        result.put("roomId", roomId);
        result.put("buiId", user.get("bui_id") != null ? ((Number) user.get("bui_id")).intValue() : null);
        result.put("roomName", user.get("room_name"));

        LocalDate today = LocalDate.now();

        // 今日余额
        try {
            Map<String, Object> todayRecord = jdbcTemplate.queryForMap(
                "SELECT balance, consumption FROM electricity_balance WHERE room_id = ? AND record_date = ?",
                roomId, today);
            result.put("balance", todayRecord.get("balance"));
            result.put("consumption", todayRecord.get("consumption"));
        } catch (Exception e) {
            // 今日无记录，尝试查最近的
            try {
                Map<String, Object> latest = jdbcTemplate.queryForMap(
                    "SELECT balance, consumption, record_date FROM electricity_balance WHERE room_id = ? ORDER BY record_date DESC LIMIT 1",
                    roomId);
                result.put("balance", latest.get("balance"));
                result.put("consumption", latest.get("consumption"));
                result.put("lastUpdate", latest.get("record_date"));
            } catch (Exception e2) {
                result.put("balance", null);
                result.put("consumption", null);
            }
        }

        return result;
    }

    /**
     * 获取历史用电数据（含楼栋平均）
     */
    public Map<String, Object> getHistory(Long userId, int days) {
        Map<String, Object> user = jdbcTemplate.queryForMap(
            "SELECT room_id, bui_id, room_name FROM user WHERE id = ?", userId);
        Integer roomId = user.get("room_id") != null ? ((Number) user.get("room_id")).intValue() : null;
        Integer buiId = user.get("bui_id") != null ? ((Number) user.get("bui_id")).intValue() : null;
        if (roomId == null) return null;

        LocalDate endDate = LocalDate.now();
        // 多查1天，因为前端显示时日期前移一天，需要额外一天的数据
        LocalDate startDate = endDate.minusDays(days + 1);

        // 个人每日用电
        List<Map<String, Object>> personal = jdbcTemplate.queryForList(
            "SELECT record_date, balance, consumption FROM electricity_balance WHERE room_id = ? AND record_date >= ? AND record_date <= ? ORDER BY record_date",
            roomId, startDate, endDate);
        for (Map<String, Object> row : personal) {
            row.put("record_date", row.get("record_date").toString());
        }

        // 楼栋每日平均用电
        List<Map<String, Object>> buildingAvg = Collections.emptyList();
        if (buiId != null) {
            buildingAvg = jdbcTemplate.queryForList(
                "SELECT record_date, AVG(consumption) as avg_consumption FROM electricity_balance WHERE bui_id = ? AND consumption > 0 AND record_date >= ? AND record_date <= ? GROUP BY record_date ORDER BY record_date",
                buiId, startDate, endDate);
            for (Map<String, Object> row : buildingAvg) {
                row.put("record_date", row.get("record_date").toString());
            }
        }

        // 充值记录
        List<Map<String, Object>> recharges = jdbcTemplate.queryForList(
            "SELECT record_date, amount, kwh, confirmed FROM electricity_recharge WHERE room_id = ? AND record_date >= ? AND record_date <= ? ORDER BY record_date",
            roomId, startDate, endDate);
        for (Map<String, Object> row : recharges) {
            row.put("record_date", row.get("record_date").toString());
        }

        // 统计
        BigDecimal totalConsumption = personal.stream()
            .filter(r -> r.get("consumption") != null)
            .map(r -> (BigDecimal) r.get("consumption"))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        long daysWithConsumption = personal.stream()
            .filter(r -> r.get("consumption") != null)
            .count();

        BigDecimal avgPersonal = daysWithConsumption > 0
            ? totalConsumption.divide(BigDecimal.valueOf(daysWithConsumption), 2, java.math.RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        BigDecimal avgBuilding = BigDecimal.ZERO;
        if (!buildingAvg.isEmpty()) {
            BigDecimal sum = buildingAvg.stream()
                .map(r -> (BigDecimal) r.get("avg_consumption"))
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            avgBuilding = sum.divide(BigDecimal.valueOf(buildingAvg.size()), 2, java.math.RoundingMode.HALF_UP);
        }

        // 当前余额
        BigDecimal currentBalance = personal.isEmpty() ? null :
            (BigDecimal) personal.get(personal.size() - 1).get("balance");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("personal", personal);
        result.put("buildingAvg", buildingAvg);
        result.put("recharges", recharges);
        result.put("totalConsumption", totalConsumption);
        result.put("avgPersonal", avgPersonal);
        result.put("avgBuilding", avgBuilding);
        result.put("currentBalance", currentBalance);
        result.put("roomName", user.get("room_name"));
        return result;
    }

    /**
     * 实时查询用户绑定宿舍的当前余额（直接调用外部API）
     */
    public Map<String, Object> getRealtimeBalance(Long userId) {
        Map<String, Object> user = jdbcTemplate.queryForMap(
            "SELECT room_id, bui_id, room_name FROM user WHERE id = ?", userId);
        Integer roomId = user.get("room_id") != null ? ((Number) user.get("room_id")).intValue() : null;
        Integer buiId = user.get("bui_id") != null ? ((Number) user.get("bui_id")).intValue() : null;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("roomId", roomId);
        result.put("buiId", buiId);
        result.put("roomName", user.get("room_name"));

        if (roomId == null || buiId == null) {
            result.put("balance", null);
            result.put("consumption", null);
            return result;
        }

        BigDecimal balance = queryBalance(roomId, buiId);
        result.put("balance", balance);

        // 查最近一次的 consumption
        try {
            BigDecimal consumption = jdbcTemplate.queryForObject(
                "SELECT consumption FROM electricity_balance WHERE room_id = ? AND consumption IS NOT NULL ORDER BY record_date DESC LIMIT 1",
                BigDecimal.class, roomId);
            result.put("consumption", consumption);
        } catch (Exception e) {
            result.put("consumption", null);
        }
        return result;
    }

    // ==================== 充值记录 ====================

    /**
     * 保存/更新充值记录
     */
    public void saveRecharge(Long userId, LocalDate recordDate, BigDecimal amount, BigDecimal kwh) {
        Map<String, Object> user = jdbcTemplate.queryForMap(
            "SELECT room_id FROM user WHERE id = ?", userId);
        Integer roomId = user.get("room_id") != null ? ((Number) user.get("room_id")).intValue() : null;
        if (roomId == null) throw new RuntimeException("未绑定宿舍");

        // 检查是否已有记录
        try {
            jdbcTemplate.queryForMap(
                "SELECT id FROM electricity_recharge WHERE room_id = ? AND record_date = ?",
                roomId, recordDate);
            // 已有记录，更新
            jdbcTemplate.update(
                "UPDATE electricity_recharge SET amount = ?, kwh = ?, confirmed = 1 WHERE room_id = ? AND record_date = ?",
                amount, kwh, roomId, recordDate);
        } catch (Exception e) {
            // 无记录，插入
            jdbcTemplate.update(
                "INSERT INTO electricity_recharge (room_id, record_date, amount, kwh, confirmed) VALUES (?, ?, ?, ?, 1)",
                roomId, recordDate, amount, kwh);
        }

        // 重新计算该天的 consumption
        recalcConsumption(roomId, recordDate);
    }

    /**
     * 获取用户的充值记录列表
     */
    public List<Map<String, Object>> getRecharges(Long userId) {
        Map<String, Object> user = jdbcTemplate.queryForMap(
            "SELECT room_id FROM user WHERE id = ?", userId);
        Integer roomId = user.get("room_id") != null ? ((Number) user.get("room_id")).intValue() : null;
        if (roomId == null) return Collections.emptyList();

        return jdbcTemplate.queryForList(
            "SELECT record_date, amount, kwh, confirmed FROM electricity_recharge WHERE room_id = ? ORDER BY record_date DESC",
            roomId);
    }

    /**
     * 重新计算指定日期的 consumption（充值确认后调用）
     */
    private void recalcConsumption(int roomId, LocalDate date) {
        try {
            Map<String, Object> balanceRecord = jdbcTemplate.queryForMap(
                "SELECT balance FROM electricity_balance WHERE room_id = ? AND record_date = ?",
                roomId, date);
            BigDecimal todayBalance = (BigDecimal) balanceRecord.get("balance");

            BigDecimal yesterdayBalance = jdbcTemplate.queryForObject(
                "SELECT balance FROM electricity_balance WHERE room_id = ? AND record_date = ?",
                BigDecimal.class, roomId, date.minusDays(1));
            if (yesterdayBalance == null) return;

            Map<String, Object> recharge = jdbcTemplate.queryForMap(
                "SELECT kwh FROM electricity_recharge WHERE room_id = ? AND record_date = ? AND confirmed = 1",
                roomId, date);
            BigDecimal kwh = (BigDecimal) recharge.get("kwh");

            BigDecimal consumption = yesterdayBalance.add(kwh).subtract(todayBalance);
            jdbcTemplate.update(
                "UPDATE electricity_balance SET consumption = ? WHERE room_id = ? AND record_date = ?",
                consumption, roomId, date);
        } catch (Exception e) {
            log.debug("重新计算 consumption 失败: roomId={}, date={}, error={}", roomId, date, e.getMessage());
        }
    }

    // ==================== 绑定/解绑 ====================

    public void bindRoom(Long userId, int roomId, int buiId, String roomName) {
        jdbcTemplate.update(
            "UPDATE user SET room_id = ?, bui_id = ?, room_name = ?, updated_at = NOW() WHERE id = ?",
            roomId, buiId, roomName, userId);
    }

    public void unbindRoom(Long userId) {
        jdbcTemplate.update(
            "UPDATE user SET room_id = NULL, bui_id = NULL, room_name = NULL, updated_at = NOW() WHERE id = ?",
            userId);
    }

    // ==================== 排行榜 ====================

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 预计算楼栋排行榜并写入 Redis（采集完成后调用）
     */
    public void computeAndCacheRanking(int buiId, LocalDate recordDate) {
        try {
            String dateStr = recordDate.format(DATE_FMT);
            String sql = "SELECT room_name, consumption FROM electricity_balance " +
                         "WHERE bui_id = ? AND record_date = ? AND consumption IS NOT NULL AND consumption > 0";

            List<Map<String, Object>> rooms = jdbcTemplate.queryForList(sql, buiId, recordDate);
            if (rooms.isEmpty()) return;

            // 按 consumption 排序
            rooms.sort((a, b) -> {
                BigDecimal ca = (BigDecimal) a.get("consumption");
                BigDecimal cb = (BigDecimal) b.get("consumption");
                return cb.compareTo(ca); // DESC
            });

            // Top 30
            List<Map<String, Object>> top30 = rooms.subList(0, Math.min(30, rooms.size()));
            String topJson = objectMapper.writeValueAsString(top30);
            String topKey = "elec:ranking:" + buiId + ":" + dateStr + ":top";
            redisTemplate.opsForValue().set(topKey, topJson, 25, TimeUnit.HOURS);

            // Bottom 30
            List<Map<String, Object>> bottom30 = rooms.subList(
                Math.max(0, rooms.size() - 30), rooms.size());
            String bottomJson = objectMapper.writeValueAsString(bottom30);
            String bottomKey = "elec:ranking:" + buiId + ":" + dateStr + ":bottom";
            redisTemplate.opsForValue().set(bottomKey, bottomJson, 25, TimeUnit.HOURS);

            log.info("楼栋 {} 排行榜已缓存 ({}), 共 {} 间房间", buiId, dateStr, rooms.size());
        } catch (Exception e) {
            log.error("缓存楼栋 {} 排行榜失败: {}", buiId, e.getMessage());
        }
    }

    /**
     * 获取排行榜数据
     * @param userId 用户ID
     * @param type "top" 或 "bottom"
     */
    public Map<String, Object> getRanking(Long userId, String type) {
        // 获取用户绑定信息
        Map<String, Object> user = jdbcTemplate.queryForMap(
            "SELECT room_id, bui_id, room_name FROM user WHERE id = ?", userId);
        Integer buiId = user.get("bui_id") != null ? ((Number) user.get("bui_id")).intValue() : null;
        String roomName = (String) user.get("room_name");
        if (buiId == null) throw new RuntimeException("请先绑定宿舍");

        // 排行日期为昨天（consumption 代表昨天的用量）
        LocalDate rankingDate = LocalDate.now().minusDays(1);
        String dateStr = rankingDate.format(DATE_FMT);
        String cacheKey = "elec:ranking:" + buiId + ":" + dateStr + ":" + type;

        // 1. 先查 Redis
        List<Map<String, Object>> list = null;
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                list = objectMapper.readValue(cached, new TypeReference<>() {});
            }
        } catch (Exception e) {
            log.warn("读取排行榜缓存失败: {}", e.getMessage());
        }

        // 2. Redis 未命中，查数据库并回写
        if (list == null) {
            String order = "top".equals(type) ? "DESC" : "ASC";
            String sql = "SELECT room_name, consumption FROM electricity_balance " +
                         "WHERE bui_id = ? AND record_date = ? AND consumption IS NOT NULL AND consumption > 0 " +
                         "ORDER BY consumption " + order + " LIMIT 30";
            list = jdbcTemplate.queryForList(sql, buiId, rankingDate);

            // 回写 Redis
            try {
                String json = objectMapper.writeValueAsString(list);
                redisTemplate.opsForValue().set(cacheKey, json, 25, TimeUnit.HOURS);
            } catch (Exception e) {
                log.warn("写入排行榜缓存失败: {}", e.getMessage());
            }
        }

        // 3. 查用户个人排名
        Map<String, Object> myRanking = null;
        Integer roomId = user.get("room_id") != null ? ((Number) user.get("room_id")).intValue() : null;
        if (roomId != null) {
            try {
                // 查用户自己的 consumption
                BigDecimal myConsumption = jdbcTemplate.queryForObject(
                    "SELECT consumption FROM electricity_balance WHERE room_id = ? AND record_date = ?",
                    BigDecimal.class, roomId, rankingDate);
                if (myConsumption != null) {
                    // top: 排名 = consumption 比自己大的房间数 + 1（第1名=最高）
                    // bottom: 排名 = consumption 比自己小的房间数 + 1（第1名=最低）
                    String cmp = "top".equals(type) ? ">" : "<";
                    Integer rank = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) + 1 FROM electricity_balance " +
                        "WHERE bui_id = ? AND record_date = ? AND consumption IS NOT NULL AND consumption " + cmp + " ?",
                        Integer.class, buiId, rankingDate, myConsumption);
                    myRanking = new LinkedHashMap<>();
                    myRanking.put("roomName", roomName);
                    myRanking.put("consumption", myConsumption);
                    myRanking.put("rank", rank);
                }
            } catch (Exception e) {
                // 用户房间无数据
            }
        }

        // 4. 组装返回
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("buildingName", BUILDINGS.getOrDefault(buiId, String.valueOf(buiId)));
        result.put("date", dateStr);
        result.put("list", list);
        result.put("myRanking", myRanking);
        return result;
    }
}

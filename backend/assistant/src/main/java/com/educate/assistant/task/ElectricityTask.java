package com.educate.assistant.task;

import com.educate.assistant.service.ElectricityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class ElectricityTask {

    private static final Logger log = LoggerFactory.getLogger(ElectricityTask.class);

    @Autowired
    private ElectricityService electricityService;

    /**
     * 每日 04:00 执行电费采集
     */
    @Scheduled(cron = "0 0 4 * * ?")
    public void dailyCollect() {
        log.info("定时任务：开始每日电费采集");
        try {
            List<Integer> collectedBuiIds = electricityService.collectAll();

            // 采集完成后，为每个楼栋预计算排行榜
            LocalDate today = LocalDate.now();
            for (int buiId : collectedBuiIds) {
                try {
                    electricityService.computeAndCacheRanking(buiId, today);
                } catch (Exception e) {
                    log.error("楼栋 {} 排行榜预计算失败: {}", buiId, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("电费采集异常: {}", e.getMessage(), e);
        }
    }
}

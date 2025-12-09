package com.hk.chart.scheduler;

import com.hk.chart.service.KisMarketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockScheduler {

    private final KisMarketService marketService;

    /**
     * 매일 평일(월~금) 오후 3시 40분(15:40)에 실행
     */
    @Scheduled(cron = "0 40 15 * * MON-FRI")
    public void dailyStockUpdate() {
        log.info("⏰ [Scheduler] 정기 업데이트 트리거 실행");
        marketService.updateAllWatchedStocks(); // 공통 로직 호출
    }
}
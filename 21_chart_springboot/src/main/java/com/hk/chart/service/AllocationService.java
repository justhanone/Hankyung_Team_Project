package com.hk.chart.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hk.chart.dto.AllocationDto;
import com.hk.chart.entity.BacktestHistory;
import com.hk.chart.entity.StockCandle;
import com.hk.chart.entity.User;
import com.hk.chart.repository.BacktestHistoryRepository;
import com.hk.chart.repository.StockCandleRepository;
import com.hk.chart.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AllocationService {

    private final StockCandleRepository candleRepository;
    private final BacktestHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final KisMarketService kisMarketService; // 필요 시 현재가 조회용 (선택)

    /**
     * 자산배분 분석 메인 메서드
     */
    @Transactional
    public AllocationDto.Response analyze(AllocationDto.Request req, String username) {
        // 1. 데이터 수집
        Map<String, List<StockCandle>> dataMap = new HashMap<>();
        int daysNeeded = req.getPeriodMonths() * 30 + 30; // 여유 있게 조회

        for (AllocationDto.Asset asset : req.getAssets()) {
            List<StockCandle> candles = candleRepository.findRecentCandles(asset.getCode(), daysNeeded);
            if (candles == null || candles.isEmpty()) {
                throw new RuntimeException("데이터가 부족합니다: " + asset.getName());
            }
            Collections.reverse(candles); // 과거 -> 현재 순 정렬
            dataMap.put(asset.getCode(), candles);
        }

        // 2. 공통 시작일 찾기
        String startDate = findCommonStartDate(dataMap);
        if (startDate == null) throw new RuntimeException("데이터 기간이 겹치지 않거나 부족합니다.");

        // 3. 시뮬레이션 초기화
        long currentTotal = req.getSeedMoney();
        double peak = currentTotal;
        double maxDrawdown = 0.0;

        List<AllocationDto.ChartPoint> curve = new ArrayList<>();
        Map<String, Double> shares = new HashMap<>(); // 종목별 보유 수량(소수점 포함)

        // 초기 매수 (비중대로 분배)
        for (AllocationDto.Asset asset : req.getAssets()) {
            List<StockCandle> list = dataMap.get(asset.getCode());
            StockCandle firstDay = findCandleByDate(list, startDate);
            
            if (firstDay != null) {
                double allocatedMoney = req.getSeedMoney() * (asset.getWeight() / 100.0);
                shares.put(asset.getCode(), allocatedMoney / firstDay.getClose());
            }
        }

        // 4. 일별 가치 계산 Loop
        List<Double> dailyReturns = new ArrayList<>();
        long prevTotal = currentTotal;

        // 가장 데이터가 많은 종목 기준으로 날짜 순회
        List<String> allDates = dataMap.values().iterator().next().stream()
                .map(StockCandle::getDate)
                .filter(d -> d.compareTo(startDate) >= 0)
                .collect(Collectors.toList());

        for (String date : allDates) {
            long dailyValue = 0;
            boolean isDataComplete = true;
            
            for (AllocationDto.Asset asset : req.getAssets()) {
                StockCandle candle = findCandleByDate(dataMap.get(asset.getCode()), date);
                if (candle != null) {
                    dailyValue += (long)(shares.get(asset.getCode()) * candle.getClose());
                } else {
                    isDataComplete = false; // 데이터가 하나라도 없으면 그 날은 스킵하거나 전일값 사용
                }
            }
            
            if (isDataComplete && dailyValue > 0) {
                // MDD 갱신
                if (dailyValue > peak) peak = dailyValue;
                double dd = (peak - dailyValue) / peak * 100;
                if (dd > maxDrawdown) maxDrawdown = dd;

                // 차트 데이터 추가
                curve.add(newDto(date, dailyValue));
                
                // 일간 수익률 기록 (변동성 계산용)
                if (prevTotal > 0 && !date.equals(startDate)) {
                    double dailyRate = (double)(dailyValue - prevTotal) / prevTotal;
                    dailyReturns.add(dailyRate);
                }
                prevTotal = dailyValue;
                currentTotal = dailyValue;
            }
        }

        // 5. 결과 생성 (Return & Risk)
        AllocationDto.Response res = new AllocationDto.Response();
        res.setFinalBalance(currentTotal);
        res.setMdd(Math.round(maxDrawdown * 100) / 100.0);
        res.setEquityCurve(curve);
        
        // 수익률 계산
        double totalReturn = ((double)(currentTotal - req.getSeedMoney()) / req.getSeedMoney()) * 100;
        res.setTotalReturn(Math.round(totalReturn * 100) / 100.0);
        
        // CAGR (연평균 수익률)
        double years = req.getPeriodMonths() / 12.0;
        if(years < 1) years = 1; 
        double cagr = (Math.pow((double)currentTotal / req.getSeedMoney(), 1.0 / years) - 1) * 100;
        res.setCagr(Math.round(cagr * 100) / 100.0);

        // Risk 지표 (변동성, 샤프지수)
        if (!dailyReturns.isEmpty()) {
            // 표준편차
            double mean = dailyReturns.stream().mapToDouble(val -> val).average().orElse(0.0);
            double variance = dailyReturns.stream().mapToDouble(val -> Math.pow(val - mean, 2)).sum() / dailyReturns.size();
            double stdevDaily = Math.sqrt(variance);
            
            // 연간 변동성 (Annualized Volatility) = 일간 표준편차 * sqrt(252)
            double volatility = stdevDaily * Math.sqrt(252) * 100; 
            res.setVolatility(Math.round(volatility * 100) / 100.0);

            // 샤프 지수 (Sharpe Ratio) = (CAGR - RiskFree) / Volatility
            double riskFreeRate = 3.0; // 무위험 수익률 3% 가정
            if (volatility > 0) {
                double sharpe = (cagr - riskFreeRate) / volatility;
                res.setSharpeRatio(Math.round(sharpe * 100) / 100.0);
            } else {
                res.setSharpeRatio(0.0);
            }
        } else {
            res.setVolatility(0.0);
            res.setSharpeRatio(0.0);
        }

        // 6. 이력 DB 저장 (로그인한 사용자만)
        if (username != null && !username.equals("anonymousUser")) {
            saveHistory(req, res, username);
        }
        
        return res;
    }

    /**
     * 사용자 과거 기록 조회
     */
    @Transactional(readOnly = true)
    public List<AllocationDto.HistoryResponse> getHistory(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        List<BacktestHistory> list = historyRepository.findByUserOrderByCreatedAtDesc(user);

        return list.stream()
                .map(this::toHistoryResponse)
                .collect(Collectors.toList());
    }

    // --- Private Helper Methods ---

    // 이력 저장 로직 분리
    private void saveHistory(AllocationDto.Request req, AllocationDto.Response res, String username) {
        try {
            User user = userRepository.findByUsername(username).orElseThrow();
            String assetsJson = objectMapper.writeValueAsString(req.getAssets());

            BacktestHistory history = BacktestHistory.builder()
                    .user(user)
                    .testType("ALLOCATION")
                    .seedMoney(req.getSeedMoney())
                    .periodMonths(req.getPeriodMonths())
                    .assetsJson(assetsJson)
                    .finalBalance(res.getFinalBalance())
                    .totalReturn(res.getTotalReturn())
                    .cagr(res.getCagr())
                    .mdd(res.getMdd())
                    .createdAt(LocalDateTime.now())
                    .build();

            historyRepository.save(history);
        } catch (Exception e) {
            System.err.println("이력 저장 실패: " + e.getMessage());
        }
    }

    // Entity -> Response DTO 변환
    private AllocationDto.HistoryResponse toHistoryResponse(BacktestHistory h) {
        String summary = "상세 내용 참조";
        try {
            AllocationDto.Asset[] assets = objectMapper.readValue(h.getAssetsJson(), AllocationDto.Asset[].class);
            if (assets != null && assets.length > 0) {
                summary = assets[0].getName() + " (" + assets[0].getWeight() + "%)";
                if (assets.length > 1) {
                    summary += " 외 " + (assets.length - 1) + "건";
                }
            }
        } catch (Exception e) {}

        return AllocationDto.HistoryResponse.builder()
                .id(h.getId())
                .date(h.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                .assetsSummary(summary)
                .assetsJson(h.getAssetsJson())
                .seedMoney(h.getSeedMoney())
                .periodMonths(h.getPeriodMonths())
                .totalReturn(h.getTotalReturn())
                .finalBalance(h.getFinalBalance())
                .build();
    }

    private AllocationDto.ChartPoint newDto(String rawDate, long value) {
        String formatted = rawDate.substring(0, 4) + "-" + rawDate.substring(4, 6) + "-" + rawDate.substring(6, 8);
        AllocationDto.ChartPoint p = new AllocationDto.ChartPoint();
        p.setDate(formatted);
        p.setValue(value);
        return p;
    }

    private StockCandle findCandleByDate(List<StockCandle> list, String date) {
        for (StockCandle c : list) {
            if (c.getDate().equals(date)) return c;
        }
        return null;
    }

    private String findCommonStartDate(Map<String, List<StockCandle>> map) {
        String maxStartDate = "00000000";
        for (List<StockCandle> list : map.values()) {
            if (list.isEmpty()) return null;
            String start = list.get(0).getDate();
            if (start.compareTo(maxStartDate) > 0) maxStartDate = start;
        }
        return maxStartDate;
    }
}
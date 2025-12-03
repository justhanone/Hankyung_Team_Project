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
        
        // 1. 포트폴리오 분석 (기존 로직)
        AnalysisResult pfResult = calculatePortfolio(req.getAssets(), req.getSeedMoney(), req.getPeriodMonths());
        
        // 2. 벤치마크 분석 (신규 로직)
        AnalysisResult bmResult = null;
        if (req.getBenchmarkCode() != null && !req.getBenchmarkCode().isEmpty()) {
            // 벤치마크는 비중 100%짜리 단일 종목 포트폴리오처럼 취급
            List<AllocationDto.Asset> bmAssets = List.of(createAsset(req.getBenchmarkCode(), 100));
            try {
                bmResult = calculatePortfolio(bmAssets, req.getSeedMoney(), req.getPeriodMonths());
            } catch (Exception e) {
                System.err.println("벤치마크 분석 실패: " + e.getMessage());
            }
        }

        // 3. 응답 생성
        AllocationDto.Response res = new AllocationDto.Response();
        
        // Portfolio 결과 매핑
        res.setFinalBalance(pfResult.finalBalance);
        res.setTotalReturn(pfResult.totalReturn);
        res.setCagr(pfResult.cagr);
        res.setMdd(pfResult.mdd);
        res.setVolatility(pfResult.volatility);
        res.setSharpeRatio(pfResult.sharpeRatio);
        res.setEquityCurve(pfResult.curve);

        // Benchmark 결과 매핑
        if (bmResult != null) {
            res.setBmFinalBalance(bmResult.finalBalance);
            res.setBmTotalReturn(bmResult.totalReturn);
            res.setBmCagr(bmResult.cagr);
            res.setBmMdd(bmResult.mdd);
            res.setBmVolatility(bmResult.volatility);
            res.setBmSharpeRatio(bmResult.sharpeRatio);
            res.setBmEquityCurve(bmResult.curve);
        }

        // 4. 이력 저장 (로그인 시)
        if (username != null && !username.equals("anonymousUser")) {
            saveHistory(req, res, username);
        }
        
        return res;
    }

    // --- 내부 분석 로직 (재사용 가능하게 분리) ---
    
    private AnalysisResult calculatePortfolio(List<AllocationDto.Asset> assets, long seedMoney, int periodMonths) {
        // 1. 데이터 수집
        Map<String, List<StockCandle>> dataMap = new HashMap<>();
        int daysNeeded = periodMonths * 30 + 60;

        for (AllocationDto.Asset asset : assets) {
            List<StockCandle> candles = candleRepository.findRecentCandles(asset.getCode(), daysNeeded);
            if (candles == null || candles.isEmpty()) throw new RuntimeException("데이터 부족: " + asset.getCode());
            Collections.reverse(candles);
            dataMap.put(asset.getCode(), candles);
        }

        // 2. 공통 시작일
        String startDate = findCommonStartDate(dataMap);
        if (startDate == null) throw new RuntimeException("기간 불일치");

        // 3. 시뮬레이션
        long currentTotal = seedMoney;
        double peak = currentTotal;
        double maxDrawdown = 0.0;
        List<AllocationDto.ChartPoint> curve = new ArrayList<>();
        Map<String, Double> shares = new HashMap<>();

        // 초기 매수
        for (AllocationDto.Asset asset : assets) {
            StockCandle first = findCandleByDate(dataMap.get(asset.getCode()), startDate);
            if (first != null) {
                double allocated = seedMoney * (asset.getWeight() / 100.0);
                shares.put(asset.getCode(), allocated / first.getClose());
            }
        }

        // 일별 루프
        List<Double> dailyReturns = new ArrayList<>();
        long prevTotal = currentTotal;
        
        List<String> allDates = dataMap.values().iterator().next().stream()
                .map(StockCandle::getDate)
                .filter(d -> d.compareTo(startDate) >= 0)
                .collect(Collectors.toList());

        for (String date : allDates) {
            long dailyValue = 0;
            boolean complete = true;
            
            for (AllocationDto.Asset asset : assets) {
                StockCandle c = findCandleByDate(dataMap.get(asset.getCode()), date);
                if (c != null) dailyValue += (long)(shares.get(asset.getCode()) * c.getClose());
                else complete = false;
            }

            if (complete && dailyValue > 0) {
                if (dailyValue > peak) peak = dailyValue;
                double dd = (peak - dailyValue) / peak * 100;
                if (dd > maxDrawdown) maxDrawdown = dd;

                curve.add(newDto(date, dailyValue));

                if (prevTotal > 0 && !date.equals(startDate)) {
                    dailyReturns.add((double)(dailyValue - prevTotal) / prevTotal);
                }
                prevTotal = dailyValue;
                currentTotal = dailyValue;
            }
        }

        // 지표 계산
        AnalysisResult result = new AnalysisResult();
        result.finalBalance = currentTotal;
        result.mdd = Math.round(maxDrawdown * 100) / 100.0;
        result.curve = curve;

        double totalReturn = ((double)(currentTotal - seedMoney) / seedMoney) * 100;
        result.totalReturn = Math.round(totalReturn * 100) / 100.0;

        double years = periodMonths / 12.0;
        if(years < 1) years = 1;
        double cagr = (Math.pow((double)currentTotal / seedMoney, 1.0 / years) - 1) * 100;
        result.cagr = Math.round(cagr * 100) / 100.0;

        if (!dailyReturns.isEmpty()) {
            double mean = dailyReturns.stream().mapToDouble(d->d).average().orElse(0.0);
            double variance = dailyReturns.stream().mapToDouble(d->Math.pow(d-mean, 2)).sum() / dailyReturns.size();
            double vol = Math.sqrt(variance) * Math.sqrt(252) * 100;
            result.volatility = Math.round(vol * 100) / 100.0;
            
            if (vol > 0) {
                double sharpe = (cagr - 3.0) / vol; // RiskFree 3%
                result.sharpeRatio = Math.round(sharpe * 100) / 100.0;
            }
        }
        
        return result;
    }

    // 내부 결과 클래스
    private static class AnalysisResult {
        long finalBalance;
        double totalReturn;
        double cagr;
        double mdd;
        double volatility;
        double sharpeRatio;
        List<AllocationDto.ChartPoint> curve;
    }

    // --- Helpers ---
    private AllocationDto.Asset createAsset(String code, int weight) {
        AllocationDto.Asset a = new AllocationDto.Asset();
        a.setCode(code); a.setWeight(weight);
        return a;
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
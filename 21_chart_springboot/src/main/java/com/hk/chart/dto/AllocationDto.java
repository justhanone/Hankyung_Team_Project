package com.hk.chart.dto;

import java.util.List;

import lombok.Builder;
import lombok.Data;

public class AllocationDto {

    @Data
    public static class Request {
        private long seedMoney;
        private int periodMonths;
        private List<Asset> assets;
        private boolean rebalance;
        private String benchmarkCode;
    }

    @Data
    public static class Asset {
        private String code;
        private String name;
        private int weight;
    }

    @Data
    public static class Response {
        // Portfolio Metrics
        private double totalReturn;
        private double cagr;
        private long finalBalance;
        private double mdd;
        private double volatility;
        private double sharpeRatio;
        
        // Benchmark Metrics
        private double bmTotalReturn;
        private double bmCagr;
        private long bmFinalBalance;
        private double bmMdd;
        private double bmVolatility;
        private double bmSharpeRatio;

        // Charts
        private List<ChartPoint> equityCurve;    // 포트폴리오 곡선
        private List<ChartPoint> bmEquityCurve;  // 벤치마크 곡선
        
        private List<AssetResult> assetReturns;
    }
    
    @Data
    @Builder
    public static class HistoryResponse {
        private Long id;
        private String date;          // 실행 날짜
        private String assetsSummary; // 자산 요약
        private String assetsJson;    // 복원용 원본 JSON
        private long seedMoney;
        private int periodMonths;
        private double totalReturn;
        private long finalBalance;
    }

    @Data
    public static class ChartPoint {
        private String date;
        private long value;
    }
    
    @Data
    public static class AssetResult {
        private String name;
        private double totalReturn;
    }
}
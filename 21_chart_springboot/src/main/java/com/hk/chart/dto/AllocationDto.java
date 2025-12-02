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
    }

    @Data
    public static class Asset {
        private String code;
        private String name;
        private int weight;
    }

    @Data
    public static class Response {
        // Return (수익)
        private double totalReturn;  // 총 수익률
        private double cagr;         // 연평균 수익률 (Annualized Return)
        private long finalBalance;   // 최종 자산

        // Risk (위험)
        private double mdd;          // 최대 낙폭 (Max Drawdown)
        private double volatility;   // 연간 변동성 (Standard Deviation) 
        private double sharpeRatio;  // 샤프 지수 (위험 대비 수익) 
        
        private List<ChartPoint> equityCurve;
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
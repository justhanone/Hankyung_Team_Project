package com.hk.chart.dto;

import lombok.Data;
import java.util.List;

@Data
public class AllocationReqDto {
    private Double seedMoney;
    private Integer periodMonths;
    private String benchmarkCode;
    private List<AssetDto> assets;

    @Data
    public static class AssetDto {
        private String code;
        private String name;
        private Double weight;
    }
}
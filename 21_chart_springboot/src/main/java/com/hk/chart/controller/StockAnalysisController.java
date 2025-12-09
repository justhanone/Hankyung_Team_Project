package com.hk.chart.controller;


import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hk.chart.service.StockAnalysisService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class StockAnalysisController {

    private final StockAnalysisService stockAnalysisService;

    // 사용법: http://localhost:8080/api/analyze?code=005930
    @GetMapping("/api/analyze")
    public ResponseEntity<?> analyzeStock(@RequestParam String code) {
        
        List<Double> predictions = stockAnalysisService.getPredictedPrices(code);
        
        if (predictions.isEmpty()) {
            return ResponseEntity.badRequest().body("분석 실패");
        }

        // JSON 형태로 반환 (예: [70000, 71000, 70500...])
        return ResponseEntity.ok(predictions);
    }
}
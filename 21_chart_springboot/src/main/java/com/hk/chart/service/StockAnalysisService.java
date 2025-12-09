package com.hk.chart.service;


import java.util.List;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.hk.chart.entity.StockCandle;
import com.hk.chart.repository.StockCandleRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StockAnalysisService {

    private final StockCandleRepository stockCandleRepository;
    
    // 파이썬 FastAPI 서버 주소 (로컬 기준)
    private final String AI_SERVER_URL = "http://localhost:8000/predict";

    /**
     * 특정 종목 코드를 받아 AI 서버에 예측을 요청하고, 예상 종가를 반환함
     */
    public List<Double> getPredictedPrices(String stockCode) {
        
        // 1. DB에서 해당 종목의 전체 데이터 조회 (날짜 오름차순 필수)
        List<StockCandle> candleData = stockCandleRepository.findByStockCodeOrderByDateAsc(stockCode);
        
        // 데이터가 너무 적으면 분석 불가 (예: 20일 미만)
        if (candleData.size() < 20) {
            throw new RuntimeException("데이터가 부족하여 분석할 수 없습니다.");
        }

        // 2. 통신 준비 (RestTemplate)
        RestTemplate restTemplate = new RestTemplate();

        // 3. 헤더 설정 (우리는 JSON 데이터를 보낸다고 명시)
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 4. 요청 바디 생성 (List<StockCandle>을 자동으로 JSON 리스트로 변환해줌)
        HttpEntity<List<StockCandle>> requestEntity = new HttpEntity<>(candleData, headers);

        try {
            Map<String, Object> response = restTemplate.postForObject(AI_SERVER_URL, requestEntity, Map.class);

            if (response != null && response.containsKey("predicted_prices")) {
                // JSON 리스트를 Java 리스트로 변환
                return (List<Double>) response.get("predicted_prices");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return List.of(); // 실패 시 빈 리스트 반환
    }
}
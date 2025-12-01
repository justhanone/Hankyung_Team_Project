package com.hk.chart.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hk.chart.config.KisApiConfig;
import com.hk.chart.dto.IndexInfoDto;
import com.hk.chart.dto.RankingDto;
import com.hk.chart.dto.response.DailyPriceDataDto;
import com.hk.chart.dto.response.KisDailyPriceResponse;
import com.hk.chart.entity.StockCandle;
import com.hk.chart.entity.StockRanking;
import com.hk.chart.repository.StockCandleRepository;
import com.hk.chart.repository.StockRankingRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class KisMarketService {

	private final KisApiConfig kisApiConfig;
    private final WebClient kisWebClient;
    private final KisAuthService authService; // 토큰 관리를 위해 주입
    private final StockCandleRepository candleRepository;
    private final StockRankingRepository rankingRepository;
    
    private static final String TR_ID_PERIOD_PRICE = "FHKST03010100";

    // 거래 ID (Daily Price Inquiry): FHKST01010400
    private static final String TR_ID_DAILY_PRICE = "FHKST01010400";
    private static final String TR_ID_INDEX_PRICE = "FHKUP03500100";

    /**
     * 특정 종목의 과거 일봉 데이터를 조회합니다.
     * @param stockCode 조회할 종목 코드 (예: "005930")
     * @param period 기간 구분 (D:일봉, W:주봉, M:월봉)
     * @return 일봉 데이터 리스트
     */
    public List<DailyPriceDataDto> getDailyPriceData(String stockCode, String period) {
        
        // 1. 접근 토큰 획득
        String accessToken = authService.getAccessToken();

        // 2. WebClient를 사용한 GET 요청 실행
        KisDailyPriceResponse response = kisWebClient.get()
                .uri(uriBuilder -> uriBuilder
                    // API 경로 설정
                    .path("/uapi/domestic-stock/v1/quotations/inquire-daily-price")
                    // 쿼리 파라미터 설정
                    .queryParam("FID_COND_MRKT_DIV_CODE", "J") // 조건 시장 구분 코드 (J: 주식)
                    .queryParam("FID_INPUT_ISCD", stockCode)    // 입력 종목 코드
                    .queryParam("FID_PERIOD_DIV_CODE", period)  // 기간 구분 코드 (D, W, M)
                    .queryParam("FID_ORG_ADJ_PRC", "1")         // 수정주가 반영 여부 (1: 반영)
                    .build())
                // 3. 헤더 설정 (토큰, App Key/Secret은 WebClientConfig에서 설정했으나, 토큰은 여기서 수동 추가)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("appkey", kisApiConfig.getAppKey()) // WebClientConfig에서 Default로 설정하지 않았다면 여기서 추가
                .header("appsecret", kisApiConfig.getAppSecret())
                .header("tr_id", TR_ID_DAILY_PRICE)
                .retrieve()
                .bodyToMono(KisDailyPriceResponse.class)
                .block();

        if (response != null && "0".equals(response.getRt_cd())) {
            return response.getOutput();
        } else if (response != null) {
            System.err.println("KIS API Error: " + response.getMsg1());
            throw new RuntimeException("주가 데이터 조회 실패: " + response.getMsg1());
        }
        return List.of();
    }
    
    /**
     * 특정 기간(시작일~종료일)의 주가 데이터를 조회합니다.
     * API: 국내주식 기간별 시세 조회 (inquire-daily-itemchartprice)
     * * @param stockCode 종목코드 (005930)
     * @param startDate 시작일 (YYYYMMDD)
     * @param endDate   종료일 (YYYYMMDD)
     * @return 일봉 데이터 리스트
     */
    public List<DailyPriceDataDto> getDailyPriceDataWithDate(String stockCode, String startDate, String endDate) {
        
        String accessToken = authService.getAccessToken();

        KisDailyPriceResponse response = kisWebClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice") // ⭐️ 경로 변경됨
                    .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                    .queryParam("FID_INPUT_ISCD", stockCode)
                    .queryParam("FID_INPUT_DATE_1", startDate) // ⭐️ 시작일 파라미터
                    .queryParam("FID_INPUT_DATE_2", endDate)   // ⭐️ 종료일 파라미터
                    .queryParam("FID_PERIOD_DIV_CODE", "D")    // 기간 (D:일봉)
                    .queryParam("FID_ORG_ADJ_PRC", "1")        // 수정주가 반영
                    .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("appkey", kisApiConfig.getAppKey())
                .header("appsecret", kisApiConfig.getAppSecret())
                .header("tr_id", TR_ID_PERIOD_PRICE) // ⭐️ TR_ID 변경됨 (FHKST03010100)
                .retrieve()
                .bodyToMono(KisDailyPriceResponse.class)
                .block();

        if (response != null && "0".equals(response.getRt_cd())) {
            // 기간별 조회 API는 결과가 'output2'에 담겨 옵니다.
            return response.getOutput2() != null ? response.getOutput2() : List.of();
        } else if (response != null) {
            System.err.println("KIS API Error (Period): " + response.getMsg1());
            // 에러 발생 시 빈 리스트 반환하거나 예외 던짐
            return List.of();
        }
        return List.of();
    }
    
    /**
     * 주기(일/주/월)에 따라 캔들 데이터를 조회하거나 가공해서 반환합니다.
     */
    public List<StockCandle> getCandleDataByPeriod(String stockCode, String period, int limit) {
        // 1. 일봉은 DB에서 바로 가져옴 (최근 데이터 넉넉하게)
        // 주봉/월봉 계산을 위해 일봉 데이터를 충분히 많이 가져옵니다 (limit * 5 ~ 30)
        int fetchLimit = period.equals("D") ? limit : limit * 30;
        
        List<StockCandle> dailies = candleRepository.findRecentCandles(stockCode, fetchLimit);
        // 날짜 오름차순(과거->현재) 정렬
        Collections.reverse(dailies);

        if (period.equals("D")) {
            // 일봉은 이미 MA가 계산되어 있으므로 그대로 반환 (최근 N개만 자름)
            int size = dailies.size();
            return size > limit ? dailies.subList(size - limit, size) : dailies;
        }

        // 2. 주봉/월봉으로 데이터 합치기 (Resampling)
        List<StockCandle> aggregated = aggregateCandles(dailies, period);

        // 3. 이동평균선(MA) 실시간 재계산 (주/월봉용)
        calculateDynamicMA(aggregated);

        // 4. 요청한 개수만큼 자르기
        int size = aggregated.size();
        return size > limit ? aggregated.subList(size - limit, size) : aggregated;
    }

    // 캔들 병합 로직 (핵심)
    private List<StockCandle> aggregateCandles(List<StockCandle> dailies, String period) {
        List<StockCandle> result = new ArrayList<>();
        if (dailies.isEmpty()) return result;

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        StockCandle currentCandle = null;
        String currentPeriodKey = ""; // 주차(Week) 또는 월(Month) 식별자

        for (StockCandle day : dailies) {
            LocalDate date = LocalDate.parse(day.getDate(), formatter);
            
            // 그룹 키 생성 (주봉: YYYY-wWW, 월봉: YYYY-MM)
            String key;
            if (period.equals("W")) {
                int weekOfYear = date.get(WeekFields.of(Locale.getDefault()).weekOfWeekBasedYear());
                key = date.getYear() + "-w" + weekOfYear;
            } else { // "M"
                key = date.getYear() + "-" + date.getMonthValue();
            }

            if (!key.equals(currentPeriodKey)) {
                // 새로운 주기 시작 -> 이전 캔들 저장
                if (currentCandle != null) result.add(currentCandle);
                
                // 새 캔들 초기화
                currentCandle = StockCandle.builder()
                        .stockCode(day.getStockCode())
                        .date(day.getDate()) // 시작일 (나중에 종료일로 갱신)
                        .open(day.getOpen())
                        .high(day.getHigh())
                        .low(day.getLow())
                        .close(day.getClose())
                        .volume(day.getVolume())
                        .build();
                currentPeriodKey = key;
            } else {
                // 기존 주기에 데이터 병합 (고가, 저가, 종가, 거래량 갱신)
                if (currentCandle != null) {
                    currentCandle = StockCandle.builder()
                            .stockCode(currentCandle.getStockCode())
                            .date(day.getDate()) // 날짜는 해당 주기의 마지막 날짜로 계속 업데이트
                            .open(currentCandle.getOpen()) // 시가는 유지
                            .high(Math.max(currentCandle.getHigh(), day.getHigh()))
                            .low(Math.min(currentCandle.getLow(), day.getLow()))
                            .close(day.getClose()) // 종가는 최신값
                            .volume(currentCandle.getVolume() + day.getVolume()) // 거래량 누적
                            .build();
                }
            }
        }
        // 마지막 캔들 추가
        if (currentCandle != null) result.add(currentCandle);
        
        return result;
    }

    // 동적 MA 계산 (주봉/월봉용)
    private void calculateDynamicMA(List<StockCandle> candles) {
        for (int i = 0; i < candles.size(); i++) {
            Integer ma5 = calcAvg(candles, i, 5);
            Integer ma20 = calcAvg(candles, i, 20);
            Integer ma60 = calcAvg(candles, i, 60);
            candles.get(i).setMa(ma5, ma20, ma60);
        }
    }

    private Integer calcAvg(List<StockCandle> list, int idx, int period) {
        if (idx < period - 1) return null;
        long sum = 0;
        for (int j = 0; j < period; j++) sum += list.get(idx - j).getClose();
        return (int) (sum / period);
    }
    
    @Transactional
    public void collectAllHistory(String stockCode) {
        // 1. 수집 종료 기준일 (상장일 이전 등 충분히 과거)
        String targetStartDate = "19900101"; 
        
        // 2. 수집 시작일 (오늘부터 과거로 감)
        LocalDate currentEndDate = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");

        System.out.println(">>> [" + stockCode + "] 과거 데이터 수집 시작...");

        boolean isRunning = true;
        while (isRunning) {
            try {
                String endDateStr = currentEndDate.format(formatter);
                // ⭐️ [핵심] 한 번에 100일 치만 요청 (API 제한 고려)
                String startDateStr = currentEndDate.minusDays(100).format(formatter);

                // 수집 목표 날짜보다 더 과거로 가면 종료
                if (startDateStr.compareTo(targetStartDate) < 0) {
                    System.out.println(">>> 목표 날짜(" + targetStartDate + ") 도달. 수집 종료.");
                    break;
                }

                System.out.println(">>> 요청 기간: " + startDateStr + " ~ " + endDateStr);

                // 3. API 호출
                List<DailyPriceDataDto> dataList = getDailyPriceDataWithDate(stockCode, startDateStr, endDateStr);

                if (dataList == null || dataList.isEmpty()) {
                    System.out.println(">>> 데이터 없음 (휴장일 또는 상장 이전). 계속 진행...");
                    // 데이터가 없어도 날짜는 과거로 이동해야 함
                    currentEndDate = currentEndDate.minusDays(100);
                    continue;
                }

                // 4. DB 저장
                int saveCount = 0;
                for (DailyPriceDataDto dto : dataList) {
                    // 날짜 포맷 등 유효성 체크
                    if (dto.getStck_bsop_date() == null) continue;

                    // 중복 확인 후 저장
                    if (!candleRepository.existsByStockCodeAndDate(stockCode, dto.getStck_bsop_date())) {
                        StockCandle candle = StockCandle.builder()
                                .stockCode(stockCode)
                                .date(dto.getStck_bsop_date())
                                .open(parseSafeInt(dto.getStck_oprc()))
                                .high(parseSafeInt(dto.getStck_hgpr()))
                                .low(parseSafeInt(dto.getStck_lwpr()))
                                .close(parseSafeInt(dto.getStck_clpr()))
                                .volume(parseSafeLong(dto.getAcc_trd_qty())) 
                                .build();
                        candleRepository.save(candle);
                        saveCount++;
                    }
                }
                
                System.out.println(">>> " + saveCount + "건 저장 완료.");

                // 5. 다음 요청 준비: 이번 요청의 시작일 하루 전날을 다음 요청의 종료일로 설정
                // (API가 100일치를 다 안 줄수도 있으므로, 단순히 날짜 계산으로 이동)
                currentEndDate = currentEndDate.minusDays(101);

                // 6. ⭐️ API 호출 제한 방지 (필수)
                Thread.sleep(300); // 0.3초 대기

            } catch (Exception e) {
                System.err.println(">>> 수집 중 에러 발생: " + e.getMessage());
                e.printStackTrace();
                isRunning = false; // 에러 시 루프 중단
            }
        }
        calculateMovingAverage(stockCode);
        System.out.println(">>> [" + stockCode + "] 수집 프로세스 종료.");
    }
    
    /**
     * 특정 종목의 데이터를 최신 상태로 업데이트 (스케줄러용)
     */
    @Transactional
    public void updateStockData(String stockCode) {
        // 1. DB에서 가장 마지막 저장된 날짜 가져오기
        String lastDate = candleRepository.findLatestDate(stockCode);
        
        if (lastDate == null) {
            // 데이터가 아예 없으면 전체 수집 실행
            collectAllHistory(stockCode);
            return;
        }

        // 2. 수집할 기간 설정 (마지막 저장일 다음날 ~ 오늘)
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        LocalDate startDate = LocalDate.parse(lastDate, formatter).plusDays(1); // 다음날
        LocalDate today = LocalDate.now();

        // 이미 최신 데이터라면 종료
        if (startDate.isAfter(today)) {
            System.out.println(">>> [" + stockCode + "] 이미 최신 데이터입니다.");
            return;
        }

        String strStart = startDate.format(formatter);
        String strEnd = today.format(formatter);

        System.out.println(">>> [" + stockCode + "] 업데이트 시작 (" + strStart + " ~ " + strEnd + ")");

        // 3. API 호출 (기간별 조회)
        try {
            List<DailyPriceDataDto> dataList = getDailyPriceDataWithDate(stockCode, strStart, strEnd);
            
            if (dataList == null || dataList.isEmpty()) {
                System.out.println(">>> [" + stockCode + "] 신규 데이터 없음.");
                return;
            }

            // 4. DB 저장
            int count = 0;
            for (DailyPriceDataDto dto : dataList) {
                if (!candleRepository.existsByStockCodeAndDate(stockCode, dto.getStck_bsop_date())) {
                    StockCandle candle = StockCandle.builder()
                            .stockCode(stockCode)
                            .date(dto.getStck_bsop_date())
                            .open(parseSafeInt(dto.getStck_oprc()))
                            .high(parseSafeInt(dto.getStck_hgpr()))
                            .low(parseSafeInt(dto.getStck_lwpr()))
                            .close(parseSafeInt(dto.getStck_clpr()))
                            .volume(parseSafeLong(dto.getAcc_trd_qty()))
                            .build();
                    candleRepository.save(candle);
                    count++;
                }
            }
            calculateMovingAverage(stockCode);
            System.out.println(">>> [" + stockCode + "] " + count + "건 업데이트 완료.");
            
        } catch (Exception e) {
            System.err.println(">>> [" + stockCode + "] 업데이트 중 오류: " + e.getMessage());
        }
    }
    
    /**
     * 특정 종목의 모든 데이터에 대해 이동평균선(MA)을 계산하고 DB에 업데이트합니다.
     */
    @Transactional
    public void calculateMovingAverage(String stockCode) {
        // 1. 전체 데이터를 날짜 오름차순(과거->현재)으로 조회
        List<StockCandle> candles = candleRepository.findAllByStockCodeOrderByDateAsc(stockCode);
        
        if (candles.isEmpty()) return;

        System.out.println(">>> [" + stockCode + "] 이동평균선 계산 시작 (" + candles.size() + "건)...");

        // 2. 반복문을 돌며 MA 계산
        for (int i = 0; i < candles.size(); i++) {
            Integer ma5 = calculateAvg(candles, i, 5);
            Integer ma20 = calculateAvg(candles, i, 20);
            Integer ma60 = calculateAvg(candles, i, 60);

            // Entity에 값 설정 (JPA의 Dirty Checking으로 인해 save 호출 없어도 트랜잭션 끝나면 자동 업데이트됨)
            candles.get(i).setMa(ma5, ma20, ma60);
        }
        
        System.out.println(">>> [" + stockCode + "] 이동평균선 계산 완료.");
    }
    
    // 네이버환율 API
    public Double getExchangeRate() {
        // 1. 네이버 API 시도
        try {
            System.out.println(">>> [환율] Naver API 요청 시작...");
            String url = "https://m.search.naver.com/p/csearch/content/qapirender.nhn?key=calculator&pkid=141&q=%ED%99%98%EC%9C%A8&where=m&u1=keb&u6=standardUnit&u7=0&u3=USD&u4=KRW&u8=down&u2=1";
            
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    // ⭐️ 네이버는 브라우저 정보(User-Agent)가 없으면 차단할 수 있음
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Object> map = mapper.readValue(response.body(), Map.class);
                
                // 네이버 응답 구조: { "country": [ { "value": "1,395.50", ... }, { ... } ] }
                if (map.containsKey("country")) {
                    List<Map<String, Object>> countryList = (List<Map<String, Object>>) map.get("country");
                    
                    // 리스트의 두 번째 요소(index 1)가 보통 KRW 정보임
                    if (countryList.size() >= 2) {
                        Object valueObj = countryList.get(1).get("value");
                        if (valueObj != null) {
                            // "1,395.50" 처럼 콤마가 포함된 문자열을 숫자로 변환
                            String valueStr = valueObj.toString().replace(",", "");
                            Double rate = Double.parseDouble(valueStr);
                            System.out.println(">>> [환율] Naver 파싱 성공: " + rate);
                            return rate;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println(">>> [환율] Naver API 실패: " + e.getMessage());
        }

        // 2. 실패 시 백업 (ExchangeRate-API) 시도
        System.out.println(">>> [환율] 백업 API 시도...");
        return fetchExchangeRate("https://open.er-api.com/v6/latest/USD", "rates", "KRW");
    }

    // 백업용 API 호출 헬퍼 메서드
    private Double fetchExchangeRate(String url, String outerKey, String innerKey) {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Object> map = mapper.readValue(response.body(), Map.class);
                
                if (map.containsKey(outerKey)) {
                    Map<String, Object> rates = (Map<String, Object>) map.get(outerKey);
                    if (rates.containsKey(innerKey)) {
                        return ((Number) rates.get(innerKey)).doubleValue();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println(">>> [환율] 백업 API 에러: " + e.getMessage());
        }
        return null;
    }

    // 평균 계산 헬퍼 메서드
    private Integer calculateAvg(List<StockCandle> list, int currentIndex, int period) {
        if (currentIndex < period - 1) {
            return null; // 데이터 부족 시 null (그리지 않음)
        }

        long sum = 0;
        for (int j = 0; j < period; j++) {
            sum += list.get(currentIndex - j).getClose();
        }
        return (int) (sum / period);
    }
    
    private int parseSafeInt(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    private long parseSafeLong(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

         //국내업종 현재지수 조회
         // - 장 운영 중: 실시간 지수 제공
         // - 장 마감 후: 종가 지수 제공 (FHKUP03500100 대신 FHPUP02100000 사용)
        public IndexInfoDto getIndexInfo(String indexCode) {
            String accessToken = authService.getAccessToken();

            try {
                // 1. API 호출
                // URL: /uapi/domestic-stock/v1/quotations/inquire-index-price
                // TR_ID: FHPUP02100000 (장 마감 후에도 데이터 나옴)
                String responseBody = kisWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/inquire-index-price")
                        .queryParam("FID_COND_MRKT_DIV_CODE", "U") // U: 업종
                        .queryParam("FID_INPUT_ISCD", indexCode)    // 0001(코스피), 1001(코스닥)
                        .build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header("appkey", kisApiConfig.getAppKey())
                    .header("appsecret", kisApiConfig.getAppSecret())
                    .header("tr_id", "FHPUP02100000") // ⭐️ 중요: 지수 전용 TR
                    .header("custtype", "P")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

                // 2. 결과 파싱
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Object> responseMap = mapper.readValue(responseBody, Map.class);

                // 3. 응답 검증 ('rt_cd'가 0이면 성공)
                if (responseMap != null && "0".equals(responseMap.get("rt_cd"))) {
                    Map<String, String> output = (Map<String, String>) responseMap.get("output");
                    
                    if (output != null) {
                        // ⭐️ 중요: API마다 필드명이 다름 (FHPUP02100000 기준 필드명 매핑)
                        return IndexInfoDto.builder()
                                .price(output.get("bstp_nmix_prpr"))  // 현재지수
                                .sign(output.get("prdy_vrss_sign"))   // 대비 부호
                                .change(output.get("bstp_nmix_prdy_vrss"))      // 전일 대비
                                .rate(output.get("bstp_nmix_prdy_ctrt"))        // 등락률
                                .build();
                    }
                } else {
                    // 에러 발생 시 로그 출력 (디버깅용)
                    if (responseMap != null) {
                        System.err.println(">>> [Index Error] Code: " + indexCode + ", Msg: " + responseMap.get("msg1"));
                    }
                }

            } catch (Exception e) {
                System.err.println(">>> [Index Exception] " + e.getMessage());
            }

            // 4. 실패 시 기본값 반환 (화면 깨짐 방지)
            return IndexInfoDto.builder()
                    .price("-")
                    .sign("3")
                    .change("0.00")
                    .rate("0.00")
                    .build();
        }
    
    /**
     * ✅ [스케줄러] 매 15분마다 실행 (0분, 15분, 30분, 45분)
     * Cron 표현식: 초 분 시 일 월 요일
     */
    @Scheduled(cron = "0 0/15 * * * *") 
    @Transactional
    public void scheduleRankingUpdate() {
        log.info("⏰ [Scheduler] 실시간 랭킹 업데이트 시작...");
        
        // 1. API 호출 (기존 로직 활용하거나 내부 로직 복사)
        List<RankingDto> apiResult = fetchRisingRankingFromApi();

        if (apiResult == null || apiResult.isEmpty()) {
            log.warn("⚠️ [Scheduler] API 데이터가 비어있어 DB를 갱신하지 않습니다.");
            return;
        }

        // 2. 기존 DB 데이터 싹 비우기 (최신 30개만 유지하기 위해)
        rankingRepository.deleteAll();

        // 3. Entity로 변환하여 저장
        List<StockRanking> entities = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (RankingDto dto : apiResult) {
            entities.add(StockRanking.builder()
                    .ranking(Integer.parseInt(dto.getRank()))
                    .stockCode(dto.getCode())
                    .stockName(dto.getName())
                    .price(dto.getPrice())
                    .changePrice(dto.getChange())
                    .changeRate(dto.getRate())
                    .updatedAt(now)
                    .build());
        }

        rankingRepository.saveAll(entities);
        log.info("✅ [Scheduler] DB 랭킹 업데이트 완료 ({}건)", entities.size());
    }

    /**
     * ✅ [Front용] DB에 저장된 랭킹 데이터 조회
     */
    public List<RankingDto> getCachedRankingFromDB() {
        // DB에서 가져오기
        List<StockRanking> entities = rankingRepository.findAllByOrderByRankingAsc();
        
        // 데이터가 없으면(최초 실행 전 등) 즉시 한번 수동 업데이트
        if (entities.isEmpty()) {
            log.info("DB가 비어있어 즉시 업데이트를 수행합니다.");
            scheduleRankingUpdate();
            entities = rankingRepository.findAllByOrderByRankingAsc();
        }

        // Entity -> DTO 변환해서 반환
        return entities.stream()
                .map(e -> RankingDto.builder()
                        .rank(String.valueOf(e.getRanking()))
                        .code(e.getStockCode())
                        .name(e.getStockName())
                        .price(e.getPrice())
                        .change(e.getChangePrice())
                        .rate(e.getChangeRate())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * (내부용) 실제 API 호출 로직 - 디버깅 강화 버전
     */
    private List<RankingDto> fetchRisingRankingFromApi() {
        String accessToken = authService.getAccessToken();
        
        try {
            // 1. String으로 원본 응답 받기 (에러 내용 확인용)
            String responseBody = kisWebClient.get()
                .uri(uriBuilder -> uriBuilder
                		.path("/uapi/domestic-stock/v1/ranking/fluctuation")
                		.queryParam("FID_RSFL_RATE2", "0")              // 등락률 범위 끝 (공란: 전체)
                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")      // 주식
                        .queryParam("FID_COND_SCR_DIV_CODE", "20170")   // 화면번호
                        .queryParam("FID_INPUT_ISCD", "0000")           // 전체 종목
                        .queryParam("FID_RANK_SORT_CLS_CODE", "0")      // 0:상승순, 1:하락순
                        .queryParam("FID_INPUT_CNT_1", "0")             // 0: 전체(최대)
                        .queryParam("FID_PRC_CLS_CODE", "0")            // 가격구분
                        .queryParam("FID_INPUT_PRICE_1", "0")            // 가격범위 시작 (공란 가능)
                        .queryParam("FID_INPUT_PRICE_2", "0")            // 가격범위 끝 (공란 가능)
                        .queryParam("FID_VOL_CNT", "0")                  // 거래량 (공란 가능)
                        .queryParam("FID_TRGT_CLS_CODE", "0")           // 대상구분 (0:전체)
                        .queryParam("FID_TRGT_EXLS_CLS_CODE", "0")      // 제외구분 (0:전체)️
                        .queryParam("FID_DIV_CLS_CODE","0")
                        .queryParam("FID_RSFL_RATE1", "0")               // 등락률 범위 시작 (공란: 전체)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("appkey", kisApiConfig.getAppKey())
                .header("appsecret", kisApiConfig.getAppSecret())
                .header("tr_id", "FHPST01700000") // 실전투자용 TR ID
                .header("custtype", "P")
                .header("content-type", "application/json")
                .retrieve()
                .bodyToMono(String.class) // ⭐️ Map 대신 String으로 수신
                .block();

            // 2. 로그에 원본 출력
            System.out.println(">>> [Ranking API Raw Body] " + responseBody);

            // 3. 파싱 (ObjectMapper 사용)
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> responseMap = mapper.readValue(responseBody, Map.class);

            if (responseMap != null && "0".equals(responseMap.get("rt_cd"))) {
                List<Map<String, String>> output = (List<Map<String, String>>) responseMap.get("output");
                if (output != null) {
                    List<RankingDto> list = new ArrayList<>();
                    for (Map<String, String> item : output) {
                        list.add(RankingDto.builder()
                            .rank(item.get("data_rank"))
                            .code(item.get("stck_shrn_iscd"))
                            .name(item.get("hts_kor_isnm"))
                            .price(item.get("stck_prpr"))
                            .change(item.get("prdy_vrss"))
                            .rate(item.get("prdy_ctrt"))
                            .build());
                    }
                    return list;
                }
            } else {
                if (responseMap != null) {
                    System.err.println(">>> [Ranking API Error] Code: " + responseMap.get("rt_cd") + ", Msg: " + responseMap.get("msg1"));
                }
            }

        } catch (Exception e) {
            System.err.println(">>> [Ranking API Exception] " + e.getMessage());
            e.printStackTrace();
        }
        
        return Collections.emptyList();
    }

    /**
     * 단일 종목 현재가 조회 (외부 서비스 호출용)
     */
    public RankingDto fetchCurrentPrice(String stockCode) {
        String accessToken = authService.getAccessToken();
        try {
            // WebClient 요청 (예외처리 포함)
            String responseBody = kisWebClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/uapi/domestic-stock/v1/quotations/inquire-price")
                    .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                    .queryParam("FID_INPUT_ISCD", stockCode)
                    .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("appkey", kisApiConfig.getAppKey())
                .header("appsecret", kisApiConfig.getAppSecret())
                .header("tr_id", "FHKST01010100") // 현재가 TR
                .header("custtype", "P")
                .retrieve()
                .bodyToMono(String.class)
                .block();

            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> responseMap = mapper.readValue(responseBody, Map.class);

            if (responseMap != null && "0".equals(responseMap.get("rt_cd"))) {
                Map<String, String> output = (Map<String, String>) responseMap.get("output");
                if (output != null) {
                    return RankingDto.builder()
                            .code(stockCode)
                            .price(output.get("stck_prpr")) // API는 String 반환
                            .change(output.get("prdy_vrss"))
                            .rate(output.get("prdy_ctrt"))
                            .build();
                }
            }
        } catch (Exception e) {
            // 로그 생략 또는 간단히
        }
        return null; // 실패 시 null
    }
}

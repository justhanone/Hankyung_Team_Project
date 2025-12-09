package com.hk.chart.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.hk.chart.service.KisMarketService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class AdminController {
	
	private final KisMarketService marketService;

	// [관리자] 전 종목 초기화 실행 API
    @GetMapping("/api/admin/init-all")
    @ResponseBody
    public String initAllData(@RequestParam(value = "confirm", required = false) String confirm) {
        // 1. 'confirm=true' 파라미터가 없으면 -> 알림창 띄우는 스크립트 리턴
        if (!"true".equals(confirm)) {
            return "<script>" +
                   "if (confirm('⚠️ 정말로 전 종목 데이터 수집을 시작하시겠습니까?\\n(예상 소요시간: 수 시간)')) {" +
                   "    location.href = '/api/admin/init-all?confirm=true';" + // 확인 시 재요청
                   "} else {" +
                   "    history.back();" + // 취소 시 뒤로가기
                   "}" +
                   "</script>";
        }

        // 2. 'confirm=true'가 있으면 -> 실제 수집 로직 실행
        new Thread(() -> {
            marketService.initAllStocksData();
        }).start();
        
        return "✅ 전 종목 데이터 수집을 백그라운드에서 시작했습니다.<br>서버 로그를 확인해주세요.";
    }
    
    // [관리자] 이동평균선 일괄 재계산 API (데이터 수집 후 MA 비어있을 때 사용)
    @GetMapping("/api/admin/recalc-ma")
    @ResponseBody
    public String recalcMa() {
        new Thread(() -> {
            marketService.recalculateAllMas();
        }).start();
        
        return "전 종목 이동평균선 재계산을 시작했습니다. (로그 확인 요망)";
    }
    
    // [관리자] 일별 데이터 수동 업데이트 (서버 꺼져서 스케줄 놓쳤을 때 사용)
    @GetMapping("/api/admin/update-daily")
    @ResponseBody
    public String manualDailyUpdate() {
    	marketService.updateAllWatchedStocks();

        return "✅ 일별 주가 업데이트를 백그라운드에서 시작했습니다. (로그 확인 요망)";
    }
}

package com.hk.chart.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import com.hk.chart.dto.AllocationDto;
import com.hk.chart.service.AllocationService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class AllocationController {

    private final AllocationService allocationService;

    // 화면 이동
    @GetMapping("/backtest")
    public String page() {
        return "backtest";
    }

    // 분석 실행 (JSON으로 데이터 받음)
    @PostMapping("/api/allocation/run")
    @ResponseBody
    public AllocationDto.Response run(@RequestBody AllocationDto.Request req, Principal principal) {
        // 로그인 안했으면 null 전달 (저장 안함)
        String username = (principal != null) ? principal.getName() : null;
        return allocationService.analyze(req, username);
    }
    
    //과거 실행 이력 조회
    @GetMapping("/api/allocation/history")
    @ResponseBody
    public List<AllocationDto.HistoryResponse> getHistory(Principal principal) {
        if (principal == null) return List.of();
        return allocationService.getHistory(principal.getName());
    }
}
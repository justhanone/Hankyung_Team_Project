package com.hk.chart.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.hk.chart.entity.StockInfo;

public interface StockInfoRepository extends JpaRepository<StockInfo, String> {
    // 종목명 또는 코드로 검색 (SQL의 LIKE %keyword% 기능)
    List<StockInfo> findByNameContainingOrCodeContaining(String name, String code);
    
    // 모든 종목 코드 조회 (String 리스트로 반환)
    @Query("SELECT s.code FROM StockInfo s")
    List<String> findAllCodes();
}
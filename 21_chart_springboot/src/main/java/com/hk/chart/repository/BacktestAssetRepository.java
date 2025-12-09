package com.hk.chart.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hk.chart.entity.BacktestAsset;
import java.util.List;
import java.util.Optional;

public interface BacktestAssetRepository extends JpaRepository<BacktestAsset, Long> {
    List<BacktestAsset> findByUserId(String userId);
    Optional<BacktestAsset> findByUserIdAndStockCode(String userId, String stockCode);
    void deleteByIdAndUserId(Long id, String userId);
}
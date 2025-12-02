package com.hk.chart.repository;

import com.hk.chart.entity.BacktestHistory;
import com.hk.chart.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BacktestHistoryRepository extends JpaRepository<BacktestHistory, Long> {
    // 특정 사용자의 최근 기록 순으로 조회
    List<BacktestHistory> findByUserOrderByCreatedAtDesc(User user);
}
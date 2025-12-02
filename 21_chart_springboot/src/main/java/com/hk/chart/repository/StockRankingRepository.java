package com.hk.chart.repository;

import com.hk.chart.entity.StockRanking;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StockRankingRepository extends JpaRepository<StockRanking, Long> {
    // 순위대로 정렬해서 가져오기
	List<StockRanking> findAllByRankingTypeOrderByRankingAsc(Integer rankingType);
}
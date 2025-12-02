package com.hk.chart.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "stock_ranking")
public class StockRanking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer ranking; // 순위 (1~30)

    @Column(length = 20)
    private String stockCode; // 종목코드

    private String stockName; // 종목명

    private String price;     // 현재가 (문자열로 저장 추천 - 콤마 등 포맷팅 위해)
    private String changePrice; // 전일대비
    private String changeRate;  // 등락률

    private LocalDateTime updatedAt; // 업데이트 시간
    
    private Integer rankingType; // 랭킹 타입 (0: 급상승, 1: 급하락)
}
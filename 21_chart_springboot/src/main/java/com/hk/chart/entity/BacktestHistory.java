package com.hk.chart.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "backtest_history")
public class BacktestHistory {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private String testType;     // "ALLOCATION"
    private long seedMoney;      // 초기금
    private int periodMonths;    // 기간

    @Column(columnDefinition = "TEXT")
    private String assetsJson;   // 자산 구성 (JSON 문자열)

    private long finalBalance;   // 최종금액
    private double totalReturn;  // 수익률
    private double cagr;         // CAGR
    private double mdd;          // MDD

    private LocalDateTime createdAt;
    
    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
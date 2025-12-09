package com.hk.chart.controller;

import com.hk.chart.entity.BacktestAsset;
import com.hk.chart.repository.BacktestAssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/my-assets")
@RequiredArgsConstructor
public class BacktestAssetController {

    private final BacktestAssetRepository repository;

    // 1. 내 자산 리스트 조회
    @GetMapping
    public ResponseEntity<?> getMyAssets(Principal principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(repository.findByUserId(principal.getName()));
    }

    // 2. 자산 추가
    @PostMapping
    public ResponseEntity<?> addAsset(Principal principal, @RequestBody BacktestAsset req) {
        if (principal == null) return ResponseEntity.status(401).build();

        if (repository.findByUserIdAndStockCode(principal.getName(), req.getStockCode()).isPresent()) {
            return ResponseEntity.badRequest().body("이미 추가된 종목입니다.");
        }

        BacktestAsset asset = BacktestAsset.builder()
                .userId(principal.getName())
                .stockCode(req.getStockCode())
                .stockName(req.getStockName())
                .weight(0.0)
                .build();
        
        return ResponseEntity.ok(repository.save(asset));
    }

    // 3. 비중 수정
    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> updateWeight(Principal principal, @PathVariable Long id, @RequestBody Double weight) {
        if (principal == null) return ResponseEntity.status(401).build();

        repository.findById(id).ifPresent(asset -> {
            if (asset.getUserId().equals(principal.getName())) {
                asset.setWeight(weight);
            }
        });
        return ResponseEntity.ok().build();
    }

    // 4. 자산 삭제
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> deleteAsset(Principal principal, @PathVariable Long id) {
        if (principal == null) return ResponseEntity.status(401).build();
        repository.deleteByIdAndUserId(id, principal.getName());
        return ResponseEntity.ok().build();
    }
}
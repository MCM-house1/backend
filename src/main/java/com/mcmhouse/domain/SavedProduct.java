package com.mcmhouse.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 사용자가 저장(하트)한 상품. 세션(resultId) 기준으로 관리한다.
 * 진단 결과와 느슨하게 resultId 컬럼으로만 연결한다(별도 연관관계 없음).
 * 같은 세션에서 같은 상품은 한 번만 저장된다.
 */
@Entity
@Table(
        name = "saved_product",
        uniqueConstraints = @UniqueConstraint(columnNames = {"result_id", "product_id"})
)
public class SavedProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "result_id", nullable = false)
    private Long resultId;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime savedAt = LocalDateTime.now();

    protected SavedProduct() {}

    public SavedProduct(Long resultId, String productId) {
        this.resultId = resultId;
        this.productId = productId;
    }

    public Long getId() { return id; }
    public Long getResultId() { return resultId; }
    public String getProductId() { return productId; }
    public LocalDateTime getSavedAt() { return savedAt; }
}

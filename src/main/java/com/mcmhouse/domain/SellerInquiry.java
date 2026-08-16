package com.mcmhouse.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 셀러 상담 요청 내역. 사용자가 어떤 상품에 상담을 요청했는지 기록만 남긴다.
 * 실제 1:1 채팅/상담 연결은 범위 밖이다.
 * 세션(resultId) 기준으로 느슨하게 연결한다.
 */
@Entity
@Table(name = "seller_inquiry")
public class SellerInquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "result_id", nullable = false)
    private Long resultId;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime requestedAt = LocalDateTime.now();

    protected SellerInquiry() {}

    public SellerInquiry(Long resultId, String productId) {
        this.resultId = resultId;
        this.productId = productId;
    }

    public Long getId() { return id; }
    public Long getResultId() { return resultId; }
    public String getProductId() { return productId; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
}

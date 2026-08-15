package com.mcmhouse.dto;

import com.mcmhouse.domain.Product;

import java.util.List;

/** 상품 상세·추천 이유(COMPLETE THE LOOK) 관련 DTO. */
public final class RecommendationDtos {

    private RecommendationDtos() {}

    /** COMPLETE THE LOOK 항목: 매칭 상품 + AI가 쓴 매칭 이유. */
    public record MatchItem(Product product, String reason) {}

    /** 추천보기_제품정보 화면 응답. */
    public record ProductDetailView(
            String house,
            Product product,
            String reason,                 // "왜 추천됐나요?" — AI 생성 (폴백 시 기본 문구)
            String story,                  // "이 제품이 담은 이야기" — AI 생성(없으면 null)
            boolean fallback,              // true면 LLM 실패로 기본 문구 사용
            List<MatchItem> completeTheLook // 함께 매치할 상품 + 이유
    ) {}
}

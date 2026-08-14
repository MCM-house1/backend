package com.mcmhouse.domain;

/**
 * 상품 1건. products.json에서 로드되는 고정 데이터.
 * image는 프론트가 통일한 파일명 규칙(예: "01_REC1")과 동일하게 맞춘다.
 */
public record Product(
        String id,
        House house,
        String name,
        int price,
        String category,
        String image
) {}

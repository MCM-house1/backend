package com.mcmhouse.domain;

/**
 * 상품 1건. products.json에서 로드되는 고정 데이터.
 * image는 프론트 public 폴더 기준 웹 경로다. 예: "/images/legacy/01_REC1.png"
 * (House 폴더명은 소문자, 확장자는 실제 파일과 동일하게 맞춘다.)
 */
public record Product(
        String id,
        House house,
        String name,
        int price,
        String category,
        String image
) {}

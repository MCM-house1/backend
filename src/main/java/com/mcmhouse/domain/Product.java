package com.mcmhouse.domain;

/**
 * 상품 1건. products.json에서 로드되는 고정 데이터.
 * image는 프론트 public 폴더 기준 웹 경로다. 예: "/images/legacy/01_REC1.png"
 * (House 폴더명은 소문자, 확장자는 실제 파일과 동일하게 맞춘다.)
 * productUrl은 MCM 공식 상품 페이지 주소('제품 보러가기' 버튼용).
 * 상품별 개별 URL을 아직 못 구해 임시로 MCM 홈페이지(https://kr.mcmworldwide.com/ko_KR/home)로
 * 통일해 두었다. 실제 상품 URL을 받으면 products.json 값만 개별로 교체하면 된다.
 */
public record Product(
        String id,
        House house,
        String name,
        int price,
        String category,
        String image,
        String productUrl
) {}

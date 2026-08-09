package com.mcmhouse.domain;

import java.util.List;

/**
 * MCM 4개 House. 진단 점수 계산과 Zone 매핑의 기준이 되는 고정 데이터.
 * 명세서의 House 소개(설명/태그/Zone)를 그대로 담는다.
 */
public enum House {

    LEGACY(
            "LEGACY HOUSE",
            "시간이 쌓아온 가치와 이야기를 중요하게 여기며, 오래도록 이어지는 스타일에 끌리는 타입입니다.",
            List.of("헤리티지", "클래식", "타임리스"),
            "LEGACY ZONE", "HERITAGE 탐험", "#8a6d3b",
            List.of("MCM-LEG-001", "MCM-LEG-002", "MCM-LEG-003")
    ),
    INSTINCT(
            "INSTINCT HOUSE",
            "자신만의 감각을 믿고, 대담한 선택과 스타일로 개성을 표현하는 타입입니다.",
            List.of("자기표현", "대담함", "개성"),
            "INSTINCT ZONE", "BOLD 탐험", "#b0413e",
            List.of("MCM-INS-001", "MCM-INS-002", "MCM-INS-003")
    ),
    FREEDOM(
            "FREEDOM HOUSE",
            "정해진 방식에 얽매이지 않고, 다양한 환경을 자유롭게 넘나드는 스타일을 즐기는 타입입니다.",
            List.of("자유로움", "모빌리티", "유연함"),
            "FREEDOM ZONE", "MOBILITY 탐험", "#3f6f6a",
            List.of("MCM-FRE-001", "MCM-FRE-002", "MCM-FRE-003")
    ),
    CURIOSITY(
            "CURIOSITY HOUSE",
            "익숙한 것에 머무르기보다 새로운 아이디어와 경험을 발견하고 시도하는 타입입니다.",
            List.of("발견", "새로움", "실험정신"),
            "CURIOSITY ZONE", "DISCOVERY 탐험", "#5a4b8a",
            List.of("MCM-CUR-001", "MCM-CUR-002", "MCM-CUR-003")
    );

    private final String title;
    private final String description;
    private final List<String> tags;
    private final String zoneName;
    private final String zoneMission;
    private final String color;
    /** House별 대표 추천 제품 ID(임시값). 실제 상품 목록이 정해지면 이 값만 교체. */
    private final List<String> recommendedProductIds;

    House(String title, String description, List<String> tags,
          String zoneName, String zoneMission, String color,
          List<String> recommendedProductIds) {
        this.title = title;
        this.description = description;
        this.tags = tags;
        this.zoneName = zoneName;
        this.zoneMission = zoneMission;
        this.color = color;
        this.recommendedProductIds = recommendedProductIds;
    }

    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public List<String> getTags() { return tags; }
    public String getZoneName() { return zoneName; }
    public String getZoneMission() { return zoneMission; }
    public String getColor() { return color; }
    public List<String> getRecommendedProductIds() { return recommendedProductIds; }

    /**
     * QR/NFC 스캔값에서 House를 해석한다.
     * "LEGACY", "ZONE:LEGACY", "https://.../legacy" 등 값에 House 이름이 포함되면 매칭.
     */
    public static House fromScanValue(String raw) {
        if (raw == null) return null;
        String v = raw.trim().toUpperCase();
        for (House h : values()) {
            if (v.contains(h.name())) return h;
        }
        return null;
    }
}

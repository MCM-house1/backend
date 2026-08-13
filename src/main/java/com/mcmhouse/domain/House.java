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
            List.of("MCM-LEG-001", "MCM-LEG-002", "MCM-LEG-003"),
            "시간이 쌓아온 가치"
    ),
    INSTINCT(
            "INSTINCT HOUSE",
            "자신만의 감각을 믿고, 대담한 선택과 스타일로 개성을 표현하는 타입입니다.",
            List.of("자기표현", "대담함", "개성"),
            "INSTINCT ZONE", "BOLD 탐험", "#b0413e",
            List.of("MCM-INS-001", "MCM-INS-002", "MCM-INS-003"),
            "자신의 감각을 믿는 대담함"
    ),
    FREEDOM(
            "FREEDOM HOUSE",
            "정해진 방식에 얽매이지 않고, 다양한 환경을 자유롭게 넘나드는 스타일을 즐기는 타입입니다.",
            List.of("자유로움", "모빌리티", "유연함"),
            "FREEDOM ZONE", "MOBILITY 탐험", "#3f6f6a",
            List.of("MCM-FRE-001", "MCM-FRE-002", "MCM-FRE-003"),
            "얽매이지 않는 자유로움"
    ),
    CURIOSITY(
            "CURIOSITY HOUSE",
            "익숙한 것에 머무르기보다 새로운 아이디어와 경험을 발견하고 시도하는 타입입니다.",
            List.of("발견", "새로움", "실험정신"),
            "CURIOSITY ZONE", "DISCOVERY 탐험", "#5a4b8a",
            List.of("MCM-CUR-001", "MCM-CUR-002", "MCM-CUR-003"),
            "새로운 것을 향한 호기심"
    );

    private final String title;
    private final String description;
    private final List<String> tags;
    private final String zoneName;
    private final String zoneMission;
    private final String color;
    /** House별 대표 추천 제품 ID(임시값). 실제 상품 목록이 정해지면 이 값만 교체. */
    private final List<String> recommendedProductIds;
    /** 복합형 설명문을 조합할 때 쓰는 명사구. 예: "시간이 쌓아온 가치" */
    private final String trait;

    House(String title, String description, List<String> tags,
          String zoneName, String zoneMission, String color,
          List<String> recommendedProductIds, String trait) {
        this.title = title;
        this.description = description;
        this.tags = tags;
        this.zoneName = zoneName;
        this.zoneMission = zoneMission;
        this.color = color;
        this.recommendedProductIds = recommendedProductIds;
        this.trait = trait;
    }

    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public List<String> getTags() { return tags; }
    public String getZoneName() { return zoneName; }
    public String getZoneMission() { return zoneMission; }
    public String getColor() { return color; }
    public List<String> getRecommendedProductIds() { return recommendedProductIds; }
    public String getTrait() { return trait; }

    /**
     * 복합형 제목. 동점 House를 "×"로 잇는다. 예: "LEGACY × CURIOSITY"
     * 단일 House면 그 House의 title을 그대로 쓴다.
     */
    public static String comboTitle(List<House> houses) {
        if (houses == null || houses.isEmpty()) return "";
        if (houses.size() == 1) return houses.get(0).getTitle();
        return houses.stream().map(House::name).collect(java.util.stream.Collectors.joining(" × "));
    }

    /**
     * 복합형 설명문. 각 House의 trait를 이어 붙인다.
     * 예: LEGACY + CURIOSITY → "시간이 쌓아온 가치와 새로운 것을 향한 호기심을 함께 지닌 타입입니다."
     * 단일 House면 그 House의 description을 그대로 쓴다.
     */
    public static String comboDescription(List<House> houses) {
        if (houses == null || houses.isEmpty()) return "";
        if (houses.size() == 1) return houses.get(0).getDescription();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < houses.size(); i++) {
            String trait = houses.get(i).getTrait();
            sb.append(trait);
            if (i < houses.size() - 1) sb.append(hasFinalConsonant(trait) ? "과 " : "와 ");
        }
        String joined = sb.toString();
        return joined + (hasFinalConsonant(joined) ? "을" : "를") + " 함께 지닌 타입입니다.";
    }

    /** 한글 마지막 글자에 받침이 있는지. 조사(와/과, 을/를) 선택에 쓴다. */
    private static boolean hasFinalConsonant(String s) {
        if (s == null || s.isBlank()) return false;
        char last = s.charAt(s.length() - 1);
        if (last < 0xAC00 || last > 0xD7A3) return false;   // 한글 음절이 아니면 판단하지 않음
        return (last - 0xAC00) % 28 != 0;
    }

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

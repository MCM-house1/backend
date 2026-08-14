package com.mcmhouse.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Zone 방문 인증·패스포트·현재 위치 관련 DTO. */
public final class ZoneDtos {

    private ZoneDtos() {}

    public record VisitRequest(
            @NotNull(message = "QR/NFC 스캔값(scanValue)이 필요합니다.")
            String scanValue    // 예: "LEGACY", "ZONE:LEGACY", "https://.../legacy"
    ) {}

    public record ZoneStatusView(
            String house, String zoneName, String zoneMission,
            String color, int order, boolean visited
    ) {}

    public record PassportView(
            Long resultId,
            int visitedCount,
            int totalZones,
            boolean completed,
            String nextRecommended,         // 다음 추천 미방문 Zone (없으면 null)
            String currentZone,             // 마지막으로 스캔한 Zone (없으면 null)
            List<ZoneStatusView> zones      // 추천 순서대로 정렬된 전체 Zone 현황
    ) {}

    /** 현재 위치. GPS가 아니라 마지막으로 스캔한 Zone을 돌려주는 것뿐이다. */
    public record CurrentZoneView(
            Long resultId,
            String currentZone,     // 아직 아무 Zone도 스캔하지 않았으면 null
            String zoneName,
            String zoneMission,
            String color,
            boolean visited,        // 해당 Zone의 체험(방문 인증)이 끝났는지
            String nextRecommended  // 미방문 중 추천 순위가 가장 높은 Zone
    ) {}
}

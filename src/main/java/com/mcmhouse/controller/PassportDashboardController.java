package com.mcmhouse.controller;

import com.mcmhouse.domain.DiagnosisResult;
import com.mcmhouse.domain.House;
import com.mcmhouse.domain.Product;
import com.mcmhouse.catalog.ProductCatalog;
import com.mcmhouse.repository.DiagnosisResultRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * 패스포트 대시보드 통합 API.
 *
 * 대시보드 UI가 필요로 하는 모든 데이터(멤버십 카드 · AI 아이덴티티 · 스탬프 현황 · 추천 상품)를
 * 한 번의 호출로 내려준다. 프론트가 여러 API를 조합하지 않아도 되도록 하는 집계 엔드포인트.
 *
 *  GET /api/results/{id}/passport-dashboard
 */
@Tag(name = "Passport Dashboard", description = "패스포트 대시보드 통합 조회")
@RestController
@CrossOrigin(origins = "*")
public class PassportDashboardController {

    private final DiagnosisResultRepository resultRepository;
    private final ProductCatalog productCatalog;

    public PassportDashboardController(DiagnosisResultRepository resultRepository,
                                       ProductCatalog productCatalog) {
        this.resultRepository = resultRepository;
        this.productCatalog = productCatalog;
    }

    @Operation(summary = "패스포트 대시보드 통합 조회",
            description = "멤버십 카드, AI 아이덴티티, 스탬프 현황, 추천 상품을 한 번에 반환한다.")
    @GetMapping("/results/{id}/passport-dashboard")
    public DashboardView dashboard(@PathVariable Long id) {
        DiagnosisResult result = resultRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND,
                        "진단 결과를 찾을 수 없습니다: " + id));

        House house = result.effectiveHouse();

        // 스탬프 현황: 추천 순서대로 방문/미방문
        List<House> route = result.recommendedRoute();
        List<ZoneStatus> zones = new ArrayList<>();
        int visitedCount = 0;
        House next = null;
        int order = 1;
        for (House h : route) {
            boolean visited = result.hasVisited(h);
            if (visited) visitedCount++;
            else if (next == null) next = h;
            zones.add(new ZoneStatus(h.name(), h.getZoneName(), h.getZoneMission(),
                    h.getColor(), order++, visited));
        }
        int total = House.values().length;
        House current = result.getCurrentZone();

        Membership membership = new Membership(
                house.name(),
                house.getTitle(),
                issueNumber(result.getId()),
                house.getColor(),
                level(visitedCount),
                house.getTags()
        );

        Identity identity = new Identity(
                result.isAiAnalyzed(),
                result.isAiFallback(),
                result.getAiSummary(),
                result.getAiReason()
        );

        Passport passport = new Passport(
                visitedCount, total, visitedCount == total,
                current == null ? null : current.name(),
                next == null ? null : next.name(),
                zones
        );

        List<Product> recommendations = productCatalog.forHouse(house);

        return new DashboardView(result.getId(), membership, identity, passport, recommendations);
    }

    /** 발급번호: 진단 결과 ID로부터 결정적으로 생성. 예) MCM-0001-VIP */
    private String issueNumber(Long id) {
        return String.format("MCM-%04d-VIP", id);
    }

    /** 방문 스탬프 수에 따른 멤버십 레벨(데모용 파생값). */
    private String level(int visitedCount) {
        return switch (visitedCount) {
            case 0 -> "Lv.0 Explorer";
            case 1 -> "Lv.1 Wanderer";
            case 2 -> "Lv.2 Collector";
            case 3 -> "Lv.3 Curator";
            default -> "Lv.4 Heritage Master";
        };
    }

    /* ---------- 응답 DTO ---------- */

    public record DashboardView(
            Long resultId,
            Membership membership,
            Identity identity,
            Passport passport,
            List<Product> recommendations
    ) {}

    /** 멤버십 카드 */
    public record Membership(
            String house,
            String houseTitle,
            String issueNumber,
            String personaColor,
            String level,
            List<String> styleTags
    ) {}

    /** AI 아이덴티티 분석 결과 */
    public record Identity(
            boolean analyzed,
            boolean fallback,
            String summary,
            String reason
    ) {}

    /** 스탬프/탐험 현황 */
    public record Passport(
            int visitedCount,
            int total,
            boolean completed,
            String currentZone,
            String nextRecommended,
            List<ZoneStatus> zones
    ) {}

    public record ZoneStatus(
            String house,
            String zoneName,
            String zoneMission,
            String color,
            int order,
            boolean visited
    ) {}
}

package com.mcmhouse.controller;

import com.mcmhouse.dto.StyleDiscoveryDtos.DiscoveryArchiveItem;
import com.mcmhouse.dto.StyleDiscoveryDtos.StyleDiscoveryRequest;
import com.mcmhouse.dto.StyleDiscoveryDtos.StyleDiscoveryView;
import com.mcmhouse.service.StyleDiscoveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 셀카 무드 분석(Style Discovery) API.
 *
 *  POST /api/results/{id}/style-discovery   거울 셀카 무드 분석 + 저장
 *  GET  /api/results/{id}/discoveries       패스포트 아카이브(저장된 셀카+스타일)
 */
@Tag(name = "Style Discovery", description = "셀카 무드 분석 · 패스포트 아카이브")
@RestController
@CrossOrigin(origins = "*")
public class StyleDiscoveryController {

    private final StyleDiscoveryService service;

    public StyleDiscoveryController(StyleDiscoveryService service) {
        this.service = service;
    }

    @Operation(summary = "셀카 무드 분석",
            description = "거울 셀카(photo: data URL)와 house만 받는다. 셀카에서 스타일 제목/키워드/인상과 "
                    + "COMPLETE THE LOOK을 생성하고 셀카를 저장한다. yourPick(내가 찾은 상품)은 House별 고정 "
                    + "매핑이라 항상 채워진다 — 사진에서 상품을 특정하는 방식은 정확도가 낮아 데모에서는 쓰지 않는다. "
                    + "이미지가 없거나 비전 실패 시 스타일 문장만 House 기반으로 폴백(fallback=true).")
    @PostMapping("/results/{id}/style-discovery")
    public StyleDiscoveryView analyze(@PathVariable Long id, @RequestBody StyleDiscoveryRequest req) {
        return service.analyze(id, req);
    }

    @Operation(summary = "패스포트 디스커버리 아카이브",
            description = "저장된 셀카+스타일 목록(최근순). 각 항목의 photoDataUrl은 그대로 <img>에 사용 가능.")
    @GetMapping("/results/{id}/discoveries")
    public List<DiscoveryArchiveItem> archive(@PathVariable Long id) {
        return service.archive(id);
    }

    @Operation(summary = "House별 디스커버리 단건 조회",
            description = "패스포트 화면에서 House Zone 카드를 눌렀을 때 이어지는 상세용. "
                    + "GET /results/{id}/passport 응답의 zones[].discoveryId가 null이 아닌 House만 조회 가능하며, "
                    + "아직 그 House에서 셀카 무드 분석을 하지 않았으면 404를 반환한다.")
    @GetMapping("/results/{id}/discoveries/{house}")
    public DiscoveryArchiveItem findByHouse(@PathVariable Long id, @PathVariable String house) {
        return service.findByHouse(id, house);
    }
}

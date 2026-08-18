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
            description = "거울 셀카(photo: data URL)와 house만 받는다. 상품은 보내지 않는다 — "
                    + "AI가 셀카에서 해당 House 상품 중 어떤 것인지 직접 찾아내 yourPick으로 돌려준다. "
                    + "특정에 실패하면 yourPick은 null이다. 스타일 제목/키워드/인상 + COMPLETE THE LOOK을 "
                    + "생성하고 셀카를 저장한다. 이미지가 없거나 비전 실패 시 House 기반으로 폴백(fallback=true).")
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
}

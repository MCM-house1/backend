package com.mcmhouse.dto;

import com.mcmhouse.domain.Product;
import com.mcmhouse.dto.RecommendationDtos.MatchItem;

import java.time.LocalDateTime;
import java.util.List;

/** 셀카 무드 분석(Style Discovery) 관련 DTO. */
public final class StyleDiscoveryDtos {

    private StyleDiscoveryDtos() {}

    /**
     * 셀카 무드 분석 요청. photo는 프론트 canvas.toDataURL 결과(data:image/jpeg;base64,...).
     * 상품은 받지 않는다 — 미션에 상품을 고르는 화면이 없어서, 셀카에서 AI가 직접 찾아낸다.
     */
    public record StyleDiscoveryRequest(
            String photo,              // data URL. 없으면 폴백 분석
            String house               // 이 셀카가 속한 House 미션 (대문자)
    ) {}

    /** 미션 결과 화면(YOUR STYLE DISCOVERY) 응답. */
    public record StyleDiscoveryView(
            Long discoveryId,
            String house,
            String styleTitle,             // "깔끔하지만 평범하지 않게"
            String styleDescription,
            List<String> styleKeywords,    // ["정돈된","도시적인","존재감 있는"]
            String impression,             // 이 스타일이 주는 인상
            Product yourPick,              // AI가 셀카에서 찾은 상품. 특정 실패 시 null
            List<MatchItem> completeTheLook,
            boolean fallback
    ) {}

    /** 패스포트 아카이브 항목(MY DISCOVERY ARCHIVE). 셀카 이미지 포함. */
    public record DiscoveryArchiveItem(
            Long discoveryId,
            String house,
            String photoDataUrl,           // 저장된 셀카 (그대로 <img src>에 사용)
            String styleTitle,
            List<String> styleKeywords,
            LocalDateTime createdAt
    ) {}
}

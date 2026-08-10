package com.mcmhouse.controller;

import com.mcmhouse.dto.Dtos.*;
import com.mcmhouse.service.DiagnosisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** MCM HOUSE 아이덴티티 테스트 & 팝업 Zone 탐험 API. */
@Tag(name = "MCM HOUSE", description = "아이덴티티 테스트 · Zone 탐험 · Stamp/Passport")
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")   // 해커톤 프론트 연동 편의를 위해 개방
public class DiagnosisController {

    private final DiagnosisService service;

    public DiagnosisController(DiagnosisService service) {
        this.service = service;
    }

    @Operation(summary = "문항 조회",
            description = "아이덴티티 테스트 6문항과 선택지를 순서대로 반환. 선택지의 index(0~3)를 제출에 사용.")
    @GetMapping("/questions")
    public List<QuestionView> questions() {
        return service.getQuestions();
    }

    @Operation(summary = "진단 제출",
            description = "answers = 1~6번 질문에서 고른 선택지 index 배열(예: [0,0,3,1,2,0]). "
                    + "점수 합산 → 최종 House(동점 시 복합형) → 개인화 추천 순서를 반환.")
    @PostMapping("/results")
    public ResultView submit(@Valid @RequestBody SubmitRequest req) {
        return service.submit(req);
    }

    @Operation(summary = "진단 결과 조회", description = "resultId로 저장된 진단 결과를 다시 조회.")
    @GetMapping("/results/{id}")
    public ResultView result(@Parameter(description = "진단 결과 ID") @PathVariable Long id) {
        return service.getResult(id);
    }

    @Operation(summary = "Zone 방문 인증(QR)",
            description = "scanValue = Zone QR/NFC 스캔값(예: \"LEGACY\", \"ZONE:FREEDOM\", 관련 URL). "
                    + "해당 House Stamp를 지급하고, 미방문 중 추천 순위가 가장 높은 다음 Zone을 안내. 중복 스캔은 멱등 처리.")
    @PostMapping("/results/{id}/visits")
    public PassportView visit(@Parameter(description = "진단 결과 ID") @PathVariable Long id,
                              @Valid @RequestBody VisitRequest req) {
        return service.visit(id, req);
    }

    @Operation(summary = "탐험 현황(Passport)",
            description = "4개 Zone의 방문/미방문 상태와 진행도(0/4~4/4), 완료 여부, 다음 추천 Zone을 반환.")
    @GetMapping("/results/{id}/passport")
    public PassportView passport(@Parameter(description = "진단 결과 ID") @PathVariable Long id) {
        return service.getPassport(id);
    }
}

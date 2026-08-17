package com.mcmhouse.controller;

import com.mcmhouse.dto.AiDtos.*;
import com.mcmhouse.dto.QuestionDtos.*;
import com.mcmhouse.dto.ResultDtos.*;
import com.mcmhouse.dto.ZoneDtos.*;
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

    @Operation(summary = "진단 결과 조회",
            description = "resultId로 저장된 진단 결과를 다시 조회. AI 분석을 마쳤다면 ai 필드에 결과가 함께 담긴다.")
    @GetMapping("/results/{id}")
    public ResultView result(@Parameter(description = "진단 결과 ID") @PathVariable Long id) {
        return service.getResult(id);
    }

    @Operation(summary = "[AI] 후속질문 생성",
            description = "6문항 응답을 근거로 이 사용자에게만 해당하는 자연어 후속질문 2개를 생성. "
                    + "재호출하면 질문이 새로 생성되고 기존 답변은 초기화된다. "
                    + "LLM 호출이 실패하면 기본 질문을 돌려주며 fallback=true로 표시된다.")
    @PostMapping("/results/{id}/ai/questions")
    public AiQuestionsView aiQuestions(@Parameter(description = "진단 결과 ID") @PathVariable Long id) {
        return service.generateAiQuestions(id);
    }

    @Operation(summary = "[AI] 최종 아이덴티티 판별",
            description = "후속질문에 대한 답변을 제출하면 객관식 결과와 함께 종합해 최종 House를 판별. "
                    + "answers는 생성된 질문과 같은 개수여야 한다. "
                    + "LLM 호출이 실패하면 객관식 점수 결과로 폴백하며 ai.fallback=true로 표시된다.")
    @PostMapping("/results/{id}/ai/analyze")
    public ResultView aiAnalyze(@Parameter(description = "진단 결과 ID") @PathVariable Long id,
                                @Valid @RequestBody AiAnalyzeRequest req) {
        return service.analyzeAi(id, req);
    }

    @Operation(summary = "[AI] A/B 스타일 이미지 후보 조회",
            description = "6문항 점수 1·2등 House를 A/B 후보로 반환. 각 후보의 image는 House 대표 이미지 경로(현재 placeholder).")
    @GetMapping("/results/{id}/ai/style-choice")
    public StyleChoiceOptionsView styleChoiceOptions(@Parameter(description = "진단 결과 ID") @PathVariable Long id) {
        return service.getStyleChoiceOptions(id);
    }

    @Operation(summary = "[AI] A/B 스타일 이미지 선택 제출",
            description = "chosenHouse = A/B 후보 중 고른 House. reason은 선택(고른 이유 한 줄) — 생략하면 LLM 호출 없이 "
                    + "선택한 House를 그대로 최종으로 채택한다. reason이 있으면 LLM이 선택+이유를 종합 판별하며, "
                    + "실패 시 선택한 House로 폴백(ai.fallback=true).")
    @PostMapping("/results/{id}/ai/style-choice")
    public ResultView styleChoice(@Parameter(description = "진단 결과 ID") @PathVariable Long id,
                                  @Valid @RequestBody StyleChoiceRequest req) {
        return service.analyzeStyleChoice(id, req);
    }

    @Operation(summary = "Zone 방문 인증(QR)",
            description = "scanValue = Zone QR/NFC 스캔값(예: \"LEGACY\", \"ZONE:FREEDOM\", 관련 URL). "
                    + "해당 House Stamp를 지급하고 현재 위치(currentZone)를 함께 갱신하며, "
                    + "미방문 중 추천 순위가 가장 높은 다음 Zone을 안내. 중복 스캔은 멱등 처리.")
    @PostMapping("/results/{id}/visits")
    public PassportView visit(@Parameter(description = "진단 결과 ID") @PathVariable Long id,
                              @Valid @RequestBody VisitRequest req) {
        return service.visit(id, req);
    }

    @Operation(summary = "탐험 현황(Passport)",
            description = "4개 Zone의 방문/미방문 상태와 진행도(0/4~4/4), 완료 여부, 다음 추천 Zone, 현재 위치를 반환.")
    @GetMapping("/results/{id}/passport")
    public PassportView passport(@Parameter(description = "진단 결과 ID") @PathVariable Long id) {
        return service.getPassport(id);
    }

    @Operation(summary = "현재 위치 확인",
            description = "마지막으로 스캔한 Zone을 반환. GPS가 아니라 방문 인증 시 갱신된 값이다. "
                    + "아직 아무 Zone도 스캔하지 않았으면 currentZone은 null.")
    @GetMapping("/results/{id}/current-zone")
    public CurrentZoneView currentZone(@Parameter(description = "진단 결과 ID") @PathVariable Long id) {
        return service.getCurrentZone(id);
    }
}

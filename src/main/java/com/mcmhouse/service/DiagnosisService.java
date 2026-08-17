package com.mcmhouse.service;

import com.mcmhouse.catalog.QuestionCatalog;
import com.mcmhouse.domain.*;
import com.mcmhouse.dto.AiDtos.*;
import com.mcmhouse.dto.QuestionDtos.*;
import com.mcmhouse.dto.ResultDtos.*;
import com.mcmhouse.dto.ZoneDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DiagnosisService {

    private final QuestionCatalog catalog;
    private final com.mcmhouse.repository.DiagnosisResultRepository repository;
    private final AiAnalysisService aiAnalysisService;

    public DiagnosisService(QuestionCatalog catalog,
                            com.mcmhouse.repository.DiagnosisResultRepository repository,
                            AiAnalysisService aiAnalysisService) {
        this.catalog = catalog;
        this.repository = repository;
        this.aiAnalysisService = aiAnalysisService;
    }

    public List<QuestionView> getQuestions() {
        return catalog.getQuestions().stream().map(QuestionView::from).toList();
    }

    /** 답변 제출 → 점수 계산 → 최종 House 판별 → 저장. */
    @Transactional
    public ResultView submit(SubmitRequest req) {
        List<Integer> answers = req.answers();
        if (answers.size() != catalog.size()) {
            throw new ResponseStatusException(BAD_REQUEST,
                    catalog.size() + "개 질문에 대한 답변이 필요합니다.");
        }
        List<House> picked = new ArrayList<>();
        for (int i = 0; i < answers.size(); i++) {
            int questionNo = i + 1;
            House h = catalog.resolveHouse(questionNo, answers.get(i));
            if (h == null) {
                throw new ResponseStatusException(BAD_REQUEST,
                        questionNo + "번 질문의 선택지 index가 올바르지 않습니다: " + answers.get(i));
            }
            picked.add(h);
        }
        DiagnosisResult result = repository.save(new DiagnosisResult(picked));
        return ResultView.from(result);
    }

    @Transactional(readOnly = true)
    public ResultView getResult(Long resultId) {
        return ResultView.from(find(resultId));
    }

    /**
     * Zone 방문 인증 → 해당 House Stamp 지급 → 현재 위치 갱신 → 최신 Passport 반환.
     * 스캔 한 번으로 방문 처리와 현재 위치 갱신이 함께 일어난다.
     */
    @Transactional
    public PassportView visit(Long resultId, VisitRequest req) {
        DiagnosisResult result = find(resultId);
        House house = House.fromScanValue(req.scanValue());
        if (house == null) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "인식할 수 없는 Zone 코드입니다: " + req.scanValue());
        }
        if (!result.hasVisited(house)) {   // 중복 스캔은 무시(멱등)
            result.addVisit(new ZoneVisit(house));
        }
        result.markCurrentZone(house);     // 중복 스캔이어도 현재 위치는 갱신한다
        repository.save(result);
        return buildPassport(result);
    }

    @Transactional(readOnly = true)
    public PassportView getPassport(Long resultId) {
        return buildPassport(find(resultId));
    }

    /**
     * 현재 위치 조회. GPS가 아니라 마지막으로 스캔한 Zone을 돌려준다.
     * 아직 아무 Zone도 스캔하지 않았으면 currentZone은 null이다.
     */
    @Transactional(readOnly = true)
    public CurrentZoneView getCurrentZone(Long resultId) {
        DiagnosisResult result = find(resultId);
        House current = result.getCurrentZone();

        House next = result.recommendedRoute().stream()
                .filter(h -> !result.hasVisited(h))
                .findFirst()
                .orElse(null);

        return new CurrentZoneView(
                result.getId(),
                current == null ? null : current.name(),
                current == null ? null : current.getZoneName(),
                current == null ? null : current.getZoneMission(),
                current == null ? null : current.getColor(),
                current != null && result.hasVisited(current),
                next == null ? null : next.name()
        );
    }

    /* ---------- AI 아이덴티티 분석 ---------- */

    /**
     * 6문항 결과를 바탕으로 개인화된 자연어 후속질문을 생성하고 저장한다.
     * 재호출하면 질문이 새로 생성되고 기존 답변은 초기화된다.
     */
    @Transactional
    public AiQuestionsView generateAiQuestions(Long resultId) {
        DiagnosisResult result = find(resultId);
        var generated = aiAnalysisService.generateQuestions(result);
        result.setAiQuestions(generated.questions());
        repository.save(result);
        return new AiQuestionsView(result.getId(), generated.questions(), generated.fallback());
    }

    /** 후속질문 답변을 받아 최종 House를 판별하고 결과를 반환한다. */
    @Transactional
    public ResultView analyzeAi(Long resultId, AiAnalyzeRequest req) {
        DiagnosisResult result = find(resultId);

        if (result.getAiQuestions().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "후속질문을 먼저 생성해야 합니다. POST /api/results/" + resultId + "/ai/questions 를 호출하세요.");
        }
        if (req.answers().size() != result.getAiQuestions().size()) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "질문 %d개에 대한 답변이 필요합니다. 받은 답변: %d개"
                            .formatted(result.getAiQuestions().size(), req.answers().size()));
        }

        var analysis = aiAnalysisService.analyze(result, req.answers());
        result.applyAiAnalysis(req.answers(), analysis.house(),
                analysis.summary(), analysis.reason(), analysis.fallback());
        repository.save(result);
        return ResultView.from(result);
    }

    /**
     * A/B 이미지 선택 화면에 내려줄 두 후보(6문항 점수 1·2등 House)를 조회한다.
     */
    @Transactional(readOnly = true)
    public StyleChoiceOptionsView getStyleChoiceOptions(Long resultId) {
        DiagnosisResult result = find(resultId);
        List<House> topTwo = aiAnalysisService.topTwoHouses(result);
        return new StyleChoiceOptionsView(result.getId(), toOption(topTwo.get(0)), toOption(topTwo.get(1)));
    }

    /**
     * A/B 선택(+선택적 이유)을 받아 최종 House를 판별하고 결과를 반환한다.
     * 기존 자연어 후속질문(ai/questions, ai/analyze)과는 별개의 대안 경로다.
     */
    @Transactional
    public ResultView analyzeStyleChoice(Long resultId, StyleChoiceRequest req) {
        DiagnosisResult result = find(resultId);
        House chosen = parseHouseOrBadRequest(req.chosenHouse());

        AiAnalysisService.Analysis analysis;
        try {
            analysis = aiAnalysisService.analyzeStyleChoice(result, chosen, req.reason());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(BAD_REQUEST, e.getMessage());
        }

        List<String> answers = req.reason() == null ? List.of() : List.of(req.reason());
        result.applyAiAnalysis(answers, analysis.house(), analysis.summary(), analysis.reason(), analysis.fallback());
        repository.save(result);
        return ResultView.from(result);
    }

    private StyleChoiceOption toOption(House house) {
        return new StyleChoiceOption(house.name(), house.getTitle(), house.getImage());
    }

    private House parseHouseOrBadRequest(String raw) {
        try {
            return House.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            throw new ResponseStatusException(BAD_REQUEST, "알 수 없는 House입니다: " + raw);
        }
    }

    /* ---------- 내부 ---------- */

    private DiagnosisResult find(Long resultId) {
        return repository.findById(resultId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND,
                        "진단 결과를 찾을 수 없습니다: " + resultId));
    }

    private PassportView buildPassport(DiagnosisResult result) {
        List<House> route = result.recommendedRoute();

        House next = null;                     // 미방문 중 추천 우선순위가 가장 높은 Zone
        List<ZoneStatusView> zones = new ArrayList<>();
        int order = 1;
        int visitedCount = 0;
        for (House h : route) {
            boolean visited = result.hasVisited(h);
            if (visited) visitedCount++;
            else if (next == null) next = h;
            zones.add(new ZoneStatusView(
                    h.name(), h.getZoneName(), h.getZoneMission(),
                    h.getColor(), order++, visited));
        }

        int total = House.values().length;
        House current = result.getCurrentZone();
        return new PassportView(
                result.getId(), visitedCount, total, visitedCount == total,
                next == null ? null : next.name(),
                current == null ? null : current.name(),
                zones
        );
    }
}

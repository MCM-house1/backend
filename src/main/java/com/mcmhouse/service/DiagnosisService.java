package com.mcmhouse.service;

import com.mcmhouse.domain.*;
import com.mcmhouse.dto.Dtos;
import com.mcmhouse.dto.Dtos.*;
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

    public DiagnosisService(QuestionCatalog catalog,
                            com.mcmhouse.repository.DiagnosisResultRepository repository) {
        this.catalog = catalog;
        this.repository = repository;
    }

    public List<QuestionView> getQuestions() {
        return catalog.getQuestions().stream().map(Dtos::toQuestionView).toList();
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

    /** Zone 방문 인증 → 해당 House Stamp 지급 → 최신 Passport 반환. */
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
            repository.save(result);
        }
        return buildPassport(result);
    }

    @Transactional(readOnly = true)
    public PassportView getPassport(Long resultId) {
        return buildPassport(find(resultId));
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
        return new PassportView(
                result.getId(), visitedCount, total, visitedCount == total,
                next == null ? null : next.name(), zones
        );
    }
}

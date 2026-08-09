package com.mcmhouse.controller;

import com.mcmhouse.dto.Dtos.*;
import com.mcmhouse.service.DiagnosisService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * MCM HOUSE API.
 *
 *  GET  /api/questions                     문항 조회
 *  POST /api/results                       진단 제출 → 결과/추천 순서
 *  GET  /api/results/{id}                  진단 결과 조회
 *  POST /api/results/{id}/visits           Zone 방문 인증(QR) → Passport
 *  GET  /api/results/{id}/passport         탐험 현황(Passport)
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")   // 해커톤 프론트 연동 편의를 위해 개방
public class DiagnosisController {

    private final DiagnosisService service;

    public DiagnosisController(DiagnosisService service) {
        this.service = service;
    }

    @GetMapping("/questions")
    public List<QuestionView> questions() {
        return service.getQuestions();
    }

    @PostMapping("/results")
    public ResultView submit(@Valid @RequestBody SubmitRequest req) {
        return service.submit(req);
    }

    @GetMapping("/results/{id}")
    public ResultView result(@PathVariable Long id) {
        return service.getResult(id);
    }

    @PostMapping("/results/{id}/visits")
    public PassportView visit(@PathVariable Long id, @Valid @RequestBody VisitRequest req) {
        return service.visit(id, req);
    }

    @GetMapping("/results/{id}/passport")
    public PassportView passport(@PathVariable Long id) {
        return service.getPassport(id);
    }
}

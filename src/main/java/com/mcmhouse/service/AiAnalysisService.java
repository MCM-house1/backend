package com.mcmhouse.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcmhouse.domain.DiagnosisResult;
import com.mcmhouse.domain.House;
import com.mcmhouse.catalog.QuestionCatalog;
import com.mcmhouse.llm.LlmClient;
import com.mcmhouse.llm.LlmException;
import com.mcmhouse.llm.MockLlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 아이덴티티 분석.
 *
 * <p>흐름: 6문항 결과를 근거로 LLM이 <b>사용자마다 다른</b> 자연어 후속질문 2개를 만들고,
 * 그 답변까지 종합해 LLM이 최종 House를 판별한다. 규칙기반 점수는 프롬프트의 참고자료로만 쓰인다.
 *
 * <p>LLM 호출이 실패하면 예외를 밖으로 던지지 않고 규칙기반 점수 결과로 폴백한다.
 * 데모 도중 백엔드가 멈추는 상황을 막기 위한 설계이며, 폴백 여부는 응답의 fallback 필드로 알 수 있다.
 */
@Service
public class AiAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AiAnalysisService.class);

    /** 명세서상 후속질문 개수. */
    private static final int QUESTION_COUNT = 2;

    /** LLM 실패 시 사용할 기본 후속질문. */
    private static final List<String> FALLBACK_QUESTIONS = List.of(
            "최근에 가장 자주 손이 간 옷이나 아이템은 무엇인가요? '이건 나답다' 싶었던 이유도 함께 들려주세요.",
            "요즘 즐겨 입는 스타일이나 좋아하는 브랜드가 있다면 편하게 알려주세요."
    );

    private final LlmClient llm;
    private final QuestionCatalog catalog;
    private final ObjectMapper mapper = new ObjectMapper();

    public AiAnalysisService(LlmClient llm, QuestionCatalog catalog) {
        this.llm = llm;
        this.catalog = catalog;
    }

    /* ==================== 1단계: 후속질문 생성 ==================== */

    /**
     * 6문항 응답을 바탕으로 이 사용자에게만 해당하는 후속질문 2개를 생성한다.
     *
     * @return 생성된 질문 목록과 폴백 여부
     */
    public GeneratedQuestions generateQuestions(DiagnosisResult result) {
        String prompt = buildQuestionPrompt(result);
        try {
            String raw = llm.complete(prompt);
            List<String> questions = parseQuestions(raw);
            if (questions.size() < QUESTION_COUNT) {
                throw new LlmException("질문이 " + QUESTION_COUNT + "개보다 적게 생성되었습니다: " + questions.size());
            }
            // mock은 형식만 맞는 고정 질문이므로 개인화된 질문이 아니다. 프론트가 구분할 수 있게 폴백으로 표시한다.
            return new GeneratedQuestions(questions.subList(0, QUESTION_COUNT), isMock());
        } catch (Exception e) {
            log.warn("후속질문 생성 실패 → 기본 질문 사용. resultId={}, provider={}, 원인={}",
                    result.getId(), llm.providerName(), e.getMessage());
            return new GeneratedQuestions(FALLBACK_QUESTIONS, true);
        }
    }

    private String buildQuestionPrompt(DiagnosisResult result) {
        return """
                %s
                당신은 패션 브랜드 MCM의 팝업 전시에서 방문객의 '아이덴티티'를 읽어내는 인터뷰어입니다.

                방문객이 아래 6개의 객관식 문항에 답했습니다.

                %s

                객관식 점수 집계(참고용):
                %s

                # 할 일
                이 방문객에게 **개인화된 자연어 후속질문 %d개**를 만들어 주세요.
                이 질문의 목적은 객관식만으로는 알 수 없는 결을 확인해, 최종 House를 더 정확히 판별하는 것입니다.

                # 규칙
                - **추상적인 가치관·성향 질문("창의성을 추구하나요?", "변화를 즐기나요?")은 금지합니다.**
                  대신 실제 경험을 묻는 구체적·행동 기반 질문을 쓰세요.
                  예: "최근에 가장 자주 손이 간 옷이나 아이템은 뭐예요?",
                      "요즘 새로 산 물건 중에 '이건 나답다' 싶었던 게 있나요?",
                      "즐겨 입는 스타일이나 좋아하는 브랜드를 편하게 들려주세요."
                - 이 사람의 실제 선택 내용을 근거로, 이 사람에게만 할 법한 질문을 쓰세요. 누구에게나 쓸 수 있는 일반적인 질문은 피합니다.
                - 점수가 비슷해 판별이 애매한 축이 있다면 그 차이를 가르는 질문을 우선하세요.
                - 객관식이 아니라 자유롭게 서술할 수 있는 열린 질문으로 쓰세요.
                - 한국어 존댓말, 질문 하나당 두 문장 이내, 따뜻하고 부담 없는 톤으로 씁니다.
                - House 이름(LEGACY/INSTINCT/FREEDOM/CURIOSITY)이나 점수를 질문에 드러내지 마세요.

                # 출력 형식
                아래 JSON만 출력하세요. 설명이나 코드펜스를 덧붙이지 마세요.
                {"questions": ["첫 번째 질문", "두 번째 질문"]}
                """
                .formatted(MockLlmClient.TASK_QUESTIONS, describeAnswers(result), describeScores(result), QUESTION_COUNT);
    }

    private List<String> parseQuestions(String raw) throws Exception {
        JsonNode root = mapper.readTree(stripCodeFence(raw));
        JsonNode arr = root.path("questions");
        if (!arr.isArray()) {
            throw new LlmException("응답에 questions 배열이 없습니다.");
        }
        List<String> questions = new ArrayList<>();
        for (JsonNode node : arr) {
            String q = node.asText("").trim();
            if (!q.isBlank()) questions.add(q);
        }
        return questions;
    }

    /* ==================== 2단계: 최종 판별 ==================== */

    /**
     * 객관식 결과 + 후속질문 답변을 종합해 최종 House를 판별한다.
     * 실패하면 규칙기반 최고점 House로 폴백한다.
     */
    public Analysis analyze(DiagnosisResult result, List<String> answers) {
        String prompt = buildAnalyzePrompt(result, answers);
        try {
            String raw = llm.complete(prompt);
            JsonNode root = mapper.readTree(stripCodeFence(raw));

            House house = parseHouse(root.path("house").asText(null));
            String summary = root.path("summary").asText("").trim();
            String reason = root.path("reason").asText("").trim();

            if (house == null) {
                // mock 구현이거나 모델이 House를 고르지 못한 경우. 점수 결과로 채운다.
                return fallbackAnalysis(result, summary, reason);
            }
            return new Analysis(house, summary, reason, false);

        } catch (Exception e) {
            log.warn("AI 판별 실패 → 규칙기반 결과 사용. resultId={}, provider={}, 원인={}",
                    result.getId(), llm.providerName(), e.getMessage());
            return fallbackAnalysis(result, null, null);
        }
    }

    private String buildAnalyzePrompt(DiagnosisResult result, List<String> answers) {
        List<String> questions = result.getAiQuestions();
        StringBuilder qa = new StringBuilder();
        for (int i = 0; i < questions.size() && i < answers.size(); i++) {
            qa.append("Q%d. %s%n".formatted(i + 1, questions.get(i)));
            qa.append("A%d. %s%n%n".formatted(i + 1, answers.get(i)));
        }

        return """
                %s
                당신은 패션 브랜드 MCM의 팝업 전시에서 방문객의 '아이덴티티'를 판별하는 큐레이터입니다.

                # House 정의
                %s

                # 방문객의 객관식 응답
                %s

                객관식 점수 집계(참고용, 절대 기준은 아님):
                %s

                # 방문객의 자연어 후속 응답
                %s

                # 할 일
                위 자료를 종합해 이 방문객에게 가장 잘 맞는 House 하나를 고르세요.

                # 규칙
                - 점수가 가장 높은 House를 기계적으로 고르지 마세요. 자연어 응답에서 드러난 결이 점수와 다르면 그쪽을 존중해도 됩니다.
                - 다만 근거 없이 뒤집지는 마세요. 자연어 응답에 뚜렷한 단서가 있을 때만 다른 선택을 하세요.
                - summary는 방문객에게 그대로 보여줄 문장입니다. 한국어 존댓말로 2문장 이내, 따뜻하게 씁니다.
                - reason은 왜 그 House인지에 대한 근거입니다. 방문객의 실제 답변 내용을 인용하며 2문장 이내로 씁니다.

                # 출력 형식
                아래 JSON만 출력하세요. 설명이나 코드펜스를 덧붙이지 마세요.
                house는 반드시 LEGACY, INSTINCT, FREEDOM, CURIOSITY 중 하나여야 합니다.
                {"house": "LEGACY", "summary": "...", "reason": "..."}
                """
                .formatted(MockLlmClient.TASK_ANALYZE, describeHouses(), describeAnswers(result),
                        describeScores(result), qa.toString().trim());
    }

    /** 규칙기반 점수 결과로 채운 분석. fallback=true로 표시해 프론트가 구분할 수 있게 한다. */
    private Analysis fallbackAnalysis(DiagnosisResult result, String summary, String reason) {
        House house = result.getFinalHouses().isEmpty()
                ? House.LEGACY
                : result.getFinalHouses().get(0);
        return new Analysis(
                null,   // LLM이 고른 것이 아니므로 aiHouse는 비워 둔다
                (summary == null || summary.isBlank())
                        ? House.comboDescription(result.getFinalHouses())
                        : summary,
                (reason == null || reason.isBlank())
                        ? "AI 분석을 사용할 수 없어 객관식 점수 결과(" + house.name() + ")로 안내합니다."
                        : reason,
                true
        );
    }

    /** 현재 LLM 구현이 가짜(mock)인지. 가짜 응답을 진짜 AI 결과처럼 보고하지 않기 위해 쓴다. */
    private boolean isMock() {
        return "mock".equals(llm.providerName());
    }

    private House parseHouse(String raw) {
        if (raw == null || raw.isBlank() || "null".equalsIgnoreCase(raw)) return null;
        String v = raw.trim().toUpperCase();
        for (House h : House.values()) {
            if (v.equals(h.name())) return h;
        }
        // "LEGACY HOUSE" 처럼 덧붙여 온 경우도 받아준다.
        return House.fromScanValue(v);
    }

    /* ==================== 프롬프트 조각 ==================== */

    /** 사용자가 각 문항에서 무엇을 골랐는지 사람이 읽을 수 있는 형태로. */
    private String describeAnswers(DiagnosisResult result) {
        List<House> picked = result.getAnswers();
        List<QuestionCatalog.Question> questions = catalog.getQuestions();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < questions.size() && i < picked.size(); i++) {
            QuestionCatalog.Question q = questions.get(i);
            House chosen = picked.get(i);
            String chosenText = q.options().stream()
                    .filter(o -> o.house() == chosen)
                    .map(QuestionCatalog.Option::text)
                    .findFirst()
                    .orElse("(알 수 없음)");
            sb.append("Q%d. %s%n → 선택: %s%n%n".formatted(q.no(), q.text(), chosenText));
        }
        return sb.toString().trim();
    }

    private String describeScores(DiagnosisResult result) {
        Map<House, Integer> scores = result.scoreMap();
        StringBuilder sb = new StringBuilder();
        for (House h : House.values()) {
            sb.append("- %s: %d점%n".formatted(h.name(), scores.get(h)));
        }
        return sb.toString().trim();
    }

    private String describeHouses() {
        StringBuilder sb = new StringBuilder();
        for (House h : House.values()) {
            sb.append("- %s: %s (키워드: %s)%n"
                    .formatted(h.name(), h.getDescription(), String.join(", ", h.getTags())));
        }
        return sb.toString().trim();
    }

    /**
     * 모델이 responseMimeType 지시를 어기고 ```json 펜스를 붙이는 경우를 대비한 방어 처리.
     */
    private String stripCodeFence(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (!s.startsWith("```")) return s;

        int firstNewline = s.indexOf('\n');
        if (firstNewline < 0) return s;
        s = s.substring(firstNewline + 1);

        int lastFence = s.lastIndexOf("```");
        if (lastFence >= 0) s = s.substring(0, lastFence);
        return s.trim();
    }

    /* ==================== A/B 이미지 선택 (신규) ==================== */

    /**
     * 6문항 점수 1·2등 House를 반환한다. 동점이면 {@link House#recommendedRoute} 순서(enum 선언 순서)로 결정한다.
     * House가 4개뿐이라 2등은 항상 존재한다.
     */
    public List<House> topTwoHouses(DiagnosisResult result) {
        return result.recommendedRoute().subList(0, 2);
    }

    /**
     * 1·2등 House 중 사용자가 고른 쪽을 최종 House로 채택한다.
     * 사유 입력 없이 이미지 선택 하나로 확정하는 방식이라 LLM을 호출하지 않는다.
     */
    public Analysis analyzeStyleChoice(DiagnosisResult result, House chosen) {
        List<House> topTwo = topTwoHouses(result);
        if (!topTwo.contains(chosen)) {
            throw new IllegalArgumentException("chosenHouse는 1·2등 House(" + topTwo + ") 중 하나여야 합니다: " + chosen);
        }

        House other = topTwo.get(0) == chosen ? topTwo.get(1) : topTwo.get(0);
        return new Analysis(chosen,
                House.comboDescription(List.of(chosen)),
                "%s와(과) %s 중 %s의 이미지에 더 끌린다고 답해주셨어요."
                        .formatted(topTwo.get(0).name(), other.name(), chosen.name()),
                false);
    }

    /* ==================== 반환 타입 ==================== */

    /** 후속질문 생성 결과. */
    public record GeneratedQuestions(List<String> questions, boolean fallback) {}

    /**
     * 최종 판별 결과.
     *
     * @param house    LLM이 고른 House. 폴백된 경우 null
     * @param fallback LLM 실패로 규칙기반 결과를 쓴 경우 true
     */
    public record Analysis(House house, String summary, String reason, boolean fallback) {}
}

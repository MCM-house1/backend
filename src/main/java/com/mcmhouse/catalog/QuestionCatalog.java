package com.mcmhouse.catalog;

import com.mcmhouse.domain.House;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 아이덴티티 테스트 문항 카탈로그(고정 데이터).
 * 각 선택지는 특정 House에 +2점.
 */
@Component
public class QuestionCatalog {

    public record Option(int index, String text, House house, int score) {}
    public record Question(int no, String text, List<Option> options) {}

    private static final int SCORE_PER_PICK = 2;

    private final List<Question> questions = List.of(
            q(1, "옷이나 가방을 고를 때 가장 나다운 선택은?",
                    "오래 봐도 질리지 않는 디자인", House.LEGACY,
                    "첫눈에 시선을 사로잡는 대담한 디자인", House.INSTINCT,
                    "다양한 장소와 스타일에 자연스럽게 어울리는 디자인", House.FREEDOM,
                    "새로운 소재나 독특한 디테일이 돋보이는 디자인", House.CURIOSITY),
            q(2, "여행지에서 하루의 시간을 보낸다면?",
                    "그 도시의 역사와 오래된 장소를 찾아간다", House.LEGACY,
                    "유명한 곳보다 내 감각에 끌리는 장소를 선택한다", House.INSTINCT,
                    "목적지를 정하지 않고 자유롭게 도시를 탐험한다", House.FREEDOM,
                    "처음 보는 문화와 새로운 경험을 적극적으로 찾아간다", House.CURIOSITY),
            q(3, "나를 표현하는 스타일에 가장 가까운 것은?",
                    "깔끔하고 오래 입을 수 있는 클래식한 스타일", House.LEGACY,
                    "나의 존재감이 드러나는 대담한 스타일", House.INSTINCT,
                    "상황에 따라 자유롭게 바꿔 입는 스타일", House.FREEDOM,
                    "새로운 조합을 시도하는 실험적인 스타일", House.CURIOSITY),
            q(4, "새로운 컬렉션을 봤을 때 가장 궁금한 것은?",
                    "이 디자인이 어떤 이야기와 역사에서 시작됐는지", House.LEGACY,
                    "가장 강렬하고 개성 있는 제품은 무엇인지", House.INSTINCT,
                    "다양한 상황에서 어떻게 활용할 수 있는지", House.FREEDOM,
                    "이번 컬렉션에서 새롭게 시도한 것은 무엇인지", House.CURIOSITY),
            q(5, "갑자기 하루의 자유시간이 생겼다면?",
                    "좋아했던 장소를 다시 찾아간다", House.LEGACY,
                    "그날 가장 끌리는 일을 바로 해본다", House.INSTINCT,
                    "정해두지 않고 발길 닿는 대로 움직인다", House.FREEDOM,
                    "한 번도 해보지 않은 것을 찾아본다", House.CURIOSITY),
            q(6, "새로운 공간에 들어갔을 때 나는?",
                    "공간에 담긴 이야기와 의미부터 살펴본다", House.LEGACY,
                    "가장 눈에 띄고 강렬한 것부터 찾아간다", House.INSTINCT,
                    "정해진 순서 없이 자유롭게 돌아다닌다", House.FREEDOM,
                    "처음 보는 것, 신기한 체험부터 찾아간다", House.CURIOSITY)
    );

    private Question q(int no, String text,
                       String o1, House h1, String o2, House h2,
                       String o3, House h3, String o4, House h4) {
        return new Question(no, text, List.of(
                new Option(0, o1, h1, SCORE_PER_PICK),
                new Option(1, o2, h2, SCORE_PER_PICK),
                new Option(2, o3, h3, SCORE_PER_PICK),
                new Option(3, o4, h4, SCORE_PER_PICK)
        ));
    }

    public List<Question> getQuestions() {
        return questions;
    }

    public int size() {
        return questions.size();
    }

    /** questionNo(1-based)의 optionIndex(0-based) 선택지가 가리키는 House. 유효하지 않으면 null. */
    public House resolveHouse(int questionNo, int optionIndex) {
        if (questionNo < 1 || questionNo > questions.size()) return null;
        Question question = questions.get(questionNo - 1);
        if (optionIndex < 0 || optionIndex >= question.options().size()) return null;
        return question.options().get(optionIndex).house();
    }
}

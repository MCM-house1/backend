package com.mcmhouse.dto;

import com.mcmhouse.catalog.QuestionCatalog;

import java.util.List;

/** 문항 조회 관련 DTO. */
public final class QuestionDtos {

    private QuestionDtos() {}

    public record OptionView(int index, String text) {}

    public record QuestionView(int no, String text, List<OptionView> options) {
        public static QuestionView from(QuestionCatalog.Question q) {
            List<OptionView> opts = q.options().stream()
                    .map(o -> new OptionView(o.index(), o.text()))
                    .toList();
            return new QuestionView(q.no(), q.text(), opts);
        }
    }
}

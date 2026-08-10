package com.mcmhouse.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI(/swagger-ui.html) 상단에 표시될 API 문서 메타 정보. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI mcmHouseOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("MCM HOUSE API")
                .version("v0.1")
                .description("""
                        아이덴티티 테스트 & 팝업 Zone 탐험(Stamp/Passport) API.

                        기본 플로우:
                        1) GET /api/questions 로 문항을 받아 화면에 표시
                        2) POST /api/results 로 선택지 index 배열 제출 → resultId, 최종 House, 추천 순서 수신
                        3) POST /api/results/{id}/visits 로 Zone QR 스캔값 전달 → Stamp 지급 + 다음 추천 Zone
                        4) GET /api/results/{id}/passport 로 탐험 현황(0/4~4/4) 조회
                        """));
    }
}

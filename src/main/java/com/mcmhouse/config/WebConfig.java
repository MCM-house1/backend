package com.mcmhouse.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 우리 컨트롤러(com.mcmhouse.controller)에만 "/api" 접두사를 한 번에 붙인다.
 * 컨트롤러마다 @RequestMapping("/api")을 반복하지 않기 위한 전역 설정.
 * (springdoc의 /v3/api-docs, /swagger-ui 같은 내부 엔드포인트는 대상에서 제외.)
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix("/api",
                HandlerTypePredicate.forBasePackage("com.mcmhouse.controller"));
    }
}

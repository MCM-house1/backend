package com.mcmhouse.catalog;

import com.mcmhouse.domain.House;
import com.mcmhouse.domain.Product;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * products.json을 읽어 House별로 상품을 제공하는 카탈로그.
 * 기동 시 한 번 로드하며, 상품은 안 바뀌는 고정 데이터라 메모리에 둔다.
 */
@Component
public class ProductCatalog {

    private final Map<House, List<Product>> byHouse = new EnumMap<>(House.class);
    private final List<Product> all = new ArrayList<>();

    @PostConstruct
    void load() {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = new ClassPathResource("products.json").getInputStream()) {
            List<Product> products = mapper.readValue(in, new com.fasterxml.jackson.core.type.TypeReference<>() {});
            for (House h : House.values()) byHouse.put(h, new ArrayList<>());
            for (Product p : products) {
                all.add(p);
                byHouse.get(p.house()).add(p);
            }
        } catch (Exception e) {
            throw new IllegalStateException("products.json 로드 실패", e);
        }
    }

    /** 전체 상품. */
    public List<Product> all() {
        return all;
    }

    /** 특정 House의 상품 목록. */
    public List<Product> forHouse(House house) {
        return byHouse.getOrDefault(house, List.of());
    }
}

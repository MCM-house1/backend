package com.mcmhouse.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * style_discovery.photo_data_url이 예전 배포에서 TEXT(64KB 한도)로 생성돼, 실제 셀카(base64로
 * 수백 KB~수 MB)를 저장할 때 "Data too long for column" 오류가 났다. {@code @Lob}만으로는
 * 이미 만들어진 컬럼의 타입이 바뀌지 않으므로(ddl-auto=update는 컬럼 타입을 바꾸지 않는다)
 * 시작 시 한 번 LONGTEXT로 맞춰준다. 이미 LONGTEXT면 아무 일도 하지 않는다.
 */
@Component
public class SchemaFixRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaFixRunner.class);

    private final DataSource dataSource;

    public SchemaFixRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'style_discovery' " +
                    "AND COLUMN_NAME = 'photo_data_url'");
            if (rs.next() && !"longtext".equalsIgnoreCase(rs.getString(1))) {
                stmt.executeUpdate("ALTER TABLE style_discovery MODIFY COLUMN photo_data_url LONGTEXT NOT NULL");
                log.info("style_discovery.photo_data_url을 LONGTEXT로 변경했습니다.");
            }
        } catch (Exception e) {
            // H2 등 문법이 다르거나 테이블이 아직 없는 환경에서는 조용히 넘어간다.
            log.debug("photo_data_url 스키마 점검을 건너뜁니다: {}", e.getMessage());
        }
    }
}

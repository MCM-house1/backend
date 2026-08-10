# MCM HOUSE — Backend API

아이덴티티 테스트 & 팝업 Zone 탐험(Stamp/Passport) REST API.
Spring Boot 3.3 · Java 17 · JPA · (dev) H2 / (운영) MySQL

## 실행

```bash
# 로컬 빠른 실행 (H2 인메모리, DB 설치 불필요)
mvn spring-boot:run

# 공용/운영 (MySQL: mcm_house 스키마 필요)
DB_USERNAME=root DB_PASSWORD=pw mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

- 서버: `http://localhost:8080`
- **Swagger UI (프론트 전달용): `http://localhost:8080/swagger-ui.html`**
- OpenAPI 스펙(JSON): `http://localhost:8080/v3/api-docs`
- H2 콘솔(dev): `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:mcm`)

> 프론트에는 서버를 띄운 뒤 **Swagger UI 주소**를 그대로 전달하면 됩니다.
> 각 API의 요청/응답 스키마와 예시를 브라우저에서 바로 확인·테스트할 수 있습니다.

## API

| Method | Path | 설명 |
| --- | --- | --- |
| GET  | `/api/questions` | 6개 문항 + 선택지 조회 |
| POST | `/api/results` | 진단 제출 → 점수·최종 House·추천순서 |
| GET  | `/api/results/{id}` | 진단 결과 조회 |
| POST | `/api/results/{id}/visits` | Zone 방문 인증(QR) → Passport |
| GET  | `/api/results/{id}/passport` | 탐험 현황(0/4~4/4) |

### 진단 제출
`answers`는 질문 1~6번 순서대로 고른 **선택지 index(0~3)** 배열.
```json
POST /api/results
{ "answers": [0, 0, 0, 3, 0, 0] }
```
응답에 `scores`, `finalHouses`(동점 시 2개=복합형), `combo`, `recommendedRoute` 포함.

### Zone 방문 인증
`scanValue`는 QR/NFC 스캔값. 값에 House 이름이 포함되면 매칭됩니다
(`"LEGACY"`, `"ZONE:LEGACY"`, `"https://.../legacy"` 모두 가능). 중복 스캔은 멱등 처리.
```json
POST /api/results/{id}/visits
{ "scanValue": "ZONE:FREEDOM" }
```
`nextRecommended`는 **미방문 Zone 중 추천 우선순위가 가장 높은 곳**을 자동 안내합니다.

## 도메인 로직
- **점수**: 선택지마다 해당 House +2
- **최종 House**: 최고점 House. 동점이면 복합형(enum 선언 순서로 정렬)
- **추천 순서**: 점수 내림차순, 동점은 enum 순서 (`LEGACY→INSTINCT→FREEDOM→CURIOSITY`)
- **Stamp**: Zone 방문 1건 = 해당 House Stamp 1개, 4개 수집 시 `completed=true`

질문/House/Zone은 고정 데이터라 코드로 관리
([House.java](src/main/java/com/mcmhouse/domain/House.java),
[QuestionCatalog.java](src/main/java/com/mcmhouse/domain/QuestionCatalog.java)).
저장 대상은 진단 결과와 방문 기록뿐입니다.

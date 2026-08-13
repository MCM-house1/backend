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

### AI 분석용 API 키 (선택)

키가 없어도 서버는 정상 기동하고 모든 API가 동작합니다. AI 판별만 규칙기반으로 폴백됩니다.

**팀원 각자 자기 키를 쓰는 방법 (권장)**

1. https://aistudio.google.com/apikey 에서 **Create API key**
   (Google 계정만 있으면 되고 카드 등록 불필요. Cloud 프로젝트가 없으면 *프로젝트 만들기*를 먼저 누르세요.)
2. `local.yml.example`을 같은 폴더에 **`local.yml`**로 복사
3. `api-key`에 발급받은 키를 붙여넣고 서버 재시작

`local.yml`은 `.gitignore`에 있어 커밋되지 않으므로, 서로의 키를 공유할 필요가 없고
다른 프로젝트나 시스템 환경에도 영향을 주지 않습니다.

환경변수 `GEMINI_API_KEY`로 넣어도 동작하지만, 계정 전역에 적용되므로 `local.yml`을 권장합니다.
(둘 다 있으면 `local.yml`이 우선)

기동 로그로 확인:

| 로그 | 의미 |
| --- | --- |
| `LLM provider = gemini (model=...)` | 키 인식됨, 실제 AI 분석 동작 |
| `LLM API 키가 비어 있어 mock으로 동작합니다` | 키 미인식, 규칙기반 폴백 |

### 문제 해결

**`fallback: true`가 계속 나올 때** — 서버 로그의 `WARN ... 실패` 줄에 원인이 찍힙니다.

| 로그의 상태 코드 | 원인 | 대응 |
| --- | --- | --- |
| `404 ... no longer available` | 지정한 모델이 퇴역 | 아래 명령으로 현재 모델 확인 후 `llm.model` 교체 |
| `503 ... high demand` | 구글 서버 혼잡 (내 키 문제 아님) | 자동으로 3회까지 재시도함. 계속되면 다른 모델로 교체 |
| `429` | 호출 속도 제한 | 잠시 후 재시도 |
| `400 API key not valid` | 키가 잘못됨 | `local.yml`의 키 확인 |

현재 사용 가능한 모델 목록 확인:

```bash
curl "https://generativelanguage.googleapis.com/v1beta/models?key=발급받은_키"
```

`supportedGenerationMethods`에 `generateContent`가 있는 모델이면 쓸 수 있습니다.
기본값은 `gemini-2.5-flash`이며, 퇴역 걱정 없는 대안으로 `gemini-flash-latest`가 있습니다
(대신 트래픽이 몰려 503이 잦습니다).

제공자를 바꾸려면 `llm.provider`를 수정하면 됩니다 (`gemini` / `mock`).

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
| POST | `/api/results/{id}/ai/questions` | **[AI]** 개인화 후속질문 2개 생성 |
| POST | `/api/results/{id}/ai/analyze` | **[AI]** 답변 제출 → 최종 House 판별 |
| POST | `/api/results/{id}/visits` | Zone 방문 인증(QR) → Passport |
| GET  | `/api/results/{id}/passport` | 탐험 현황(0/4~4/4) |
| GET  | `/api/results/{id}/current-zone` | 현재 위치(마지막 스캔 Zone) 조회 |

### 진단 제출
`answers`는 질문 1~6번 순서대로 고른 **선택지 index(0~3)** 배열.
```json
POST /api/results
{ "answers": [0, 0, 0, 3, 0, 0] }
```
응답에 `scores`, `finalHouses`(동점 시 2개=복합형), `combo`, `comboTitle`, `comboDescription`, `recommendedRoute`, `ai` 포함.

복합형이면 `comboTitle`은 `"LEGACY × CURIOSITY"`, `comboDescription`은
`"시간이 쌓아온 가치와 새로운 것을 향한 호기심을 함께 지닌 타입입니다."` 처럼 조합됩니다.

### AI 아이덴티티 분석
6문항만으로는 알 수 없는 결을 확인하기 위해, **사용자마다 다른 자연어 후속질문**을 LLM이 생성하고
그 답변까지 종합해 LLM이 최종 House를 판별합니다. 규칙기반 점수는 프롬프트의 참고자료로만 쓰입니다.

호출 순서는 `POST /api/results` → `ai/questions` → `ai/analyze` 입니다.

```json
POST /api/results/{id}/ai/questions
→ { "resultId": 1, "questions": ["...", "..."], "fallback": false }

POST /api/results/{id}/ai/analyze
{ "answers": ["10년 전에 산 가죽 가방을 아직도 들고 다녀요.", "낯선 골목을 발견하면 일단 들어가 봅니다."] }
→ ResultView (ai.house, ai.summary, ai.reason 포함)
```

`answers`는 생성된 질문과 **개수가 같아야** 합니다. 질문 생성 없이 `analyze`를 호출하면 400.

> **폴백**: LLM 호출이 실패해도 500을 내지 않고 객관식 점수 결과로 내려갑니다.
> 이때 `fallback: true`(질문) / `ai.fallback: true`(판별)로 표시되고 `ai.house`는 `null`이 됩니다.
> API 키가 없을 때도 동일하게 동작하므로, 키 없이 프론트 연동을 먼저 진행할 수 있습니다.

### 현재 위치 확인
GPS가 아니라 **마지막으로 스캔한 Zone**을 돌려주는 API입니다.
`POST /visits`로 방문 인증을 할 때 `currentZone`이 함께 갱신되며, 조회만 별도 엔드포인트로 제공합니다.
아직 아무 Zone도 스캔하지 않았으면 `currentZone`은 `null`입니다.

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

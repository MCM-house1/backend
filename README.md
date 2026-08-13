# MCM HOUSE — Backend API

아이덴티티 테스트 · AI 분석 · 팝업 Zone 탐험(Stamp/Passport) REST API.
Spring Boot 3.3 · Java 17 · JPA · (dev) H2 / (운영) MySQL

전체 흐름:

```
시작 → 6문항 객관식 → AI 후속질문 2개 → AI 최종 판별 → House 결과 → Zone 탐험 → Stamp 4개 수집
```

---

## 실행

```bash
# 로컬 빠른 실행 (H2 인메모리, DB 설치 불필요)
mvn spring-boot:run

# 공용/운영 (MySQL: mcm_house 스키마 필요)
DB_USERNAME=root DB_PASSWORD=pw mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

| | 주소 |
| --- | --- |
| 서버 | `http://localhost:8080` |
| **Swagger UI (프론트 전달용)** | **`http://localhost:8080/swagger-ui.html`** |
| OpenAPI 스펙(JSON) | `http://localhost:8080/v3/api-docs` |
| H2 콘솔 (dev) | `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:mcm`) |

> 프론트에는 서버를 띄운 뒤 **Swagger UI 주소**를 그대로 전달하면 됩니다.
> 각 API의 요청/응답 스키마와 예시를 브라우저에서 바로 확인·테스트할 수 있습니다.
> 서버 없이 스펙만 필요하면 저장소의 [docs/openapi.json](docs/openapi.json)을 보면 됩니다.

**API 키 없이도 모든 API가 동작합니다.** AI 판별만 규칙기반으로 폴백되므로,
키 설정 없이 바로 프론트 연동을 시작할 수 있습니다. (설정은 아래 [AI 분석 설정](#ai-분석-설정) 참고)

---

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

응답에 `scores`, `finalHouses`, `combo`, `comboTitle`, `comboDescription`,
`primaryHouse`, `recommendedRoute`, `ai` 가 포함됩니다.

동점이면 복합형으로 처리됩니다.

```json
"finalHouses": ["LEGACY", "CURIOSITY"],
"combo": true,
"comboTitle": "LEGACY × CURIOSITY",
"comboDescription": "시간이 쌓아온 가치와 새로운 것을 향한 호기심을 함께 지닌 타입입니다."
```

`primaryHouse`는 화면에 크게 보여줄 대표 House이며, House 이름·설명·태그·대표컬러·추천제품 ID를 담고 있습니다.
AI 판별을 마쳤다면 AI가 고른 House가, 아직이면 최고점 House가 들어갑니다.

### AI 아이덴티티 분석

6문항만으로는 알 수 없는 결을 확인하기 위해, **사용자마다 다른 자연어 후속질문**을 LLM이 생성하고
그 답변까지 종합해 LLM이 최종 House를 판별합니다. 규칙기반 점수는 프롬프트의 참고자료로만 쓰입니다.

호출 순서는 `POST /api/results` → `ai/questions` → `ai/analyze`.

```json
POST /api/results/{id}/ai/questions
→ { "resultId": 1, "questions": ["...", "..."], "fallback": false }

POST /api/results/{id}/ai/analyze
{ "answers": ["10년 전에 산 가죽 가방을 아직도 들고 다녀요.", "낯선 골목을 발견하면 일단 들어가 봅니다."] }
→ ResultView (ai.house, ai.summary, ai.reason 포함)
```

- `answers`는 생성된 질문과 **개수가 같아야** 합니다. 다르면 400.
- 질문 생성 없이 `analyze`를 호출하면 400.
- `ai/questions`를 다시 호출하면 질문이 새로 생성되고 기존 답변은 초기화됩니다.

**AI는 점수를 뒤집을 수 있습니다.** 자연어 답변에 뚜렷한 단서가 있으면 최고점 House가 아닌 곳을 고릅니다.
실제 동작 예시 — 점수는 LEGACY·CURIOSITY 동점이었지만 답변을 보고 CURIOSITY로 판별:

```
"익숙함은 안전장치일 뿐, 늘 새로운 쪽으로 한 발 더 내딛는 편입니다."
→ ai.house: "CURIOSITY"
```

> **폴백**: LLM 호출이 실패해도 500을 내지 않고 객관식 점수 결과로 내려갑니다.
> 이때 `fallback: true`(질문) / `ai.fallback: true`(판별)로 표시되고 `ai.house`는 `null`이 됩니다.
> 프론트는 이 값으로 "AI 분석 결과"와 "점수 기반 결과"를 구분해 표시할 수 있습니다.

### Zone 방문 인증

`scanValue`는 QR/NFC 스캔값. 값에 House 이름이 포함되면 매칭됩니다
(`"LEGACY"`, `"ZONE:LEGACY"`, `"https://.../legacy"` 모두 가능). 중복 스캔은 멱등 처리.

```json
POST /api/results/{id}/visits
{ "scanValue": "ZONE:FREEDOM" }
```

`nextRecommended`는 **미방문 Zone 중 추천 우선순위가 가장 높은 곳**을 자동 안내합니다.
추천 순서를 무시하고 다른 Zone을 먼저 방문해도 정상 처리되며, 남은 Zone 중 우선순위가 높은 곳을 다시 안내합니다.

### 현재 위치 확인

GPS가 아니라 **마지막으로 스캔한 Zone**을 돌려주는 API입니다.
`POST /visits`로 방문 인증을 할 때 `currentZone`이 함께 갱신되며, 조회만 별도 엔드포인트로 제공합니다.
아직 아무 Zone도 스캔하지 않았으면 `currentZone`은 `null`입니다.

---

## 도메인 로직

- **점수**: 선택지마다 해당 House +2
- **최종 House**: 최고점 House. 동점이면 복합형(enum 선언 순서로 정렬)
- **추천 순서**: 점수 내림차순, 동점은 enum 순서 (`LEGACY→INSTINCT→FREEDOM→CURIOSITY`)
- **Stamp**: Zone 방문 1건 = 해당 House Stamp 1개, 4개 수집 시 `completed=true`

질문/House/Zone은 고정 데이터라 코드로 관리합니다
([House.java](src/main/java/com/mcmhouse/domain/House.java),
[QuestionCatalog.java](src/main/java/com/mcmhouse/domain/QuestionCatalog.java)).
저장 대상은 진단 결과와 방문 기록뿐입니다.

House의 대표컬러·태그·추천제품 ID도 `House.java`에 있습니다.
추천제품 ID는 임시값(`MCM-LEG-001` 등)이므로, 실제 상품 목록이 정해지면 그 값만 교체하면 됩니다.

---

## AI 분석 설정

키가 없어도 서버는 정상 기동하고 모든 API가 동작합니다. AI 판별만 규칙기반으로 폴백됩니다.

### 팀원 각자 자기 키를 쓰는 방법 (권장)

1. https://aistudio.google.com/apikey 에서 **Create API key**
   (Google 계정만 있으면 되고 카드 등록 불필요. Cloud 프로젝트가 없으면 *프로젝트 만들기*를 먼저 누르세요.)
2. `local.yml.example`을 같은 폴더에 **`local.yml`**로 복사
3. `api-key`에 발급받은 키를 붙여넣고 서버 재시작

`local.yml`은 `.gitignore`에 있어 커밋되지 않습니다.
서로의 키를 공유할 필요가 없고, 다른 프로젝트나 시스템 환경에도 영향을 주지 않습니다.

환경변수 `GEMINI_API_KEY`로 넣어도 동작하지만 계정 전역에 적용되므로 `local.yml`을 권장합니다.
(둘 다 있으면 `local.yml`이 우선)

기동 로그로 확인:

| 로그 | 의미 |
| --- | --- |
| `LLM provider = gemini (model=...)` | 키 인식됨, 실제 AI 분석 동작 |
| `LLM API 키가 비어 있어 mock으로 동작합니다` | 키 미인식, 규칙기반 폴백 |

### 제공자 교체

`llm.provider`만 바꾸면 됩니다 (`gemini` / `mock`).
새 제공자를 추가하려면 `LlmClient` 인터페이스를 구현하고 `LlmConfig`에 분기를 추가하면 됩니다.

```
src/main/java/com/mcmhouse/llm/
├── LlmClient.java        인터페이스
├── GeminiLlmClient.java  Gemini 구현 (재시도 포함)
├── MockLlmClient.java    키 없을 때 쓰는 가짜 구현
└── LlmConfig.java        설정에 따라 구현체 선택
```

---

## 문제 해결

**`fallback: true`가 계속 나올 때** — 서버 로그의 `WARN ... 실패` 줄에 원인이 찍힙니다.

| 상태 코드 | 원인 | 대응 |
| --- | --- | --- |
| `404 ... no longer available` | 지정한 모델이 퇴역 | 아래 명령으로 현재 모델 확인 후 `llm.model` 교체 |
| `503 ... high demand` | 구글 서버 혼잡 (내 키 문제 아님) | 자동으로 3회 재시도함. 계속되면 다른 모델로 교체 |
| `429` | 호출 속도 제한 | 잠시 후 재시도 |
| `400 API key not valid` | 키가 잘못됨 | `local.yml`의 키 확인 |

현재 사용 가능한 모델 목록:

```bash
curl "https://generativelanguage.googleapis.com/v1beta/models?key=발급받은_키"
```

`supportedGenerationMethods`에 `generateContent`가 있는 모델이면 쓸 수 있습니다.
기본값은 `gemini-2.5-flash`이며, 퇴역 걱정이 없는 대안으로 `gemini-flash-latest`가 있습니다
(대신 트래픽이 몰려 503이 잦습니다).

**포트 8080이 이미 사용 중일 때** — 이전에 띄운 서버가 남아 있는 경우입니다.

```bash
# Windows
netstat -ano | findstr :8080     # 마지막 열이 PID
taskkill /PID <PID> /F
```

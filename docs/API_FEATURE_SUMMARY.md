# Money Weather API Feature Summary

## 기능별 요약

| 기능 영역 | 포함 API | 주요 내용 |
| --- | --- | --- |
| 인증 | `POST /api/v1/auth/dev-token` | 개발 및 테스트용 Bearer 토큰을 발급합니다. 외부 인증 연동 전까지 사용자 컨텍스트 검증에 사용합니다. |
| 사용자/시드 | `GET /api/v1/users/me`, `POST /api/v1/dev/seed` | 현재 사용자 정보를 확인하고, 데모 데이터를 초기화합니다. |
| 자산 기초 데이터 | `GET /api/v1/accounts`, `GET /api/v1/cards`, `GET /api/v1/categories` | 계좌 잔액, 카드 결제일, 거래 분류 기준을 제공합니다. |
| 거래 관리 | `GET /api/v1/transactions`, `PATCH /api/v1/transactions/{transactionId}/category` | 월별 거래 검색과 카테고리 보정을 지원합니다. |
| 예정 금융 이벤트 | `GET/POST/PATCH/DELETE /api/v1/financial-events` | 카드 결제, 급여, 구독료 등 미래 현금흐름을 관리합니다. 삭제는 취소 상태 변경으로 처리합니다. |
| 반복 규칙 | `POST/GET/PATCH/DELETE /api/v1/recurring-rules` | 월 반복 입출금 규칙을 생성, 조회, 수정, 비활성화합니다. 생성 시 해당 월 이벤트를 함께 만듭니다. |
| 예측/대시보드 | `GET /api/v1/forecasts`, `GET /api/v1/dashboard`, `POST /api/v1/available-funds/simulations` | 현재 잔액과 예정 이벤트를 기준으로 사용 가능 자금, 최저 잔액일, 자금 날씨, 추가 지출 영향을 계산합니다. |
| AI Agent | `POST /api/v1/agent/conversations`, `POST /api/v1/agent/chat`, `GET /api/v1/agent/conversations/{id}/messages`, `GET /api/v1/agent/suggestions`, `POST /api/v1/agent/analyze-spending`, `POST /api/v1/agent/detect-recurring`, `POST /api/v1/agent/recommend-actions`, `GET /api/v1/agent/evidence/{messageId}` | 대화 저장, 규칙 기반 또는 OpenAI 호환 답변, 추천 질문, 소비 분석, 반복 지출 탐지, 행동 추천, 답변 근거 조회를 제공합니다. |
| 예산/안내/알림 | `GET/PUT /api/v1/budgets/monthly`, `GET /api/v1/weather-statuses`, `GET /api/v1/forecast-alerts` | 월 예산 저장/조회, 날씨 상태 설명, 예측 위험 알림을 제공합니다. |
| 운영/모니터링 | `/actuator/health`, `/actuator/metrics`, `/actuator/prometheus`, `api_request_logs` | 운영 상태 확인, 메트릭 수집, Prometheus 연동, API 요청 감사 로그 저장을 지원합니다. |

## 구현 완료된 내부 개선

| 항목 | 완료 내용 |
| --- | --- |
| Flyway V2 이후 마이그레이션 | `V2__observability.sql`로 운영 요청 로그 테이블과 조회 인덱스를 추가했습니다. |
| Swagger 설명 보강 | 업무 API와 인증 API에 정상 한국어 `@Tag`, `@Operation` 설명을 적용했습니다. |
| 계산 로직 테스트 세분화 | 잔액 계산, 고정 지출 필터링, 날씨 등급, 최저 잔액일, 잘못된 기간 검증 테스트를 추가했습니다. |
| 운영 로그/모니터링 | Spring Actuator, Prometheus registry, 요청 로그 필터, 로그 저장 JPA repository를 추가했습니다. |

## 외부 키가 필요한 남은 영역

| 항목 | 이유 |
| --- | --- |
| 실제 AI 모델 호출 | `AI_PROVIDER=openai-compatible`과 `AI_API_KEY`가 필요합니다. 키가 없으면 기본 `rule-based` 답변으로 동작합니다. |
| 파인튜닝 또는 별도 모델 학습 | 학습 데이터셋, 모델 제공자, 비용/보안 정책 확정이 필요합니다. 현재 백엔드는 모델 교체 가능한 어댑터까지만 구현되어 있습니다. |

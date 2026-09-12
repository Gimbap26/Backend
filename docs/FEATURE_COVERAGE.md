# Feature Coverage

검토 기준:

- `기능_명세서_초안.pdf`: 핵심 백엔드 API 18개
- `기획_초안.pdf`: MVP 화면, 자금 날씨 계산, AI Agent, 반복 지출, 월 예산, 예측 안내
- 현재 코드: Spring Boot Controller, Service, Flyway migration, 테스트 코드

## 핵심 API 구현 현황

| No | Method | Path | 설명 | 상태 |
| --- | --- | --- | --- | --- |
| 01 | GET | `/api/v1/users/me` | 현재 사용자 조회 | 완료 |
| 02 | POST | `/api/v1/dev/seed` | 개발용 시드 데이터 생성 | 완료 |
| 03 | GET | `/api/v1/accounts` | 계좌 목록 및 잔액 조회 | 완료 |
| 04 | GET | `/api/v1/cards` | 카드 목록 조회 | 완료 |
| 05 | GET | `/api/v1/categories` | 거래 카테고리 조회 | 완료 |
| 06 | GET | `/api/v1/transactions` | 거래 내역 검색 | 완료 |
| 07 | PATCH | `/api/v1/transactions/{transactionId}/category` | 거래 카테고리 변경 | 완료 |
| 08 | GET | `/api/v1/financial-events` | 예정 금융 이벤트 조회 | 완료 |
| 09 | POST | `/api/v1/financial-events` | 예정 금융 이벤트 등록 | 완료 |
| 10 | PATCH | `/api/v1/financial-events/{eventId}` | 예정 금융 이벤트 수정 | 완료 |
| 11 | DELETE | `/api/v1/financial-events/{eventId}` | 예정 금융 이벤트 취소 | 완료 |
| 12 | POST | `/api/v1/recurring-rules` | 반복 규칙 생성 | 완료 |
| 13 | GET | `/api/v1/forecasts` | 잔액 예측 타임라인 조회 | 완료 |
| 14 | GET | `/api/v1/dashboard` | 홈 대시보드 조회 | 완료 |
| 15 | POST | `/api/v1/available-funds/simulations` | 추가 지출 시뮬레이션 | 완료 |
| 16 | POST | `/api/v1/agent/conversations` | AI 대화 생성 | 완료 |
| 17 | POST | `/api/v1/agent/chat` | AI Agent 질문 | 완료 |
| 18 | GET | `/api/v1/agent/conversations/{id}/messages` | AI 대화 메시지 조회 | 완료 |

## 기획 검토로 보강한 API

| 우선순위 | Method | Path | 보강 이유 | 상태 |
| --- | --- | --- | --- | --- |
| P1 | GET | `/api/v1/recurring-rules` | 반복 지출 관리 화면에 필요 | 완료 |
| P1 | PATCH | `/api/v1/recurring-rules/{id}` | 금액, 일자, 활성 상태 수정에 필요 | 완료 |
| P1 | DELETE | `/api/v1/recurring-rules/{id}` | 반복 규칙 비활성화에 필요 | 완료 |
| P1 | PUT | `/api/v1/budgets/monthly` | 월 예산 저장에 필요 | 완료 |
| P2 | GET | `/api/v1/budgets/monthly` | 카테고리별 예산 조회에 필요 | 완료 |
| P2 | GET | `/api/v1/weather-statuses` | 자금 날씨 상태 안내 화면에 필요 | 완료 |
| P2 | GET | `/api/v1/forecast-alerts` | 예측 위험 알림 표시에 필요 | 완료 |
| P1 | GET | `/api/v1/agent/suggestions` | 추천 질문 칩 제공 | 완료 |
| P1 | POST | `/api/v1/agent/analyze-spending` | 소비 패턴 분석 도구 | 완료 |
| P1 | POST | `/api/v1/agent/detect-recurring` | 반복 지출 후보 탐지 | 완료 |
| P1 | POST | `/api/v1/agent/recommend-actions` | 자금 날씨 기반 행동 추천 | 완료 |

## 내부 개선 완료

| 항목 | 구현 내용 | 확인 |
| --- | --- | --- |
| Flyway V2 이후 마이그레이션 | `V2__observability.sql`로 `api_request_logs`와 인덱스 추가 | 테스트에서 V1, V2 적용 확인 |
| Swagger 설명 보강 | 전체 업무 API와 인증 API에 `@Tag`, `@Operation` 설명 추가 | Swagger UI에서 확인 가능 |
| 계산 로직 테스트 세분화 | 잔액, 고정 지출, 날씨 등급, 최저 잔액일, 기간 검증 테스트 추가 | `ForecastServiceTest` 5개 통과 |
| 운영 로그/모니터링 | 요청 로그 필터, Actuator health/metrics/prometheus 추가 | 통합 테스트 부팅 및 endpoint 노출 확인 |

## 현재 MVP 메모

- 데모 데이터는 애플리케이션 시작 시 사용자가 없을 때 자동 생성됩니다.
- 사용자 컨텍스트는 Bearer 토큰, `X-User-Id`, `userId` query 순서로 해석합니다.
- 기본 AI 답변은 규칙 기반으로 동작하며, 설정을 바꾸면 OpenAI 호환 Chat Completions API에 연결할 수 있습니다.
- 외부 AI API 키가 필요한 파인튜닝/실모델 호출을 제외한 백엔드 기능은 로컬에서 실행 가능합니다.

# Feature Coverage

엔드포인트 47개. 로그인·회원가입을 제외한 모든 API 는 `Authorization: Bearer <토큰>` 이 필요합니다.

상태 표기: **완료** = 구현과 자동 테스트까지 끝남 / **키 필요** = 코드는 완료, 실제 외부 AI 연결에만 API 키가 필요

## 인증 / 사용자

| Method | Path | 설명 | 상태 |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/signup` | 이메일·비밀번호 회원가입, 가입 즉시 토큰 발급 | 완료 |
| POST | `/api/v1/auth/login` | 로그인, JWT 발급 (실패 반복 시 429) | 완료 |
| POST | `/api/v1/auth/logout` | 모든 기기의 토큰 무효화 | 완료 |
| PATCH | `/api/v1/auth/password` | 비밀번호 변경, 다른 기기 토큰 무효화 | 완료 |
| GET | `/api/v1/users/me` | 로그인한 사용자 정보 | 완료 |
| POST | `/api/v1/dev/seed` | 개발용 데이터 초기화 (**전체 삭제 후 데모 계정 재생성**, 운영에서는 꺼짐) | 완료 |

## 계좌 · 카드 · 카테고리

| Method | Path | 설명 | 상태 |
| --- | --- | --- | --- |
| GET | `/api/v1/accounts` | 계좌 목록, 자산 포함 잔액 합계와 전체 합계 | 완료 |
| POST | `/api/v1/accounts` | 계좌 등록 | 완료 |
| PATCH | `/api/v1/accounts/{id}` | 계좌 수정, 잔액 직접 수정 | 완료 |
| DELETE | `/api/v1/accounts/{id}` | 계좌 삭제 (사용 중이면 409) | 완료 |
| GET | `/api/v1/cards` | 카드 목록 | 완료 |
| POST | `/api/v1/cards` | 카드 등록 (결제일, 결제 계좌) | 완료 |
| PATCH | `/api/v1/cards/{id}` | 카드 수정 | 완료 |
| DELETE | `/api/v1/cards/{id}` | 카드 비활성화 (이력 보존) | 완료 |
| GET | `/api/v1/categories` | 카테고리 목록 | 완료 |
| POST | `/api/v1/categories` | 카테고리 등록 (이름 중복 409) | 완료 |
| PATCH | `/api/v1/categories/{id}` | 카테고리 수정 (이름 변경 시 예산 한도도 함께 변경) | 완료 |
| DELETE | `/api/v1/categories/{id}` | 카테고리 삭제 (기본 카테고리는 409, 거래는 미분류로) | 완료 |

## 거래

| Method | Path | 설명 | 상태 |
| --- | --- | --- | --- |
| GET | `/api/v1/transactions` | 월·유형·카테고리·키워드 검색, DB 페이징(최신순, 최대 100건) | 완료 |
| POST | `/api/v1/transactions` | 거래 등록, 계좌 거래는 잔액 즉시 반영 | 완료 |
| PATCH | `/api/v1/transactions/{id}` | 거래 수정, 잔액 재반영 | 완료 |
| DELETE | `/api/v1/transactions/{id}` | 거래 삭제, 잔액 복구 | 완료 |
| PATCH | `/api/v1/transactions/{id}/category` | 카테고리만 변경 | 완료 |

## 예정 이벤트 · 반복 규칙

| Method | Path | 설명 | 상태 |
| --- | --- | --- | --- |
| GET | `/api/v1/financial-events` | 기간 내 예정 이벤트 (반복 규칙·카드 청구 자동 생성 포함) | 완료 |
| POST | `/api/v1/financial-events` | 예정 이벤트 등록 | 완료 |
| PATCH | `/api/v1/financial-events/{id}` | 수정, `PAID` 처리 시 결제 계좌 잔액 반영 | 완료 |
| DELETE | `/api/v1/financial-events/{id}` | 취소 (결제된 이벤트면 잔액 복구) | 완료 |
| POST | `/api/v1/recurring-rules` | 반복 규칙 등록 (매달·매주·매년, 종료일) | 완료 |
| GET | `/api/v1/recurring-rules` | 반복 규칙 목록 | 완료 |
| PATCH | `/api/v1/recurring-rules/{id}` | 수정, 앞으로의 예정분만 다시 맞춤 | 완료 |
| DELETE | `/api/v1/recurring-rules/{id}` | 비활성화, 앞으로의 예정분 제거 | 완료 |

## 예측 · 대시보드 · 예산

| Method | Path | 설명 | 상태 |
| --- | --- | --- | --- |
| GET | `/api/v1/dashboard` | 잔액, 고정 지출, 사용 가능 자금, 자금 날씨, 다가오는 이벤트 | 완료 |
| GET | `/api/v1/forecasts` | 날짜별 예상 잔액, 최저 잔액일 | 완료 |
| POST | `/api/v1/available-funds/simulations` | 지출일을 반영한 추가 지출 시뮬레이션 | 완료 |
| GET | `/api/v1/forecast-alerts` | 잔액 부족·다가오는 결제·예산 초과 알림 | 완료 |
| GET | `/api/v1/weather-statuses` | 날씨 상태 안내 | 완료 |
| GET | `/api/v1/budgets/monthly` | 월 예산 조회 (월 지정 가능) | 완료 |
| PUT | `/api/v1/budgets/monthly` | 월 예산 저장 | 완료 |
| GET | `/api/v1/budgets/monthly/status` | 예산 대비 실제 지출, 초과 여부 | 완료 |

## AI Agent

| Method | Path | 설명 | 상태 |
| --- | --- | --- | --- |
| POST | `/api/v1/agent/chat` | 도구를 호출해 답변 (대화 이력 반영) | 완료 — 키 없이 규칙 기반으로 동작 / 외부 AI 는 **키 필요** |
| POST | `/api/v1/agent/conversations` | 대화 생성 | 완료 |
| GET | `/api/v1/agent/conversations/{id}/messages` | 대화 메시지 | 완료 |
| GET | `/api/v1/agent/evidence/{messageId}` | 답변 당시 호출한 도구와 결과 (저장값) | 완료 |
| GET | `/api/v1/agent/suggestions` | 현재 상황에 맞춘 추천 질문 | 완료 |
| POST | `/api/v1/agent/analyze-spending` | 월별 카테고리 지출 순위 | 완료 |
| POST | `/api/v1/agent/detect-recurring` | 거래 내역에서 등록 안 된 반복 지출 탐지 | 완료 |
| POST | `/api/v1/agent/recommend-actions` | 실제 수치를 넣은 행동 추천 | 완료 |

## 외부 키가 필요한 부분

| 항목 | 현재 | 키를 넣으면 |
| --- | --- | --- |
| `POST /agent/chat` 의 외부 AI 답변 | 키만 넣으면 자동으로 외부 AI 사용(`AI_PROVIDER=auto`). 실제 키로 선택까지 확인했고, OpenAI 계정 크레딧이 없어 실제 답변은 아직 확인하지 못함. Tool Calling 흐름은 가짜 AI 서버로 검증 완료 | 크레딧 충전 후 재시작. 응답 `provider` 가 `openai-compatible` 이면 성공, `-fallback` 이면 서버 로그에서 원인 확인 |

## 운영 기능

| 항목 | 내용 |
| --- | --- |
| DB 마이그레이션 | Flyway V1(스키마) · V2(요청 로그) · V3(반복 규칙·카드 연결) · V4(인증·잔액 원장·AI 근거) · V5(토큰 무효화) |
| 인증 | Spring Security + JWT(HS256), BCrypt 비밀번호, 세션 없음, 로그아웃·비밀번호 변경 시 기존 토큰 무효화, 로그인 실패 횟수 제한 |
| CORS | `localhost:5173`, `5174`, `3000` 허용, `CORS_ALLOWED_ORIGINS` 로 추가 |
| 배포 | `Dockerfile` + `docker-compose.prod.yml` (앱 + PostgreSQL, 시간대 Asia/Seoul) |
| 요청 로그 | `api_request_logs` 에 사용자, 경로, 상태, 처리 시간 기록 (401 로 막힌 요청 포함) |
| 모니터링 | Actuator health/info/metrics/prometheus |
| 오류 응답 | 모든 오류가 `{timestamp, status, code, message, path, fields}` 형식. 500 은 내부 메시지를 노출하지 않음 |

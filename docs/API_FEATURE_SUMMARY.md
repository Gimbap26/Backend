# Money Weather API Feature Summary

## 기능별 요약

| 기능 영역 | 포함 API | 주요 내용 |
| --- | --- | --- |
| 인증 | `POST /auth/signup`, `POST /auth/login`, `POST /auth/logout`, `PATCH /auth/password`, `GET /users/me` | 이메일·비밀번호 가입과 로그인. 비밀번호는 BCrypt 로 저장하고 JWT 를 발급합니다. 로그인 실패 시 무엇이 틀렸는지 알려주지 않고, 실패가 반복되면 잠시 막습니다. 로그아웃·비밀번호 변경 시 기존 토큰을 무효로 만듭니다. |
| 자산 | `/accounts`, `/cards`, `/categories` (조회·등록·수정·삭제) | 계좌 잔액 직접 수정, 카드 결제일·결제 계좌, 카테고리 관리. 사용 중인 계좌는 삭제 불가, 카드 삭제는 비활성화, 카테고리 이름을 바꾸면 예산 한도도 따라 바뀝니다. |
| 거래 | `GET/POST /transactions`, `PATCH/DELETE /transactions/{id}` | 계좌 거래는 잔액에 즉시 반영, 수정·삭제 시 되돌림. 이체는 두 계좌 모두 반영. 카드 거래는 카드 청구 때 반영. 검색은 DB 에서 페이지 단위로 처리. |
| 예정 이벤트 | `GET/POST/PATCH/DELETE /financial-events` | 카드 대금, 고정 지출, 급여 등 앞으로의 현금 흐름. `PAID` 로 바꾸면 결제 계좌에서 빠지고 예측에서는 제외됩니다. |
| 반복 규칙 | `POST/GET/PATCH/DELETE /recurring-rules` | 매달·매주·매년, 종료일 지원. 조회하는 달마다 예정 이벤트가 자동 생성되고, 규칙을 바꾸면 앞으로의 예정분만 다시 맞춥니다. |
| 예측 | `/dashboard`, `/forecasts`, `/available-funds/simulations`, `/forecast-alerts` | 사용 가능 자금과 자금 날씨, 날짜별 예상 잔액과 최저 잔액일, 지출일을 반영한 시뮬레이션, 잔액 부족·결제 임박·예산 초과 알림. |
| 예산 | `GET/PUT /budgets/monthly`, `GET /budgets/monthly/status` | 월별 총 한도와 카테고리별 한도, 실제 지출 대비 남은 한도와 초과 여부. |
| AI Agent | `/agent/chat`, `/agent/conversations`, `/agent/evidence/{id}`, `/agent/suggestions`, `/agent/analyze-spending`, `/agent/detect-recurring`, `/agent/recommend-actions` | AI 가 8개 도구 중 필요한 것을 골라 호출하고 실제 수치로 답합니다. 호출한 도구와 결과를 답변과 함께 저장해 근거로 보여줍니다. 거래 내역에서 등록되지 않은 반복 지출을 찾아 바로 등록할 수 있는 규칙으로 제안합니다. |
| 운영 | `/actuator/health`, `/actuator/prometheus`, `api_request_logs` | 헬스 체크, 메트릭, API 요청 기록. |

## AI 도구

| 도구 | 하는 일 |
| --- | --- |
| `get_dashboard` | 잔액, 고정 지출, 사용 가능 자금, 자금 날씨 |
| `get_financial_events` | 기간 내 예정 입금·지출 |
| `get_forecast` | 날짜별 예상 잔액, 최저 잔액일 |
| `get_budget_status` | 예산 대비 지출 |
| `analyze_spending` | 월 카테고리별 지출 순위 |
| `simulate_spending` | 추가 지출 가정 시 변화 |
| `detect_recurring` | 등록되지 않은 반복 지출 |
| `get_alerts` | 잔액 부족·결제 임박·예산 초과 |

## 외부 키가 필요한 부분

| 항목 | 필요한 것 |
| --- | --- |
| 외부 AI 모델 답변 | `AI_PROVIDER=openai-compatible`, `AI_API_KEY`. 키가 없으면 규칙 기반 답변기가 같은 도구로 답합니다. |
| 파인튜닝 / 별도 모델 학습 | 학습 데이터셋, 모델 제공자, 비용·보안 정책 확정. 현재 백엔드는 답변기를 교체할 수 있는 구조까지 준비되어 있습니다. |

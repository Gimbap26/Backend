# 08. AI Agent 남은 과제 구현 완료

## 1. 문서 목적

이 문서는 AI Agent 영역에서 구현 완료된 범위와 검증 상태를 정리한다.

이 프로젝트의 Agent는 단순 문자열 응답 API가 아니라, 대화 세션을 저장하고 필요한 도구를 호출한 뒤 그 근거를 함께 보존하는 구조다. 외부 AI 키가 없거나 호출이 실패해도 규칙 기반 답변기로 대체 응답을 제공하므로, 프론트엔드 연동과 발표 시연은 외부 크레딧 없이도 가능하다.

---

## 2. 구현 대상

| 구분 | 구현 내용 | 상태 |
|---|---|---|
| 대화 생성 | `POST /api/v1/agent/conversations`로 대화방 생성 | 완료 |
| 질문 처리 | `POST /api/v1/agent/chat`에서 사용자 질문 수신 | 완료 |
| 대화 이력 반영 | 같은 대화의 최근 메시지 10개를 Agent 요청에 포함 | 완료 |
| 도구 호출 | 대시보드, 예측, 알림, 예산, 소비 분석, 시뮬레이션, 반복 지출 탐지 도구 호출 | 완료 |
| 규칙 기반 답변 | API 키 없이 질문 의도에 따라 필요한 도구를 호출해 답변 | 완료 |
| 외부 AI 연동 | OpenAI Chat Completions 호환 Tool Calling 클라이언트 구현 | 완료 |
| 폴백 처리 | 외부 AI 실패 시 규칙 기반 답변으로 대체하고 provider에 `-fallback` 표시 | 완료 |
| 근거 저장 | 답변 당시 호출한 도구, 인자, 오류, 소요 시간을 `tool_trace`로 저장 | 완료 |
| 근거 조회 | `GET /api/v1/agent/evidence/{messageId}`로 저장 근거 조회 | 완료 |
| 추천 질문 | 현재 상태에 맞는 질문 후보 4개 반환 | 완료 |
| 소비 분석 | 월별 카테고리 지출 합산 및 순위 반환 | 완료 |
| 반복 지출 탐지 | 최근 거래에서 등록되지 않은 반복 지출 후보 탐지 | 완료 |
| 행동 추천 | 잔액, 예산, 알림, 반복 지출 후보를 바탕으로 추천 행동 반환 | 완료 |

---

## 3. 처리 흐름

사용자 행동  
→ 사용자가 AI Agent 화면에서 질문을 입력한다.

프론트엔드 요청  
→ `POST /api/v1/agent/chat`

백엔드 처리  
1. `conversationId`가 없으면 새 대화 `"AI 상담"`을 생성한다.
2. 사용자 메시지를 `agent_messages`에 저장한다.
3. 같은 대화의 최근 메시지 10개를 조회한다.
4. `AgentAnswerClient`가 질문과 이력을 바탕으로 답변을 생성한다.
5. 규칙 기반 답변기는 키워드와 금액/날짜 표현을 분석해 필요한 도구를 호출한다.
6. 외부 AI 답변기는 OpenAI 호환 Tool Calling API를 사용해 필요한 도구를 선택한다.
7. 외부 AI 호출이 실패하면 규칙 기반 답변으로 폴백한다.
8. 답변, provider, source 목록, tool trace를 저장한다.
9. `conversationId`, `messageId`, `answer`, `provider`, `sources`, `toolCalls`를 반환한다.

---

## 4. API 명세

### POST `/api/v1/agent/chat`

#### Request

```json
{
  "conversationId": 1,
  "message": "이번 달 얼마 써도 돼?"
}
```

`conversationId`는 선택값이다. 생략하면 새 대화가 자동 생성된다.

#### Response

```json
{
  "conversationId": 1,
  "messageId": 2,
  "answer": "이번 달 쓸 수 있는 돈은 561,000원이고 자금 날씨는 흐림입니다. 현재 잔액 1,200,000원에서 앞으로 나갈 고정 지출 639,000원을 뺀 금액이에요. 월말까지 1일 남아 하루 약 561,000원씩 쓸 수 있습니다. 가장 빠듯한 날은 2026-09-15(561,000원)입니다.",
  "provider": "rule-based",
  "sources": [
    "get_dashboard",
    "get_forecast"
  ],
  "toolCalls": [
    {
      "name": "get_dashboard",
      "arguments": {},
      "error": null,
      "durationMs": 12
    },
    {
      "name": "get_forecast",
      "arguments": {},
      "error": null,
      "durationMs": 8
    }
  ]
}
```

---

## 5. Provider 의미

| provider | 의미 |
|---|---|
| `rule-based` | API 키 없이 규칙 기반 답변기가 답변 |
| `openai-compatible` | 외부 AI가 OpenAI 호환 Tool Calling 방식으로 답변 |
| `openai-compatible-fallback` | 외부 AI 호출 실패 후 규칙 기반 답변으로 대체 |

---

## 6. 규칙 기반 답변 의도

| Intent | 주요 조건 | 호출 도구 예시 |
|---|---|---|
| `SIMULATE` | 금액 표현 + `쓰면`, `사면`, `써도`, `결제하면` 등 | `simulate_spending` |
| `BUDGET` | `예산`, `한도`, `초과` | `get_budget_status` |
| `RECURRING` | `반복`, `구독`, `고정비`, `정기` | `detect_recurring` |
| `SPENDING` | `소비`, `어디에`, `많이 썼`, `카테고리`, `분석` | `analyze_spending` |
| `RISK` | `위험`, `부족`, `최저`, `마이너스`, `괜찮`, `언제` | `get_dashboard`, `get_forecast`, `get_alerts` |
| `AVAILABLE` | `얼마`, `여유`, `쓸 수`, `남은`, `가용` | `get_dashboard`, `get_forecast` |
| `SUMMARY` | 위 조건에 해당하지 않음 | `get_dashboard` |

---

## 7. 검증 상태

| 대상 | 검증 방식 | 결과 |
|---|---|---|
| 규칙 기반 Agent | `AgentRuleBasedTest` | 통과 |
| 외부 AI Tool Calling 형식 | `OpenAiToolCallingTest`의 가짜 AI 서버 | 통과 |
| 외부 AI 실패 폴백 | 500 응답, 도구 호출 초과 상황 테스트 | 통과 |
| 대화 저장/조회 | 통합 테스트 | 통과 |
| 답변 근거 저장/조회 | 통합 테스트 | 통과 |
| 실제 OpenAI 크레딧 사용 | 실제 계정 크레딧 필요 | 미검증 |

---

## 8. 최종 정리

AI Agent의 백엔드 구현은 MVP 기준으로 완료되었다.

현재 구현은 API 키가 없어도 규칙 기반 답변으로 동작하고, API 키를 넣으면 OpenAI 호환 Tool Calling 방식으로 전환된다. 외부 AI가 실패해도 서비스는 중단되지 않고 `openai-compatible-fallback` 응답으로 대체된다.

남은 것은 실제 OpenAI 크레딧을 사용한 답변 품질 확인이다. 이 항목은 코드 누락이 아니라 외부 계정/크레딧이 필요한 운영 검증 항목이다.

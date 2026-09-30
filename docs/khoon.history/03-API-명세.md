# 03. API 명세

엔드포인트 47개. **아래 응답 예시는 모두 실제 서버를 띄워 받은 응답**입니다(시드 데이터 기준, 긴 목록은 2개만 남기고 줄였습니다).
Swagger UI(`/swagger-ui/index.html`)에서도 같은 API 를 호출해 볼 수 있습니다. 오른쪽 위 **Authorize** 에 토큰을 넣으세요.

## 공통 규칙

| 항목 | 내용 |
| --- | --- |
| 기본 경로 | `/api/v1` |
| 인증 | `Authorization: Bearer <accessToken>`. **공개**: `POST /auth/signup`, `POST /auth/login`, `/actuator/health`, `/actuator/info`, `/actuator/prometheus`, Swagger |
| 본문 | JSON, UTF-8 |
| 날짜 | `yyyy-MM-dd` (예: `2026-09-12`), 월은 `yyyy-MM` (예: `2026-09`), 시각은 `yyyy-MM-ddTHH:mm:ss.SSS` (Asia/Seoul) |
| 금액 | 원 단위 정수 |
| CORS | `http://localhost:5173`, `5174`, `3000` 허용 (`CORS_ALLOWED_ORIGINS` 로 추가). 쿠키는 쓰지 않음 |
| 소유권 | 다른 사용자의 리소스를 건드리면 403 |
| 응답 필드 순서 | 일부 응답은 필드 순서가 호출마다 다를 수 있습니다. 순서에 의존하지 마세요 |

### 오류 응답

모든 오류는 같은 형식입니다.

```json
{"timestamp": "...", "status": 400, "code": "VALIDATION_ERROR", "message": "요청 값이 올바르지 않습니다.", "path": "/api/v1/transactions", "fields": {"amount": "0보다 커야 합니다"}}
```

| 상태 | code | 언제 |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | 필수 값 누락, 범위 위반. `fields` 에 필드별 이유 |
| 400 | `MALFORMED_REQUEST` | JSON 형식 오류, 타입 불일치(예: enum 에 없는 값) |
| 400 | `BAD_REQUEST` | 업무 규칙 위반 (예: 계좌와 카드 동시 지정, 결제 계좌 없는 결제 완료) |
| 401 | `UNAUTHORIZED` | 토큰 없음·만료·위조·무효화(로그아웃, 비밀번호 변경), 로그인 실패 |
| 403 | `FORBIDDEN` | 다른 사용자의 리소스 |
| 404 | `NOT_FOUND` | 없는 리소스·경로 |
| 405 | `METHOD_NOT_ALLOWED` | 지원하지 않는 HTTP 메서드 |
| 409 | `CONFLICT` | 상태 충돌 (예: 사용 중인 계좌 삭제, 이미 취소된 이벤트, 중복 이메일·카테고리 이름) |
| 409 | `CONCURRENT_MODIFICATION` | 같은 계좌를 동시에 수정 (다시 시도) |
| 409 | `DATA_CONFLICT` | DB 제약 위반 |
| 429 | `TOO_MANY_REQUESTS` | 로그인 실패가 너무 많음 |
| 500 | `INTERNAL_SERVER_ERROR` | 서버 오류. 내부 메시지는 싣지 않고 서버 로그에만 남김 |

실제 응답 예시:

401 — 토큰 없이 요청
응답 `401`

```jsonc
{
  "timestamp": "2026-09-30T15:36:15.3439558",
  "status": 401,
  "code": "UNAUTHORIZED",
  "message": "로그인이 필요합니다. Authorization: Bearer <토큰> 헤더를 보내세요.",
  "path": "/api/v1/dashboard",
  "fields": {}
}
```

400 — 검증 실패 (`{"merchant": "", "amount": -1}`)
응답 `400`

```jsonc
{
  "timestamp": "2026-09-30T15:36:15.380057",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "요청 값이 올바르지 않습니다.",
  "path": "/api/v1/transactions",
  "fields": {
    "amount": "0보다 커야 합니다",
    "merchant": "공백일 수 없습니다",
    "transactionType": "널이어서는 안됩니다",
    "transactionDate": "널이어서는 안됩니다"
  }
}
```

403 — 다른 사용자의 계좌 수정
응답 `403`

```jsonc
{
  "timestamp": "2026-09-30T15:36:15.4020926",
  "status": 403,
  "code": "FORBIDDEN",
  "message": "Resource owner does not match current user.",
  "path": "/api/v1/accounts/8",
  "fields": {}
}
```

409 — 사용 중인 계좌 삭제
응답 `409`

```jsonc
{
  "timestamp": "2026-09-30T15:36:15.4434284",
  "status": 409,
  "code": "CONFLICT",
  "message": "이 계좌를 사용하는 예정 이벤트, 카드 결제 계좌, 반복 규칙이(가) 있어 삭제할 수 없습니다. 연결을 먼저 바꾸세요.",
  "path": "/api/v1/accounts/8",
  "fields": {}
}
```

429 — 로그인 5번 실패 후
응답 `429`

```jsonc
{
  "timestamp": "2026-09-30T15:36:15.7955962",
  "status": 429,
  "code": "TOO_MANY_REQUESTS",
  "message": "로그인 실패가 너무 많습니다. 15분 뒤에 다시 시도하세요.",
  "path": "/api/v1/auth/login",
  "fields": {}
}
```

> 검증 메시지(`"0보다 커야 합니다"`)는 서버의 언어 설정을 따릅니다. 서버 환경에 따라 영어로 나올 수 있으니, 프론트는 `fields` 의 **키**로 판단하고 문구는 참고만 하세요.

### enum 값

| enum | 값 |
| --- | --- |
| `TransactionType` | `INCOME`, `EXPENSE`, `TRANSFER` |
| `CategoryType` | `INCOME`, `EXPENSE`, `TRANSFER` |
| `Direction` | `INFLOW`(들어옴), `OUTFLOW`(나감) |
| `EventType` | `CARD_BILL`, `TELECOM`, `SUBSCRIPTION`, `LOAN`, `SALARY`, `TRANSFER`, `ETC` |
| `EventStatus` | `SCHEDULED`(예정), `PAID`(결제 완료), `CANCELED`(취소) |
| `RecurrenceType` | `MONTHLY`, `WEEKLY`, `YEARLY` |
| `WeatherStatus` | `SUNNY`, `CLOUDY`, `RAINY`, `STORM` |
| `RiskLevel` | `GOOD`, `CAUTION`, `DANGER` |
| `UserStatus` | `ACTIVE`, `INACTIVE` |

---

## 1. 인증 · 사용자

### `POST /auth/signup` — 회원가입 (공개)

| 필드 | 타입 | 필수 | 규칙 |
| --- | --- | --- | --- |
| `email` | string | ✔ | 이메일 형식. 대소문자·앞뒤 공백 무시하고 저장. 중복이면 409 |
| `password` | string | ✔ | 8~100자 |
| `name` | string | ✔ | 50자 이하 |

가입하면 기준 월은 이번 달, 기본 카테고리 4개(식비/카페, 교통, 급여, 구독)가 만들어지고 **바로 쓸 수 있는 토큰**을 돌려줍니다. 사용자 ID 는 1000번부터 발급됩니다.

요청

```jsonc
{
  "email": "new@example.com",
  "password": "password123",
  "name": "김밥"
}
```
응답 `200`

```jsonc
{
  "userId": 1000,
  "email": "new@example.com",
  "name": "김밥",
  "tokenType": "Bearer",
  "accessToken": "<JWT>",
  "expiresIn": 86400
}
```

### `POST /auth/login` — 로그인 (공개)

`email`, `password` 필수. 틀리면 이메일·비밀번호 중 무엇이 틀렸는지 구분하지 않고 401.
**15분 안에 같은 이메일로 5번, 같은 IP 에서 20번 넘게 실패하면 429** (맞는 비밀번호도 막힘). 성공하면 그 이메일의 실패 횟수는 초기화됩니다.

요청

```jsonc
{
  "email": "demo@moneyweather.dev",
  "password": "demo1234!"
}
```
응답 `200`

```jsonc
{
  "userId": 1,
  "tokenType": "Bearer",
  "accessToken": "<JWT>",
  "expiresIn": 86400
}
```

### `POST /auth/logout` — 로그아웃

본문 없음. **이 계정으로 발급된 모든 토큰**(다른 기기 포함)이 무효가 됩니다.

응답 `200`

```jsonc
{
  "at": "2026-09-30T15:36:15.2470181",
  "loggedOut": true
}
```

### `PATCH /auth/password` — 비밀번호 변경

| 필드 | 필수 | 규칙 |
| --- | --- | --- |
| `currentPassword` | ✔ | 틀리면 **400** (401 이 아님 — 로그인이 풀린 것으로 오해하지 않도록) |
| `newPassword` | ✔ | 8~100자, 현재와 같으면 400 |

다른 기기의 토큰은 무효가 되고, **지금 기기에서 쓸 새 토큰**을 돌려줍니다. 응답의 토큰으로 교체하세요.

요청

```jsonc
{
  "currentPassword": "password123",
  "newPassword": "newpassword456"
}
```
응답 `200`

```jsonc
{
  "tokenType": "Bearer",
  "accessToken": "<JWT>",
  "expiresIn": 86400
}
```

### `GET /users/me` — 내 정보

응답 `200`

```jsonc
{
  "userId": 1,
  "name": "테스트 사용자",
  "email": "demo@moneyweather.dev",
  "baseMonth": "2026-09",
  "status": "ACTIVE"
}
```

### `POST /dev/seed` — 개발용 전체 초기화

⚠️ **모든 사용자의 데이터를 지우고** 데모 계정(`demo@moneyweather.dev` / `demo1234!`)과 샘플 데이터를 다시 만듭니다. 운영(`postgres` 프로필)에서는 꺼져 있어 404. 호출한 뒤에는 기존 토큰이 모두 무효이니 다시 로그인하세요.

| 필드 | 필수 | 설명 |
| --- | --- | --- |
| `userId` | ✔ | 데모 사용자 ID (보통 1) |
| `baseMonth` | ✔ | 기준 월 `yyyy-MM` |
| `reset` | | `false` 면 이미 있을 때 409 |

요청

```jsonc
{
  "userId": 1,
  "baseMonth": "2026-09",
  "reset": true
}
```
응답 `200`

```jsonc
{
  "created": {
    "cards": 2,
    "accounts": 3,
    "recurringRules": 1,
    "transactions": 15,
    "financialEvents": 2
  },
  "userId": 1,
  "baseMonth": "2026-09"
}
```

---

## 2. 계좌 · 카드 · 카테고리

### `GET /accounts` — 계좌 목록

`?includedOnly=true` 면 자산 포함 계좌만. `totalBalance` 는 **자산 포함 계좌만의 합계**(대시보드의 현재 잔액과 같음), `allAccountsBalance` 는 전체 합계.

응답 `200`

```jsonc
{
  "accounts": [
    {
      "accountId": 4,
      "bankName": "하나은행",
      "accountName": "생활비 통장",
      "balance": 800000,
      "purpose": "생활비",
      "includedInAssets": true
    },
    {
      "accountId": 5,
      "bankName": "국민은행",
      "accountName": "비상금",
      "balance": 400000,
      "purpose": "예비비",
      "includedInAssets": true
    },
    // ... 외 1개
  ],
  "allAccountsBalance": 2200000,
  "totalBalance": 1200000
}
```

### `POST /accounts` — 계좌 등록

| 필드 | 필수 | 기본값 |
| --- | --- | --- |
| `bankName`, `accountName` | ✔ | |
| `balance` | | 0 |
| `purpose` | | null |
| `includedInAssets` | | true (false 면 사용 가능 자금 계산에서 빠짐. 예: 적금) |

요청

```jsonc
{
  "bankName": "토스뱅크",
  "accountName": "파킹 통장",
  "balance": 50000
}
```
응답 `200`

```jsonc
{
  "accountId": 7,
  "bankName": "토스뱅크",
  "accountName": "파킹 통장",
  "balance": 50000,
  "purpose": null,
  "includedInAssets": true
}
```

### `PATCH /accounts/{accountId}` — 계좌 수정 · 잔액 직접 수정

위 필드를 부분 수정. `balance` 를 보내면 **그 값으로 덮어씁니다**(실제 통장과 맞출 때). 거래·결제로 인한 증감은 자동이므로 평소에는 쓸 필요 없습니다. 동시에 같은 계좌를 수정하면 한쪽은 409 `CONCURRENT_MODIFICATION`.

요청

```jsonc
{
  "balance": 60000
}
```
응답 `200`

```jsonc
{
  "accountId": 7,
  "bankName": "토스뱅크",
  "accountName": "파킹 통장",
  "balance": 60000,
  "purpose": null,
  "includedInAssets": true
}
```

### `DELETE /accounts/{accountId}` — 계좌 삭제

거래·예정 이벤트·카드 결제 계좌·반복 규칙 중 하나라도 이 계좌를 쓰면 **409** (메시지에 어디서 쓰는지 나옴). 아니면 실제 삭제.

응답 `200`

```jsonc
{
  "accountId": 7,
  "deletedAt": "2026-09-30T15:36:13.5532136"
}
```

### `GET /cards` — 카드 목록

`?active=true|false` 로 필터.

응답 `200`

```jsonc
{
  "cards": [
    {
      "cardId": 3,
      "cardCompany": "신한카드",
      "cardName": "Deep Dream",
      "paymentDay": 5,
      "active": true,
      "paymentAccountId": 4
    },
    {
      "cardId": 4,
      "cardCompany": "현대카드",
      "cardName": "Zero",
      "paymentDay": 15,
      "active": true,
      "paymentAccountId": 4
    }
  ]
}
```

### `POST /cards` — 카드 등록

| 필드 | 필수 | 규칙 |
| --- | --- | --- |
| `cardCompany`, `cardName` | ✔ | |
| `paymentDay` | ✔ | 1~31. 그 달에 없는 날(예: 31일)은 말일로 |
| `paymentAccountId` | | 카드 대금이 빠질 계좌. 청구 이벤트가 이 계좌를 물려받음 |

요청

```jsonc
{
  "cardCompany": "KB국민카드",
  "cardName": "톡톡",
  "paymentDay": 25,
  "paymentAccountId": 4
}
```
응답 `200`

```jsonc
{
  "cardId": 5,
  "cardCompany": "KB국민카드",
  "cardName": "톡톡",
  "paymentDay": 25,
  "active": true,
  "paymentAccountId": 4
}
```

### `PATCH /cards/{cardId}` — 카드 수정

`cardCompany`, `cardName`, `paymentDay`, `active`, `paymentAccountId` 부분 수정. 아직 지나지 않은 청구 이벤트는 다음 조회 때 새 값으로 맞춰집니다.

요청

```jsonc
{
  "paymentDay": 27
}
```
응답 `200`

```jsonc
{
  "cardId": 5,
  "cardCompany": "KB국민카드",
  "cardName": "톡톡",
  "paymentDay": 27,
  "active": true,
  "paymentAccountId": 4
}
```

### `DELETE /cards/{cardId}` — 카드 비활성화

과거 거래·청구 이력 때문에 **지우지 않고 비활성화**합니다. 비활성 카드는 새 청구가 생기지 않습니다.

응답 `200`

```jsonc
{
  "active": false,
  "cardId": 5,
  "deactivatedAt": "2026-09-30T15:36:13.6322984"
}
```

### `GET /categories` — 카테고리 목록

`?categoryType=EXPENSE` 등으로 필터. `systemDefault=true` 는 기본 카테고리(삭제 불가).

응답 `200`

```jsonc
{
  "categories": [
    {
      "categoryId": 5,
      "name": "식비/카페",
      "categoryType": "EXPENSE",
      "systemDefault": true
    },
    {
      "categoryId": 6,
      "name": "교통",
      "categoryType": "EXPENSE",
      "systemDefault": true
    },
    // ... 외 2개
  ]
}
```

### `POST /categories` — 카테고리 등록

`name`(필수, 같은 사용자 안에서 중복이면 409), `categoryType`(필수).

요청

```jsonc
{
  "name": "취미",
  "categoryType": "EXPENSE"
}
```
응답 `200`

```jsonc
{
  "categoryId": 13,
  "name": "취미",
  "categoryType": "EXPENSE",
  "systemDefault": false
}
```

### `PATCH /categories/{categoryId}` — 카테고리 수정

`name`, `categoryType` 부분 수정. **이름을 바꾸면 이 카테고리의 예산 한도 이름도 함께 바뀝니다**(예산이 카테고리를 이름으로 참조하기 때문).

요청

```jsonc
{
  "name": "취미/여가"
}
```
응답 `200`

```jsonc
{
  "categoryId": 13,
  "name": "취미/여가",
  "categoryType": "EXPENSE",
  "systemDefault": false
}
```

### `DELETE /categories/{categoryId}` — 카테고리 삭제

기본 카테고리는 409. 삭제하면 그 카테고리의 거래는 **미분류**가 되고, 걸려 있던 예산 한도도 삭제됩니다. 응답에 영향받은 건수가 나옵니다.

응답 `200`

```jsonc
{
  "deletedAt": "2026-09-30T15:36:13.7136376",
  "removedBudgetLimits": 0,
  "uncategorizedTransactions": 0,
  "categoryId": 13
}
```

---

## 3. 거래

### `GET /transactions` — 거래 검색

| 파라미터 | 필수 | 설명 |
| --- | --- | --- |
| `month` | ✔ | `yyyy-MM` |
| `transactionType` | | `INCOME` / `EXPENSE` / `TRANSFER` |
| `categoryId` | | |
| `keyword` | | 상호명 포함 검색, 대소문자 무시. `%`, `_` 도 글자 그대로 검색 |
| `page` | | 0부터, 기본 0 |
| `size` | | 1~100, 기본 20 |

DB 에서 페이지 단위로 읽고 **최신순**입니다. `totalAmount` 는 현재 페이지가 아니라 **조건에 맞는 전체 거래의 합계**(수입·지출 구분 없이 금액 합)입니다.

응답 `200`

```jsonc
{
  "month": "2026-09",
  "totalAmount": 2523000,
  "items": [
    {
      "transactionId": 21,
      "date": "2026-09-25",
      "merchant": "회사 급여",
      "amount": 2500000,
      "transactionType": "INCOME",
      "category": "급여",
      "categoryId": 7,
      "cardId": null,
      "accountId": null,
      "transferAccountId": null
    },
    {
      "transactionId": 31,
      "date": "2026-09-20",
      "merchant": "편의점",
      "amount": 15000,
      "transactionType": "EXPENSE",
      "category": "식비/카페",
      "categoryId": 5,
      "cardId": null,
      "accountId": 4,
      "transferAccountId": null
    },
    // ... 외 2개
  ],
  "page": 0,
  "size": 20,
  "totalElements": 4,
  "totalPages": 1
}
```

### `POST /transactions` — 거래 등록

| 필드 | 필수 | 설명 |
| --- | --- | --- |
| `transactionDate` | ✔ | |
| `merchant` | ✔ | 상호명 |
| `amount` | ✔ | 양수 |
| `transactionType` | ✔ | |
| `categoryId` | | 없으면 미분류 |
| `accountId` | | 계좌 거래 → **잔액 즉시 반영** |
| `cardId` | | 카드 거래 → 잔액은 카드 청구 때 반영. `accountId` 와 동시 지정 불가(400) |
| `transferAccountId` | | `TRANSFER` 일 때 받는 계좌. 이체는 `accountId` 와 이것이 모두 필요하고 서로 달라야 함 |

요청

```jsonc
{
  "transactionDate": "2026-09-20",
  "merchant": "편의점",
  "amount": 12000,
  "transactionType": "EXPENSE",
  "categoryId": 5,
  "accountId": 4
}
```
응답 `200`

```jsonc
{
  "transactionId": 31,
  "date": "2026-09-20",
  "merchant": "편의점",
  "amount": 12000,
  "transactionType": "EXPENSE",
  "category": "식비/카페",
  "categoryId": 5,
  "cardId": null,
  "accountId": 4,
  "transferAccountId": null
}
```

### `PATCH /transactions/{transactionId}` — 거래 수정

위 필드 부분 수정. 잔액은 이전 효과를 되돌리고 새 효과를 적용합니다. `cardId` 를 보내면 `accountId` 가, `accountId` 를 보내면 `cardId` 가 비워집니다. 이체가 아닌 유형으로 바꾸면 `transferAccountId` 도 비워집니다.

요청

```jsonc
{
  "amount": 15000
}
```
응답 `200`

```jsonc
{
  "transactionId": 31,
  "date": "2026-09-20",
  "merchant": "편의점",
  "amount": 15000,
  "transactionType": "EXPENSE",
  "category": "식비/카페",
  "categoryId": 5,
  "cardId": null,
  "accountId": 4,
  "transferAccountId": null
}
```

### `DELETE /transactions/{transactionId}` — 거래 삭제

잔액 효과를 되돌린 뒤 삭제합니다.

응답 `200`

```jsonc
{
  "deletedAt": "2026-09-30T15:36:13.8659138",
  "transactionId": 31
}
```

### `PATCH /transactions/{transactionId}/category` — 카테고리만 변경

`categoryId` 필수.

응답 `200`

```jsonc
{
  "categoryName": "식비/카페",
  "updatedAt": "2026-09-30T15:36:13.800404",
  "transactionId": 31,
  "categoryId": 5
}
```

---

## 4. 예정 이벤트 · 반복 규칙

### `GET /financial-events` — 예정 이벤트 조회

`from`, `to` 필수(`from ≤ to`). `direction`, `eventType`, `status` 로 필터. **조회 전에 그 기간의 반복 규칙·카드 청구 이벤트를 자동 생성**합니다. 자동 생성된 이벤트는 `recurringRuleId` 또는 `cardId` 로 출처를 알 수 있습니다.

응답 `200`

```jsonc
{
  "events": [
    {
      "eventId": 6,
      "eventDate": "2026-09-05",
      "title": "Deep Dream 결제",
      "amount": 520000,
      "direction": "OUTFLOW",
      "eventType": "CARD_BILL",
      "status": "SCHEDULED",
      "fixed": true,
      "accountId": 4,
      "recurringRuleId": null,
      "cardId": 3
    },
    {
      "eventId": 5,
      "eventDate": "2026-09-10",
      "title": "통신비",
      "amount": 80000,
      "direction": "OUTFLOW",
      "eventType": "TELECOM",
      "status": "SCHEDULED",
      "fixed": true,
      "accountId": 4,
      "recurringRuleId": 2,
      "cardId": null
    },
    // ... 외 2개
  ],
  "from": "2026-09-01",
  "to": "2026-09-30"
}
```

### `POST /financial-events` — 예정 이벤트 등록

| 필드 | 필수 | 설명 |
| --- | --- | --- |
| `eventDate`, `title` | ✔ | |
| `amount` | ✔ | 양수 |
| `direction`, `eventType` | ✔ | |
| `fixed` | | `true` 면 고정 지출로 사용 가능 자금에서 미리 뺌 |
| `accountId` | | 결제 완료 시 잔액을 바꿀 계좌 |

항상 `SCHEDULED` 로 만들어집니다.

요청

```jsonc
{
  "eventDate": "2026-09-28",
  "title": "관리비",
  "amount": 150000,
  "direction": "OUTFLOW",
  "eventType": "ETC",
  "fixed": true,
  "accountId": 4
}
```
응답 `200`

```jsonc
{
  "eventId": 7,
  "eventDate": "2026-09-28",
  "title": "관리비",
  "amount": 150000,
  "direction": "OUTFLOW",
  "eventType": "ETC",
  "status": "SCHEDULED",
  "fixed": true,
  "accountId": 4,
  "recurringRuleId": null,
  "cardId": null
}
```

### `PATCH /financial-events/{eventId}` — 이벤트 수정 · 결제 완료 처리

`eventDate`, `title`, `amount`, `status`, `fixed`, `accountId` 부분 수정.
- `status: "PAID"` → 결제 계좌 잔액 반영. 결제 계좌(요청 또는 이벤트의 `accountId`)가 없으면 400
- `PAID` 에서 다른 상태로 → 잔액 되돌림
- 결제된 이벤트의 금액을 바꾸면 잔액도 차액만큼 맞춰짐

요청

```jsonc
{
  "status": "PAID"
}
```
응답 `200`

```jsonc
{
  "eventId": 7,
  "eventDate": "2026-09-28",
  "amount": 150000,
  "status": "PAID",
  "accountId": 4,
  "updatedAt": "2026-09-30T15:36:13.9411307"
}
```

### `DELETE /financial-events/{eventId}` — 이벤트 취소

지우지 않고 `CANCELED` 로 바꿉니다. 결제 완료였다면 잔액을 되돌립니다. 이미 취소됐으면 409.

응답 `200`

```jsonc
{
  "canceledAt": "2026-09-30T15:36:13.950446",
  "status": "CANCELED",
  "eventId": 7
}
```

### `POST /recurring-rules` — 반복 규칙 등록

| 필드 | 필수 | 설명 |
| --- | --- | --- |
| `title` | ✔ | |
| `recurrenceType` | ✔ | `MONTHLY`: `dayOfMonth` 일마다 / `WEEKLY`: `startDate` 부터 7일마다 / `YEARLY`: `startDate` 와 같은 월·일 |
| `dayOfMonth` | `MONTHLY` 면 사실상 필수 | 1~31, 없는 날은 말일. **빠뜨려도 오류가 나지 않고 이벤트가 안 생깁니다** ([07](07-현황과-남은-과제.md)) |
| `startDate` | ✔ | |
| `endDate` | | 이 날 이후로는 만들지 않음 |
| `amount` | ✔ | 양수 |
| `eventType`, `direction` | ✔ | |
| `accountId` | | 생성되는 이벤트가 물려받음 |

시작 월의 이벤트를 즉시 만들고, 나머지 달은 조회할 때 만듭니다. `generatedEvents` 는 즉시 만든 개수입니다. `detect-recurring` 의 `suggestedRule` 을 그대로 보내도 됩니다.

요청

```jsonc
{
  "title": "헬스장",
  "recurrenceType": "MONTHLY",
  "dayOfMonth": 12,
  "startDate": "2026-09-01",
  "amount": 50000,
  "eventType": "SUBSCRIPTION",
  "direction": "OUTFLOW",
  "accountId": 4
}
```
응답 `200`

```jsonc
{
  "recurringRuleId": 3,
  "title": "헬스장",
  "generatedEvents": 1,
  "recurrenceType": "MONTHLY"
}
```

### `GET /recurring-rules` — 반복 규칙 목록

응답 `200`

```jsonc
{
  "recurringRules": [
    {
      "recurringRuleId": 2,
      "title": "통신비",
      "recurrenceType": "MONTHLY",
      "dayOfMonth": 10,
      "startDate": "2026-09-01",
      "endDate": null,
      "amount": 80000,
      "eventType": "TELECOM",
      "direction": "OUTFLOW",
      "active": true,
      "accountId": 4
    },
    {
      "recurringRuleId": 3,
      "title": "헬스장",
      "recurrenceType": "MONTHLY",
      "dayOfMonth": 12,
      "startDate": "2026-09-01",
      "endDate": null,
      "amount": 50000,
      "eventType": "SUBSCRIPTION",
      "direction": "OUTFLOW",
      "active": true,
      "accountId": 4
    }
  ]
}
```

### `PATCH /recurring-rules/{id}` — 반복 규칙 수정

위 필드와 `active` 부분 수정. 오늘 이후의 `SCHEDULED` 이벤트를 지우고 새 값으로 다시 만듭니다. 지난 이벤트와 결제된 이벤트는 그대로입니다.

요청

```jsonc
{
  "amount": 55000
}
```
응답 `200`

```jsonc
{
  "recurringRuleId": 3,
  "title": "헬스장",
  "recurrenceType": "MONTHLY",
  "dayOfMonth": 12,
  "startDate": "2026-09-01",
  "endDate": null,
  "amount": 55000,
  "eventType": "SUBSCRIPTION",
  "direction": "OUTFLOW",
  "active": true,
  "accountId": 4
}
```

### `DELETE /recurring-rules/{id}` — 반복 규칙 비활성화

지우지 않고 `active=false`. 오늘 이후의 `SCHEDULED` 이벤트를 지웁니다.

응답 `200`

```jsonc
{
  "active": false,
  "recurringRuleId": 3,
  "deletedAt": "2026-09-30T15:36:14.0893977"
}
```

---

## 5. 예측 · 대시보드 · 알림

### `GET /dashboard` — 홈 요약

`baseDate`, `targetDate` 선택(기본: 기준 월 1일~말일). `nextEvents` 는 기간 안의 아직 내지 않은 이벤트를 날짜순으로 최대 5개. 계산 규칙은 [02](02-구조와-핵심-개념.md#사용-가능-자금과-자금-날씨).

응답 `200`

```jsonc
{
  "weather": "CLOUDY",
  "riskSummary": {
    "message": "이번 달 자금 흐름이 안정적입니다.",
    "level": "GOOD"
  },
  "nextEvents": [
    {
      "date": "2026-09-05",
      "direction": "OUTFLOW",
      "title": "Deep Dream 결제",
      "amount": 520000
    },
    {
      "date": "2026-09-10",
      "direction": "OUTFLOW",
      "title": "통신비",
      "amount": 80000
    },
    // ... 외 2개
  ],
  "fixedOutflows": 639000,
  "availableAmount": 561000,
  "currentBalance": 1200000
}
```

### `GET /forecasts` — 날짜별 예상 잔액

`from`, `to` 필수. `timeline` 은 이벤트가 있는 날만 담습니다. `weatherStatus` 는 **최저 잔액** 기준입니다.

응답 `200`

```jsonc
{
  "timeline": [
    {
      "expectedBalance": 680000,
      "date": "2026-09-05",
      "eventTitle": "Deep Dream 결제",
      "eventAmount": -520000
    },
    {
      "expectedBalance": 600000,
      "date": "2026-09-10",
      "eventTitle": "통신비",
      "eventAmount": -80000
    },
    // ... 외 2개
  ],
  "weatherStatus": "CLOUDY",
  "minimumExpectedBalance": 561000,
  "from": "2026-09-01",
  "to": "2026-09-30",
  "minimumBalanceDate": "2026-09-15"
}
```

### `POST /available-funds/simulations` — 추가 지출 시뮬레이션

| 필드 | 필수 | 설명 |
| --- | --- | --- |
| `spendingDate` | ✔ | 가상 지출일. `targetDate` 보다 늦으면 400 |
| `amount` | ✔ | 양수 |
| `targetDate` | ✔ | 영향을 볼 마지막 날 |
| `title` | | 기본 "추가 지출" |

저장하지 않습니다. 기간은 `spendingDate` 가 속한 달의 1일 ~ `targetDate`. `availableAmount` 는 날짜와 상관없이 같은 만큼 줄지만, `minimumBalance` 는 **지출일에 따라 달라집니다**(급여일 전에 쓰면 더 낮아짐).

요청

```jsonc
{
  "spendingDate": "2026-09-12",
  "amount": 250000,
  "targetDate": "2026-09-30",
  "title": "에어팟"
}
```
응답 `200`

```jsonc
{
  "title": "에어팟",
  "spendingDate": "2026-09-12",
  "targetDate": "2026-09-30",
  "before": {
    "availableAmount": 561000,
    "weather": "CLOUDY",
    "minimumBalance": 561000,
    "minimumBalanceDate": "2026-09-15",
    "forecastWeather": "CLOUDY"
  },
  "after": {
    "availableAmount": 311000,
    "weather": "CLOUDY",
    "minimumBalance": 311000,
    "minimumBalanceDate": "2026-09-15",
    "forecastWeather": "CLOUDY"
  },
  "difference": -250000,
  "message": "2026-09-12에 에어팟(250,000원)을 쓰면 2026-09-30까지 쓸 수 있는 돈이 561,000원에서 311,000원으로 줄고, 날씨는 흐림 → 흐림입니다. 가장 잔액이 적은 날은 2026-09-15(311,000원)입니다."
}
```

### `GET /forecast-alerts` — 위험 알림

`from`, `to` 선택(기본: 기준 월). 심각도(DANGER → CAUTION → INFO) → 날짜순.

| type | severity | 조건 |
| --- | --- | --- |
| `NEGATIVE_BALANCE` | DANGER | 예상 잔액이 0 미만이 되는 첫날 |
| `LOW_BALANCE` | CAUTION | 최저 예상 잔액 < 300,000 (마이너스가 아닐 때) |
| `UPCOMING_BILL` | INFO | **오늘부터** 7일 안의 고정 지출·카드 청구 (조회 기간과 무관) |
| `BUDGET_EXCEEDED` | DANGER | 카테고리 지출 > 한도 (`amount` = 초과액) |
| `BUDGET_NEAR_LIMIT` | CAUTION | 카테고리 지출 ≥ 한도의 80% (`amount` = 남은 한도) |

응답 `200`

```jsonc
{
  "from": "2026-09-01",
  "to": "2026-09-30",
  "count": 0,
  "alerts": []
}
```

알림이 있을 때의 모양: `{"type": "LOW_BALANCE", "severity": "CAUTION", "date": "2026-09-15", "title": "잔액 낮음 예상", "message": "2026-09-15에 잔액이 61,000원까지 내려갈 것으로 예상됩니다.", "amount": 61000}`

### `GET /weather-statuses` — 날씨 안내

응답 `200`

```jsonc
{
  "statuses": [
    {
      "label": "맑음",
      "weather": "SUNNY",
      "description": "여유 자금이 충분합니다."
    },
    {
      "label": "흐림",
      "weather": "CLOUDY",
      "description": "예정 지출을 확인하세요."
    },
    // ... 외 2개
  ]
}
```

---

## 6. 예산

### `GET /budgets/monthly` — 월 예산

`?month=yyyy-MM`, 생략하면 가장 최근 달. 없으면 404.

응답 `200`

```jsonc
{
  "month": "2026-09",
  "categoryLimits": {
    "교통": 100000,
    "구독": 80000,
    "식비/카페": 350000
  },
  "totalLimit": 530000
}
```

### `PUT /budgets/monthly` — 월 예산 저장

| 필드 | 필수 | 설명 |
| --- | --- | --- |
| `month` | ✔ | `yyyy-MM`. 있으면 덮어쓰고 없으면 만듦 |
| `categoryLimits` | ✔ | `{"카테고리 이름": 한도}`. **카테고리 이름으로 연결**됩니다. 기존 한도는 모두 교체 |
| `totalLimit` | ✔ | 양수 |

요청

```jsonc
{
  "month": "2026-10",
  "categoryLimits": {
    "식비/카페": 300000,
    "교통": 90000
  },
  "totalLimit": 390000
}
```
응답 `200`

```jsonc
{
  "month": "2026-10",
  "categoryLimits": {
    "교통": 90000,
    "식비/카페": 300000
  },
  "totalLimit": 390000
}
```

### `GET /budgets/monthly/status` — 예산 대비 지출

`?month=` 선택. 한도가 있는 카테고리와 실제 지출이 있는 카테고리를 모두 보여줍니다(한도 없는 카테고리는 `limit: null`). 카테고리 없는 지출은 `미분류`.

응답 `200`

```jsonc
{
  "month": "2026-09",
  "totalLimit": 530000,
  "totalSpent": 8000,
  "totalRemaining": 522000,
  "totalExceeded": false,
  "categories": [
    {
      "category": "교통",
      "limit": 100000,
      "spent": 1500,
      "remaining": 98500,
      "exceeded": false
    },
    {
      "category": "구독",
      "limit": 80000,
      "spent": 0,
      "remaining": 80000,
      "exceeded": false
    },
    // ... 외 1개
  ]
}
```

---

## 7. AI Agent

어느 답변기로 답했는지는 `provider` 로 확인합니다: `rule-based`(키 없음), `openai-compatible`(외부 AI), `openai-compatible-fallback`(외부 AI 실패 → 규칙 기반이 대신 답함).

### `POST /agent/conversations` — 대화 생성

본문 선택: `{"title": "..."}`(기본 "새 대화").

요청

```jsonc
{
  "title": "이번 달 점검"
}
```
응답 `200`

```jsonc
{
  "conversationId": 1,
  "title": "이번 달 점검",
  "createdAt": "2026-09-30T15:36:14.6723497"
}
```

### `POST /agent/chat` — 질문

| 필드 | 필수 | 설명 |
| --- | --- | --- |
| `conversationId` | | 생략하면 새 대화("AI 상담")를 만듦 |
| `message` | ✔ | 2,000자 이하 |

같은 대화의 최근 메시지 10개가 함께 전달됩니다. `sources` 는 실제로 호출한 도구, `toolCalls` 는 인자·오류·소요 시간(결과 본문은 `evidence` 로).

요청

```jsonc
{
  "conversationId": 1,
  "message": "이번 달 얼마 써도 돼?"
}
```
응답 `200`

```jsonc
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

### `GET /agent/conversations/{id}/messages` — 대화 메시지

응답 `200`

```jsonc
{
  "conversationId": 1,
  "messages": [
    {
      "messageId": 1,
      "role": "USER",
      "content": "이번 달 얼마 써도 돼?",
      "sources": [],
      "createdAt": "2026-09-30T15:36:14.716008"
    },
    {
      "messageId": 2,
      "role": "ASSISTANT",
      "content": "이번 달 쓸 수 있는 돈은 561,000원이고 자금 날씨는 흐림입니다. 현재 잔액 1,200,000원에서 앞으로 나갈 고정 지출 639,000원을 뺀 금액이에요. 월말까지 1일 남아 하루 약 561,000원씩 쓸 수 있습니다. 가장 빠듯한 날은 2026-09-15(561,000원)입니다.",
      "sources": [
        "get_dashboard",
        "get_forecast"
      ],
      "createdAt": "2026-09-30T15:36:14.737765"
    }
  ]
}
```

### `GET /agent/evidence/{messageId}` — 답변 근거

답변 **당시** 호출한 도구의 인자와 결과를 저장된 그대로 돌려줍니다(다시 계산하지 않음). 결과가 너무 크면(10만 자 초과) 결과 본문을 빼고 `truncated: true`.

응답 `200`

```jsonc
{
  "messageId": 2,
  "role": "ASSISTANT",
  "content": "이번 달 쓸 수 있는 돈은 561,000원이고 자금 날씨는 흐림입니다. 현재 잔액 1,200,000원에서 앞으로 나갈 고정 지출 639,000원을 뺀 금액이에요. 월말까지 1일 남아 하루 약 561,000원씩 쓸 수 있습니다. 가장 빠듯한 날은 2026-09-15(561,000원)입니다.",
  "recordedAt": "2026-09-30T15:36:14.737765",
  "sources": [
    "get_dashboard",
    "get_forecast"
  ],
  "provider": "rule-based",
  "truncated": false,
  "toolCalls": [
    {
      "name": "get_dashboard",
      "arguments": {},
      "result": {
        "weather": "CLOUDY",
        "riskSummary": {
          "message": "이번 달 자금 흐름이 안정적입니다.",
          "level": "GOOD"
        },
        "nextEvents": [
          {
            "date": "2026-09-05",
            "direction": "OUTFLOW",
            "title": "Deep Dream 결제",
            "amount": 520000
          },
          {
            "date": "2026-09-10",
            "direction": "OUTFLOW",
            "title": "통신비",
            "amount": 80000
          },
          // ... 외 2개
        ],
        "fixedOutflows": 639000,
        "availableAmount": 561000,
        "currentBalance": 1200000
      },
      "error": null,
      "durationMs": 12
    },
    {
      "name": "get_forecast",
      "arguments": {},
      "result": {
        "timeline": [
          {
            "expectedBalance": 680000,
            "date": "2026-09-05",
            "eventTitle": "Deep Dream 결제",
            "eventAmount": -520000
          },
          {
            "expectedBalance": 600000,
            "date": "2026-09-10",
            "eventTitle": "통신비",
            "eventAmount": -80000
          },
          // ... 외 2개
        ],
        "weatherStatus": "CLOUDY",
        "minimumExpectedBalance": 561000,
        "from": "2026-09-01",
        "to": "2026-09-30",
        "minimumBalanceDate": "2026-09-15"
      },
      "error": null,
      "durationMs": 8
    }
  ]
}
```

### `GET /agent/suggestions` — 추천 질문

지금 상황(잔액이 낮아지는 날, 예산 초과·임박, 등록 안 된 반복 지출)에 맞춘 질문 4개.

응답 `200`

```jsonc
{
  "suggestions": [
    "넷플릭스를 반복 지출로 등록할까?",
    "이번 달 하루에 얼마까지 써도 돼?",
    // ... 외 2개
  ]
}
```

### `POST /agent/analyze-spending` — 소비 분석

`?month=` 선택(기본 기준 월). `share` 는 비율(%).

응답 `200`

```jsonc
{
  "month": "2026-09",
  "totalExpense": 8000,
  "byCategory": {
    "교통": 1500,
    "식비/카페": 6500
  },
  "ranking": [
    {
      "amount": 6500,
      "share": 81.3,
      "category": "식비/카페"
    },
    {
      "amount": 1500,
      "share": 18.8,
      "category": "교통"
    }
  ]
}
```

### `POST /agent/detect-recurring` — 반복 지출 탐지

최근 6개월 거래에서 **3개월 이상** 같은 상호로 나갔고 금액 변동이 20% 이하이며 **아직 활성 반복 규칙이 없는** 지출. `confidence`(0~1)는 반복 개월 수·금액 안정성·결제일 안정성으로 계산합니다. `suggestedRule` 은 `POST /recurring-rules` 에 그대로 보낼 수 있습니다.

응답 `200`

```jsonc
{
  "candidates": [
    {
      "merchant": "넷플릭스",
      "monthsObserved": 3,
      "months": [
        "2026-06",
        "2026-07",
        // ... 외 1개
      ],
      "typicalAmount": 39000,
      "typicalDayOfMonth": 15,
      "confidence": 0.8,
      "reason": "3개월 동안 매달 15일 전후로 약 39,000원씩 결제됐습니다.",
      "suggestedRule": {
        "title": "넷플릭스",
        "recurrenceType": "MONTHLY",
        "dayOfMonth": 15,
        "startDate": "2026-10-01",
        "amount": 39000,
        "eventType": "SUBSCRIPTION",
        "direction": "OUTFLOW",
        "accountId": null
      }
    },
    {
      "merchant": "유튜브 프리미엄",
      "monthsObserved": 3,
      "months": [
        "2026-06",
        "2026-07",
        // ... 외 1개
      ],
      "typicalAmount": 14900,
      "typicalDayOfMonth": 20,
      "confidence": 0.77,
      "reason": "3개월 동안 매달 20일 전후로 약 14,900원씩 결제됐습니다.",
      "suggestedRule": {
        "title": "유튜브 프리미엄",
        "recurrenceType": "MONTHLY",
        "dayOfMonth": 20,
        "startDate": "2026-10-01",
        "amount": 14900,
        "eventType": "SUBSCRIPTION",
        "direction": "OUTFLOW",
        "accountId": null
      }
    }
  ]
}
```

### `POST /agent/recommend-actions` — 행동 추천

`priority` 가 작을수록 급함. `type`: `PROTECT_LOW_BALANCE`, `COVER_SHORTFALL`, `DAILY_ALLOWANCE`, `BUDGET_EXCEEDED`, `UPCOMING_BILL`, `REGISTER_RECURRING`, `SAVE_SURPLUS`. `detail` 은 유형별 추가 정보(예: 등록할 규칙).

응답 `200`

```jsonc
{
  "weather": "CLOUDY",
  "availableAmount": 561000,
  "actions": [
    {
      "type": "DAILY_ALLOWANCE",
      "priority": 2,
      "message": "2026-09-30까지 하루 561,000원 이내로 쓰면 지금 자금 날씨를 유지할 수 있습니다.",
      "detail": {
        "remainingDays": 1,
        "availableAmount": 561000
      }
    },
    {
      "type": "REGISTER_RECURRING",
      "priority": 4,
      "message": "넷플릭스 월 39,000원이 매달 반복됩니다. 반복 규칙으로 등록하면 자금 날씨 예측에 반영됩니다.",
      "detail": {
        "title": "넷플릭스",
        "recurrenceType": "MONTHLY",
        "dayOfMonth": 15,
        "startDate": "2026-10-01",
        "amount": 39000,
        "eventType": "SUBSCRIPTION",
        "direction": "OUTFLOW",
        "accountId": null
      }
    },
    // ... 외 1개
  ],
  "riskSummary": {
    "message": "이번 달 자금 흐름이 안정적입니다.",
    "level": "GOOD"
  }
}
```

---

## 8. 운영

| 경로 | 인증 | 설명 |
| --- | --- | --- |
| `/actuator/health` | 공개 | `{"status":"UP"}` |
| `/actuator/info` | 공개 | |
| `/actuator/prometheus` | 공개 | Prometheus 메트릭 (인터넷에 직접 노출한다면 프록시에서 막을 것) |
| `/actuator/metrics` | 필요 | |
| `/swagger-ui/index.html`, `/v3/api-docs` | 공개 | |

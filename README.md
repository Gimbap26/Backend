# Money Weather API

"이번 달 마음대로 써도 되는 돈"을 계산해 **자금 날씨**(맑음·흐림·비·폭풍)로 보여주는 가계부 앱의 백엔드입니다.

```
사용 가능 자금 = 현재 잔액(자산 포함 계좌) − 이번 달 아직 내지 않은 고정 지출
```

Spring Boot 3.3 / Java 21. 기본 경로는 `/api/v1`, 기본 DB 는 H2 메모리 DB 이고 `postgres` 프로필로 PostgreSQL 을 씁니다.

## 실행

### 빌드

```powershell
mvn test
mvn package
```

`mvn` 이 PATH 에 없으면 IntelliJ 에 들어 있는 Maven 을 쓸 수 있습니다.

```powershell
& "C:\Program Files\JetBrains\IntelliJ IDEA 2024.3.2\plugins\maven\lib\maven3\bin\mvn.cmd" package
```

### 실행 (jar)

```powershell
java -jar target/money-weather-api-0.0.1-SNAPSHOT.jar
```

- 프로젝트 경로에 한글(예: `사무공간`, `종설2`)이 있으면 `mvn spring-boot:run` 이 클래스를 찾지 못하고 실패합니다. 위처럼 **jar 로 실행**하세요.
- 8080 포트를 다른 프로그램이 쓰고 있으면 `--server.port=8081` 을 붙이세요.

### PostgreSQL 로 실행

```powershell
docker compose up -d
$env:JWT_SECRET="16자-이상의-긴-비밀값으로-바꾸세요"
java -jar target/money-weather-api-0.0.1-SNAPSHOT.jar --spring.profiles.active=postgres
```

`postgres` 프로필은 운영용이라 `JWT_SECRET` 이 없으면 **기동하지 않고**, 데모 데이터(시드)도 만들지 않습니다.

## 홈서버 배포 (Docker)

기본 H2 는 **메모리 DB** 라서 서버를 재시작하면 모든 데이터가 사라집니다. 배포할 때는 아래처럼 앱과 PostgreSQL 을 함께 띄웁니다. 홈서버에 Docker(Compose 포함)만 있으면 되고, Java·Maven 은 필요 없습니다. x86 과 ARM(라즈베리파이 등) 모두 됩니다.

```bash
git clone <저장소 주소> && cd Gimbap_BE
cp .env.example .env
# .env 에서 POSTGRES_PASSWORD, JWT_SECRET 을 채운다 (JWT_SECRET 만들기: openssl rand -base64 48)
docker compose -f docker-compose.prod.yml up -d --build
```

**처음 배포한 뒤 한 번 확인할 것** — 로그에 마이그레이션 5개가 적용되고 앱이 시작됐는지 봅니다.

```bash
docker compose -f docker-compose.prod.yml logs app | grep -E "Successfully applied|Started MoneyWeather|ERROR"
curl http://localhost:8080/actuator/health
```

`Successfully applied 5 migrations` 와 `Started MoneyWeatherApiApplication` 이 보이고 health 가 `{"status":"UP"}` 이면 정상입니다.

| 할 일 | 명령 |
| --- | --- |
| 코드 갱신 후 재배포 | `git pull && docker compose -f docker-compose.prod.yml up -d --build` |
| 로그 보기 | `docker compose -f docker-compose.prod.yml logs -f app` |
| DB 백업 | `docker compose -f docker-compose.prod.yml exec db pg_dump -U moneyweather moneyweather > backup.sql` |
| 중지 (데이터 유지) | `docker compose -f docker-compose.prod.yml down` |

- DB 데이터는 `db-data` 볼륨에 남습니다. `down -v` 는 **데이터까지 지우므로** 쓰지 마세요.
- DB 포트는 외부에 열지 않습니다. 앱 컨테이너만 내부 네트워크로 접근합니다.
- 컨테이너 시간대는 `Asia/Seoul` 로 고정되어 있습니다. "오늘" 기준 계산(지난 이벤트, 남은 일수, 다가오는 결제)이 한국 날짜로 맞게 동작합니다.
- 프론트를 배포하면 그 주소를 `.env` 의 `CORS_ALLOWED_ORIGINS` 에 추가하세요.
- Nginx·Caddy 같은 리버스 프록시 뒤에 두면 `FORWARD_HEADERS_STRATEGY=framework` 로 바꾸세요. 그대로 두면 모든 요청이 프록시 IP 로 보여, IP 기준 로그인 제한이 모든 사용자를 함께 막습니다.
- 시연용 데모 계정이 필요하면 `DEMO_DATA_ENABLED=true` 로 켜세요. DB 가 비어 있을 때 한 번만 만듭니다. 전체 초기화 API(`/dev/seed`)는 운영에서 계속 꺼져 있습니다.
- `/actuator/prometheus` 는 인증 없이 열려 있습니다. 인터넷에 포트를 직접 열 계획이면 프록시에서 이 경로를 막는 것을 권장합니다.

## 로그인

로그인·회원가입을 제외한 모든 API 는 `Authorization: Bearer <토큰>` 헤더가 필요합니다.

기본(H2) 실행 시 데모 계정이 자동으로 만들어집니다.

| 이메일 | 비밀번호 |
| --- | --- |
| `demo@moneyweather.dev` | `demo1234!` (환경 변수 `DEMO_PASSWORD` 로 변경 가능) |

```powershell
$login = Invoke-RestMethod -Method Post -ContentType "application/json" `
  -Body '{"email":"demo@moneyweather.dev","password":"demo1234!"}' `
  -Uri "http://localhost:8080/api/v1/auth/login"

Invoke-RestMethod -Headers @{ Authorization = "Bearer $($login.accessToken)" } `
  -Uri "http://localhost:8080/api/v1/dashboard"
```

Swagger UI 에서는 오른쪽 위 **Authorize** 버튼에 토큰을 넣으면 됩니다.

새 계정은 `POST /api/v1/auth/signup` (`email`, 8자 이상 `password`, `name`) 으로 만들고, 가입 응답에 바로 토큰이 들어 있습니다.

| 기능 | API | 동작 |
| --- | --- | --- |
| 로그아웃 | `POST /api/v1/auth/logout` | 이 계정의 **모든 기기** 토큰을 무효로 만듭니다 |
| 비밀번호 변경 | `PATCH /api/v1/auth/password` | 다른 기기의 토큰은 무효가 되고, 지금 기기에서 쓸 새 토큰을 돌려줍니다 |
| 로그인 제한 | — | 15분 안에 같은 이메일로 5번, 같은 IP 에서 20번 넘게 실패하면 잠시 429 를 돌려줍니다 |

## 프론트엔드(React) 연동

`http://localhost:5173`, `http://localhost:5174`, `http://localhost:3000` 에서 브라우저로 호출할 수 있도록 CORS 가 열려 있습니다. 다른 주소를 쓰려면 `CORS_ALLOWED_ORIGINS` 에 쉼표로 추가합니다. 토큰은 쿠키가 아니라 `Authorization` 헤더로 보냅니다.

```js
const res = await fetch("http://localhost:8080/api/v1/dashboard", {
  headers: { Authorization: `Bearer ${accessToken}` },
});
```

401 을 받으면 토큰이 없거나 만료·무효(로그아웃, 비밀번호 변경)된 것이므로 로그인 화면으로 보내면 됩니다.

## 주요 URL

| 용도 | URL |
| --- | --- |
| Swagger UI | `http://localhost:8080/swagger-ui/index.html` |
| Health Check | `http://localhost:8080/actuator/health` |
| Prometheus | `http://localhost:8080/actuator/prometheus` |
| H2 Console | `http://localhost:8080/h2-console` — `H2_CONSOLE_ENABLED=true` 일 때만. JDBC URL `jdbc:h2:mem:moneyweather` |

## AI Agent

AI 는 질문에 필요한 **도구**(대시보드, 예정 이벤트, 잔액 예측, 예산 현황, 소비 분석, 지출 시뮬레이션, 반복 지출 탐지, 알림)를 스스로 골라 호출하고, 그 결과의 실제 숫자로 답합니다. 호출한 도구와 결과는 답변과 함께 저장되어 `GET /api/v1/agent/evidence/{messageId}` 로 볼 수 있습니다.

**`AI_API_KEY` 환경 변수만 넣으면 됩니다.** `AI_PROVIDER` 의 기본값 `auto` 가 키를 보고 알아서 고릅니다.

- **키 없음**: 규칙 기반 답변기가 질문의 키워드로 같은 도구를 골라 답합니다. "25만원 쓰면?", "9월 12일", "지난달" 같은 금액·날짜·달도 읽습니다.
- **키 있음**: OpenAI 호환 Chat Completions API 로 Tool Calling 을 합니다.

```powershell
$env:AI_API_KEY="<발급받은 키>"
$env:AI_MODEL="gpt-4.1-mini"      # 선택
```

IntelliJ 에서는 실행 설정(Run Configuration)의 Environment variables 에 `AI_API_KEY` 를 넣으면 됩니다. 키는 `application.yml` 같은 파일이나 채팅에 붙여넣지 말고 환경 변수로만 넣으세요.

어느 쪽으로 동작하는지는 기동 로그의 `AI 답변기:` 줄에 나옵니다.

| `AI_PROVIDER` | 동작 |
| --- | --- |
| `auto` (기본) | 키가 있으면 외부 AI, 없으면 규칙 기반 |
| `rule-based` | 키가 있어도 규칙 기반 (비용 없이 시연할 때) |
| `openai-compatible` | 외부 AI. 키가 없으면 기동 실패 |

외부 AI 호출이 실패하면(키 오류, 크레딧 소진 등) 서비스는 멈추지 않고 규칙 기반으로 대신 답하며, 서버 로그에 원인을 남깁니다.

chat 응답의 `provider` 로 실제 어떤 경로로 답했는지 알 수 있습니다.

| provider | 의미 |
| --- | --- |
| `rule-based` | 키 없이 규칙 기반으로 답함 |
| `openai-compatible` | 외부 AI 가 도구를 호출해 답함 |
| `openai-compatible-fallback` | 외부 AI 호출이 실패해 규칙 기반으로 대신 답함 (서버 로그에 원인이 남음) |

## 환경 변수

| 이름 | 기본값 | 설명 |
| --- | --- | --- |
| `JWT_SECRET` | 개발용 값 (postgres 에서는 필수) | 토큰 서명 키. 16자 이상 |
| `JWT_TTL_SECONDS` | `86400` | 토큰 유효 시간(초) |
| `AI_PROVIDER` | `auto` | 키가 있으면 외부 AI, 없으면 규칙 기반. `rule-based` / `openai-compatible` 로 강제 가능 |
| `AI_API_KEY` | — | 외부 AI 키 |
| `AI_BASE_URL` | `https://api.openai.com/v1` | OpenAI 호환 API 주소 |
| `AI_MODEL` | `gpt-4.1-mini` | 모델 이름 |
| `AI_TIMEOUT_SECONDS` | `60` | 외부 AI 응답 대기 시간 |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:5174,http://localhost:3000` | 브라우저에서 API 를 부를 수 있는 프론트 주소 |
| `FORWARD_HEADERS_STRATEGY` | `none` | 리버스 프록시 뒤라면 `framework` |
| `LOGIN_MAX_FAILURES_PER_EMAIL` / `_PER_IP` | `5` / `20` | 로그인 실패 허용 횟수 |
| `LOGIN_FAILURE_WINDOW_MINUTES` | `15` | 실패 횟수를 세는 기간 |
| `DEMO_DATA_ENABLED` | `true` (postgres 는 `false`) | DB 가 비어 있으면 시작할 때 데모 계정과 샘플 데이터 생성 |
| `DEV_SEED_ENABLED` | `true` (postgres 는 `false`) | `POST /dev/seed` 허용. **모든 데이터를 지우므로** 운영에서 켜지 말 것 |
| `DEMO_PASSWORD` | `demo1234!` | 데모 계정 비밀번호 |
| `H2_CONSOLE_ENABLED` | `false` | H2 콘솔 사용 |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | docker-compose 값 | PostgreSQL 연결 |

## 핵심 동작

- **잔액 자동 반영**: 계좌를 지정한 거래(지출·수입·이체)는 즉시 잔액에 반영되고, 수정·삭제하면 되돌려집니다. 카드 거래는 결제일 청구 이벤트를 `PAID` 로 바꿀 때 결제 계좌에서 빠집니다. 실제 통장과 어긋나면 `PATCH /accounts/{id}` 의 `balance` 로 직접 맞출 수 있습니다.
- **반복 규칙**: 매달·매주·매년 규칙을 등록하면 조회하는 달마다 예정 이벤트가 자동으로 생깁니다. 규칙을 바꾸면 아직 지나지 않은 예정분만 새로 맞춰집니다.
- **카드 청구**: 전월 카드 사용액을 합쳐 당월 결제일에 청구 이벤트를 만듭니다.
- **알림**: 잔액 마이너스·부족 예상, 일주일 안의 결제, 예산 초과·임박.
- **거래 검색**: 필터와 페이징을 DB 에서 처리하고 최신순으로 돌려줍니다. 한 페이지 최대 100건. `totalAmount` 는 조건에 맞는 전체 합계입니다.

## 문서

- API 목록과 상태: [docs/FEATURE_COVERAGE.md](docs/FEATURE_COVERAGE.md)
- 기능별 요약: [docs/API_FEATURE_SUMMARY.md](docs/API_FEATURE_SUMMARY.md)
- 남은 과제: [docs/DEVELOPMENT_BACKLOG.md](docs/DEVELOPMENT_BACKLOG.md)

## 테스트

`mvn clean test` — 117개(통합 103, 단위 14). 테스트는 `AI_API_KEY` 가 있어도 실제(유료) AI 를 부르지 않도록 규칙 기반으로 고정돼 있습니다(`src/test/resources/config/application.properties`). 외부 AI 경로는 테스트 안에서 띄우는 가짜 AI 서버로 검증하므로 키가 필요 없습니다.

소스 파일을 지우거나 옮긴 뒤에는 꼭 `clean` 을 붙이세요. 붙이지 않으면 지운 클래스가 `target/` 에 남아 계속 로드될 수 있습니다.

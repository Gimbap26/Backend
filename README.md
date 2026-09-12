# Money Weather API

Spring Boot 기반 자금 날씨 백엔드입니다. 기본 API 경로는 `/api/v1`이며, H2 메모리 DB로 바로 실행하고 `postgres` 프로필로 PostgreSQL 운영 환경을 확인할 수 있습니다.

## 실행

로컬에 Maven이 있으면 아래 명령으로 실행합니다.

```powershell
mvn test
mvn package
java -jar target/money-weather-api-0.0.1-SNAPSHOT.jar
```

PostgreSQL로 실행하려면:

```powershell
docker compose up -d
mvn package
java -jar target/money-weather-api-0.0.1-SNAPSHOT.jar --spring.profiles.active=postgres
```

## 주요 URL

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- H2 Console: `http://localhost:8080/h2-console`
- H2 JDBC URL: `jdbc:h2:mem:moneyweather`
- Health Check: `http://localhost:8080/actuator/health`
- Metrics: `http://localhost:8080/actuator/metrics`
- Prometheus: `http://localhost:8080/actuator/prometheus`
- 기능 대조표: `docs/FEATURE_COVERAGE.md`

## 사용자 컨텍스트

개발 단계 인증은 Bearer 토큰 또는 `X-User-Id` 헤더로 처리합니다. 헤더가 없으면 기본 사용자 `1`을 사용합니다.

토큰 발급:

```powershell
Invoke-RestMethod -Method Post `
  -ContentType "application/json" `
  -Body '{"userId":1}' `
  -Uri "http://localhost:8080/api/v1/auth/dev-token"
```

토큰 사용:

```powershell
Invoke-RestMethod `
  -Headers @{ "Authorization" = "Bearer <token>" } `
  -Uri "http://localhost:8080/api/v1/dashboard"
```

## AI Agent 설정

기본 AI provider는 `rule-based`입니다. 외부 OpenAI 호환 Chat Completions API를 연결하려면 아래 환경 변수를 설정합니다.

```powershell
$env:AI_PROVIDER="openai-compatible"
$env:AI_API_KEY="<api-key>"
$env:AI_MODEL="gpt-4.1-mini"
```

API 키가 필요한 외부 모델 호출을 제외하면, 로컬 규칙 기반 답변과 도구 스냅샷 기반 보조 API는 바로 동작합니다.

## 운영 기능

- Flyway V1: 핵심 도메인 스키마 생성
- Flyway V2: API 요청 로그 저장용 `api_request_logs` 테이블과 조회 인덱스 추가
- Actuator: `health`, `info`, `metrics`, `prometheus` 노출
- 요청 로그: 업무 API 요청의 사용자, HTTP 메서드, 경로, 상태 코드, 처리 시간, IP, User-Agent 저장
- 테스트: 컨트롤러 통합 테스트 9개, 계산 로직 단위 테스트 5개

## 구현 범위

기능 명세서 초안의 핵심 API 18개와 기획 초안에서 추가로 필요한 API 11개, 인증 API 1개, 운영 모니터링 엔드포인트를 구현했습니다. 데이터는 JPA 기반으로 저장하며, 스키마는 Flyway 마이그레이션과 Hibernate validate로 검증합니다.

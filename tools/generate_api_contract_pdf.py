from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    BaseDocTemplate,
    Frame,
    PageBreak,
    PageTemplate,
    Paragraph,
    Spacer,
    Table,
    TableStyle,
)


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "output" / "pdf" / "money_weather_api_contract.pdf"


def register_font():
    candidates = [
        Path("C:/Windows/Fonts/malgun.ttf"),
        Path("C:/Windows/Fonts/NanumGothic.ttf"),
    ]
    bold_candidates = [
        Path("C:/Windows/Fonts/malgunbd.ttf"),
        Path("C:/Windows/Fonts/NanumGothicBold.ttf"),
    ]
    font = next((p for p in candidates if p.exists()), None)
    bold = next((p for p in bold_candidates if p.exists()), font)
    if font:
        pdfmetrics.registerFont(TTFont("Korean", str(font)))
        pdfmetrics.registerFont(TTFont("Korean-Bold", str(bold)))
        return "Korean", "Korean-Bold"
    return "Helvetica", "Helvetica-Bold"


FONT, FONT_BOLD = register_font()


def styles():
    base = getSampleStyleSheet()
    return {
        "title": ParagraphStyle(
            "title",
            parent=base["Title"],
            fontName=FONT_BOLD,
            fontSize=22,
            leading=29,
            alignment=TA_CENTER,
            textColor=colors.HexColor("#1F2937"),
            spaceAfter=10,
        ),
        "subtitle": ParagraphStyle(
            "subtitle",
            parent=base["Normal"],
            fontName=FONT,
            fontSize=10.5,
            leading=15,
            alignment=TA_CENTER,
            textColor=colors.HexColor("#4B5563"),
            spaceAfter=18,
        ),
        "h1": ParagraphStyle(
            "h1",
            parent=base["Heading1"],
            fontName=FONT_BOLD,
            fontSize=16,
            leading=22,
            textColor=colors.HexColor("#111827"),
            spaceBefore=8,
            spaceAfter=8,
        ),
        "h2": ParagraphStyle(
            "h2",
            parent=base["Heading2"],
            fontName=FONT_BOLD,
            fontSize=12.5,
            leading=17,
            textColor=colors.HexColor("#111827"),
            spaceBefore=6,
            spaceAfter=4,
        ),
        "body": ParagraphStyle(
            "body",
            parent=base["BodyText"],
            fontName=FONT,
            fontSize=9.2,
            leading=14,
            textColor=colors.HexColor("#1F2937"),
            spaceAfter=6,
        ),
        "small": ParagraphStyle(
            "small",
            parent=base["BodyText"],
            fontName=FONT,
            fontSize=8,
            leading=11,
            textColor=colors.HexColor("#374151"),
        ),
        "cell": ParagraphStyle(
            "cell",
            parent=base["BodyText"],
            fontName=FONT,
            fontSize=7.6,
            leading=10.5,
            textColor=colors.HexColor("#111827"),
            alignment=TA_LEFT,
        ),
        "cell_bold": ParagraphStyle(
            "cell_bold",
            parent=base["BodyText"],
            fontName=FONT_BOLD,
            fontSize=7.6,
            leading=10.5,
            textColor=colors.white,
            alignment=TA_LEFT,
        ),
    }


S = styles()


FEATURE_ROWS = [
    ("인증", "`POST /auth/signup`, `/auth/login`, `/auth/logout`, `PATCH /auth/password`, `GET /users/me`", "이메일·비밀번호 가입과 로그인(JWT). 로그아웃·비밀번호 변경 시 기존 토큰 무효화, 로그인 실패 반복 시 잠시 차단(429)."),
    ("자산", "`/accounts`, `/cards`, `/categories` 조회·등록·수정·삭제", "계좌 잔액 직접 수정, 카드 결제일·결제 계좌, 카테고리 관리. 사용 중인 계좌 삭제 불가, 카드 삭제는 비활성화."),
    ("거래", "`GET/POST /transactions`, `PATCH/DELETE /transactions/{id}`, `PATCH .../category`", "계좌 거래는 잔액에 즉시 반영, 카드 거래는 카드 청구 때 반영. 검색은 DB 페이징(최신순, 최대 100건)."),
    ("예정 이벤트", "`GET/POST/PATCH/DELETE /financial-events`", "카드 대금·고정 지출·급여 등 앞으로의 현금 흐름. PAID 처리 시 결제 계좌 잔액에 반영."),
    ("반복 규칙", "`POST/GET/PATCH/DELETE /recurring-rules`", "매달·매주·매년, 종료일. 조회하는 달마다 예정 이벤트 자동 생성."),
    ("예측/대시보드", "`/dashboard`, `/forecasts`, `/available-funds/simulations`, `/forecast-alerts`", "사용 가능 자금·자금 날씨, 날짜별 예상 잔액, 지출일 반영 시뮬레이션, 위험 알림 5종."),
    ("예산", "`GET/PUT /budgets/monthly`, `GET /budgets/monthly/status`", "월 예산과 카테고리별 한도, 실제 지출 대비 초과 여부."),
    ("AI Agent", "`/agent/*` 8개 API", "AI 가 도구 8개 중 필요한 것을 호출해 실제 수치로 답변. 근거 저장, 반복 지출 탐지, 추천 질문·행동 추천."),
    ("운영", "`/actuator/health`, `/actuator/prometheus`, `api_request_logs`", "헬스 체크, 메트릭, API 요청 기록(401 포함)."),
]


API_ROWS = [
    ("POST", "/api/v1/auth/signup", "인증", "회원가입 (공개)", "body: email, password(8자+), name", "userId, email, name, accessToken, expiresIn"),
    ("POST", "/api/v1/auth/login", "인증", "로그인 (공개)", "body: email, password", "userId, tokenType, accessToken, expiresIn"),
    ("POST", "/api/v1/auth/logout", "인증", "모든 기기 로그아웃", "none", "loggedOut"),
    ("PATCH", "/api/v1/auth/password", "인증", "비밀번호 변경", "body: currentPassword, newPassword", "tokenType, accessToken (새 토큰)"),
    ("GET", "/api/v1/users/me", "사용자", "로그인한 사용자 정보", "none", "userId, name, email, baseMonth, status"),
    ("POST", "/api/v1/dev/seed", "개발", "전체 초기화 후 데모 데이터 생성 (운영에서는 404)", "body: userId, baseMonth, reset", "userId, baseMonth, created"),
    ("GET", "/api/v1/accounts", "자산", "계좌 목록과 잔액 합계", "query: includedOnly", "totalBalance, allAccountsBalance, accounts"),
    ("POST", "/api/v1/accounts", "자산", "계좌 등록", "body: bankName, accountName, balance?, purpose?, includedInAssets?", "account"),
    ("PATCH", "/api/v1/accounts/{accountId}", "자산", "계좌 수정·잔액 직접 수정", "body: 부분 필드, balance?", "account"),
    ("DELETE", "/api/v1/accounts/{accountId}", "자산", "계좌 삭제 (사용 중이면 409)", "path", "accountId, deletedAt"),
    ("GET", "/api/v1/cards", "자산", "카드 목록", "query: active?", "cards"),
    ("POST", "/api/v1/cards", "자산", "카드 등록", "body: cardCompany, cardName, paymentDay(1~31), paymentAccountId?", "card"),
    ("PATCH", "/api/v1/cards/{cardId}", "자산", "카드 수정", "body: 부분 필드", "card"),
    ("DELETE", "/api/v1/cards/{cardId}", "자산", "카드 비활성화", "path", "cardId, active=false"),
    ("GET", "/api/v1/categories", "분류", "카테고리 목록", "query: categoryType?", "categories"),
    ("POST", "/api/v1/categories", "분류", "카테고리 등록 (이름 중복 409)", "body: name, categoryType", "category"),
    ("PATCH", "/api/v1/categories/{categoryId}", "분류", "카테고리 수정 (예산 한도 이름도 변경)", "body: name?, categoryType?", "category"),
    ("DELETE", "/api/v1/categories/{categoryId}", "분류", "카테고리 삭제 (기본은 409)", "path", "uncategorizedTransactions, removedBudgetLimits"),
    ("GET", "/api/v1/transactions", "거래", "거래 검색 (DB 페이징, 최신순)", "query: month, transactionType?, categoryId?, keyword?, page, size(≤100)", "items, totalAmount, totalElements, totalPages"),
    ("POST", "/api/v1/transactions", "거래", "거래 등록 (계좌 거래는 잔액 반영)", "body: transactionDate, merchant, amount, transactionType, categoryId?, accountId? | cardId?, transferAccountId?", "transaction"),
    ("PATCH", "/api/v1/transactions/{transactionId}", "거래", "거래 수정 (잔액 재반영)", "body: 부분 필드", "transaction"),
    ("DELETE", "/api/v1/transactions/{transactionId}", "거래", "거래 삭제 (잔액 복구)", "path", "transactionId, deletedAt"),
    ("PATCH", "/api/v1/transactions/{transactionId}/category", "거래", "카테고리만 변경", "body: categoryId", "transactionId, categoryId, categoryName"),
    ("GET", "/api/v1/financial-events", "예정 이벤트", "기간 내 예정 입출금 (자동 생성 포함)", "query: from, to, direction?, eventType?, status?", "events"),
    ("POST", "/api/v1/financial-events", "예정 이벤트", "예정 이벤트 등록", "body: eventDate, title, amount, direction, eventType, fixed, accountId?", "event"),
    ("PATCH", "/api/v1/financial-events/{eventId}", "예정 이벤트", "수정, PAID 시 잔액 반영", "body: 부분 필드, status?, accountId?", "eventId, status, accountId"),
    ("DELETE", "/api/v1/financial-events/{eventId}", "예정 이벤트", "취소 (결제분이면 잔액 복구)", "path", "eventId, status=CANCELED"),
    ("POST", "/api/v1/recurring-rules", "반복 규칙", "반복 규칙 등록", "body: title, recurrenceType, dayOfMonth?, startDate, endDate?, amount, eventType, direction, accountId?", "recurringRuleId, generatedEvents"),
    ("GET", "/api/v1/recurring-rules", "반복 규칙", "반복 규칙 목록", "none", "recurringRules"),
    ("PATCH", "/api/v1/recurring-rules/{id}", "반복 규칙", "수정 (앞으로의 예정분만 재생성)", "body: 부분 필드", "rule"),
    ("DELETE", "/api/v1/recurring-rules/{id}", "반복 규칙", "비활성화 (앞으로의 예정분 제거)", "path", "recurringRuleId, active=false"),
    ("GET", "/api/v1/forecasts", "예측", "날짜별 예상 잔액", "query: from, to", "timeline, minimumExpectedBalance, minimumBalanceDate, weatherStatus"),
    ("GET", "/api/v1/dashboard", "대시보드", "홈 요약", "query: baseDate?, targetDate?", "currentBalance, fixedOutflows, availableAmount, weather, riskSummary, nextEvents"),
    ("POST", "/api/v1/available-funds/simulations", "시뮬레이션", "추가 지출 영향 (저장 안 함)", "body: spendingDate, amount, targetDate, title?", "before, after(최저 잔액·날짜 포함), difference, message"),
    ("GET", "/api/v1/forecast-alerts", "알림", "위험 알림 (심각도순)", "query: from?, to?", "alerts[type, severity, date, title, message, amount]"),
    ("GET", "/api/v1/weather-statuses", "안내", "자금 날씨 상태 안내", "none", "statuses"),
    ("GET", "/api/v1/budgets/monthly", "예산", "월 예산 조회", "query: month?", "month, categoryLimits, totalLimit"),
    ("PUT", "/api/v1/budgets/monthly", "예산", "월 예산 저장", "body: month, categoryLimits, totalLimit", "month, categoryLimits, totalLimit"),
    ("GET", "/api/v1/budgets/monthly/status", "예산", "예산 대비 지출", "query: month?", "totalLimit, totalSpent, totalExceeded, categories"),
    ("POST", "/api/v1/agent/conversations", "AI Agent", "대화 생성", "body: title?", "conversationId, title"),
    ("POST", "/api/v1/agent/chat", "AI Agent", "도구를 호출해 답변", "body: conversationId?, message", "messageId, answer, provider, sources, toolCalls"),
    ("GET", "/api/v1/agent/conversations/{id}/messages", "AI Agent", "대화 메시지", "path", "messages"),
    ("GET", "/api/v1/agent/evidence/{messageId}", "AI Agent", "답변 당시 도구 호출 기록", "path", "provider, sources, toolCalls[name, arguments, result]"),
    ("GET", "/api/v1/agent/suggestions", "AI Agent", "상황별 추천 질문 4개", "none", "suggestions"),
    ("POST", "/api/v1/agent/analyze-spending", "AI Agent", "월 카테고리별 지출 순위", "query: month?", "totalExpense, byCategory, ranking"),
    ("POST", "/api/v1/agent/detect-recurring", "AI Agent", "등록 안 된 반복 지출 탐지", "none", "candidates[merchant, confidence, suggestedRule]"),
    ("POST", "/api/v1/agent/recommend-actions", "AI Agent", "수치 기반 행동 추천", "none", "weather, availableAmount, actions[type, priority, message]"),
    ("GET", "/actuator/health", "운영", "상태 확인 (공개)", "none", "status"),
    ("GET", "/actuator/prometheus", "운영", "Prometheus 메트릭 (공개)", "none", "Prometheus text format"),
]


DETAILS = [
    ("인증", [
        ("POST /api/v1/auth/login", "이메일과 비밀번호가 맞으면 JWT 를 발급합니다. 틀리면 무엇이 틀렸는지 구분하지 않고 401. 15분 안에 같은 이메일 5번, 같은 IP 20번 넘게 실패하면 429.", '{"email": "demo@moneyweather.dev", "password": "demo1234!"}', '{"tokenType": "Bearer", "accessToken": "<token>", "expiresIn": 86400}'),
        ("POST /api/v1/auth/logout", "이 계정으로 발급된 모든 토큰을 무효로 만듭니다(모든 기기 로그아웃).", "header: Authorization", '{"loggedOut": true}'),
        ("PATCH /api/v1/auth/password", "현재 비밀번호 확인 후 변경. 다른 기기의 토큰은 무효가 되고 지금 기기용 새 토큰을 돌려줍니다. 현재 비밀번호가 틀리면 400.", '{"currentPassword": "...", "newPassword": "..."}', '{"accessToken": "<new token>"}'),
    ]),
    ("거래 / 잔액", [
        ("POST /api/v1/transactions", "accountId 가 있으면 잔액에 즉시 반영(지출 차감, 수입 증가, 이체는 두 계좌). cardId 가 있으면 카드 청구 이벤트가 PAID 될 때 결제 계좌에서 빠집니다. 둘 다 지정하면 400.", '{"transactionDate": "2026-09-20", "merchant": "편의점", "amount": 12000, "transactionType": "EXPENSE", "accountId": 1}', '{"transactionId": 7, "accountId": 1, ...}'),
        ("GET /api/v1/transactions", "필터와 페이징을 DB 에서 처리합니다. totalAmount 는 현재 페이지가 아닌 조건에 맞는 전체 합계.", "query: month=2026-09&keyword=편의&page=0&size=20", '{"items": [...], "totalElements": 25, "totalPages": 2}'),
        ("PATCH /api/v1/financial-events/{id}", "status 를 PAID 로 바꾸면 결제 계좌(요청의 accountId 또는 이벤트의 계좌)에서 빠집니다. 계좌가 없으면 400.", '{"status": "PAID", "accountId": 1}', '{"status": "PAID", "accountId": 1}'),
    ]),
    ("예측", [
        ("GET /api/v1/dashboard", "사용 가능 자금 = 현재 잔액(자산 포함 계좌) − 기간 내 아직 내지 않은 고정 지출.", "query: baseDate, targetDate optional", '{"availableAmount": 561000, "weather": "CLOUDY"}'),
        ("POST /api/v1/available-funds/simulations", "지출일에 가상 지출을 넣어 전후 예측을 비교합니다. 저장하지 않습니다.", '{"spendingDate": "2026-09-12", "amount": 250000, "targetDate": "2026-09-30", "title": "에어팟"}', '{"after": {"availableAmount": 311000, "minimumBalance": 311000}}'),
    ]),
    ("AI Agent", [
        ("POST /api/v1/agent/chat", "AI 가 필요한 도구를 골라 호출하고 결과 수치로 답합니다. provider: rule-based(키 없음) / openai-compatible / openai-compatible-fallback(외부 호출 실패).", '{"message": "이번 달 얼마 써도 돼?"}', '{"answer": "...561,000원...", "sources": ["get_dashboard", "get_forecast"]}'),
        ("GET /api/v1/agent/evidence/{messageId}", "답변 당시 호출한 도구의 인자와 결과를 저장된 그대로 돌려줍니다.", "path: messageId", '{"toolCalls": [{"name": "get_dashboard", "result": {...}}]}'),
    ]),
]


def p(text, style="body"):
    return Paragraph(text.replace("`", ""), S[style])


def table(data, widths, header=True):
    converted = []
    for row_idx, row in enumerate(data):
        style = "cell_bold" if header and row_idx == 0 else "cell"
        converted.append([p(str(cell), style) for cell in row])
    t = Table(converted, colWidths=widths, repeatRows=1 if header else 0, hAlign="LEFT")
    ts = [
        ("GRID", (0, 0), (-1, -1), 0.25, colors.HexColor("#D1D5DB")),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING", (0, 0), (-1, -1), 4),
        ("RIGHTPADDING", (0, 0), (-1, -1), 4),
        ("TOPPADDING", (0, 0), (-1, -1), 4),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#F9FAFB")]),
    ]
    if header:
        ts.append(("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#1F2937")))
    t.setStyle(TableStyle(ts))
    return t


def header_footer(canvas, doc):
    canvas.saveState()
    canvas.setFont(FONT, 8)
    canvas.setFillColor(colors.HexColor("#6B7280"))
    canvas.drawString(18 * mm, 12 * mm, "Money Weather API Contract")
    canvas.drawRightString(192 * mm, 12 * mm, f"{doc.page}")
    canvas.restoreState()


def build():
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    doc = BaseDocTemplate(
        str(OUTPUT),
        pagesize=A4,
        rightMargin=16 * mm,
        leftMargin=16 * mm,
        topMargin=16 * mm,
        bottomMargin=18 * mm,
        title="Money Weather API Contract",
    )
    frame = Frame(doc.leftMargin, doc.bottomMargin, doc.width, doc.height, id="normal")
    doc.addPageTemplates([PageTemplate(id="main", frames=[frame], onPage=header_footer)])

    story = [
        p("Money Weather API 명세서", "title"),
        p("Spring Boot 백엔드 API 47개 구성 및 기능 설명", "subtitle"),
        p("1. 문서 개요", "h1"),
        p("본 문서는 현재 구현된 Money Weather 백엔드의 전체 API, 요청/응답 형태, 기능별 역할, 운영 모니터링 항목을 정리한 개발 명세서입니다. 기본 업무 API 경로는 /api/v1이며, 운영 Actuator 엔드포인트는 /actuator 아래에 노출됩니다."),
        table([
            ("항목", "내용"),
            ("백엔드", "Spring Boot, Spring MVC, Spring Data JPA, Flyway"),
            ("DB", "개발: H2 메모리 DB / 배포: PostgreSQL (docker-compose.prod.yml)"),
            ("인증", "이메일·비밀번호 로그인, JWT(HS256), BCrypt, 로그아웃·비밀번호 변경 시 토큰 무효화, 로그인 실패 제한"),
            ("AI", "Tool Calling. 키가 없으면 규칙 기반 답변기가 같은 도구로 답변, 키가 있으면 OpenAI 호환 API"),
            ("검증", "자동 테스트 110개 통과 (통합 101, 단위 9)"),
            ("CORS", "localhost:5173, 5174, 3000 허용 (CORS_ALLOWED_ORIGINS 로 추가)"),
        ], [36 * mm, 132 * mm]),
        Spacer(1, 8),
        p("2. 기능별 요약", "h1"),
        table([("기능 영역", "포함 API", "주요 내용")] + FEATURE_ROWS, [30 * mm, 58 * mm, 80 * mm]),
        PageBreak(),
        p("3. 전체 API 색인", "h1"),
        p("아래 표는 프론트엔드 연동과 QA 체크리스트로 사용할 수 있는 전체 엔드포인트 목록입니다."),
        table([("Method", "Path", "영역", "목적", "요청", "응답")] + API_ROWS, [15 * mm, 53 * mm, 17 * mm, 30 * mm, 31 * mm, 32 * mm]),
        PageBreak(),
        p("4. 공통 규칙", "h1"),
        p("요청은 JSON 본문과 ISO 날짜 형식을 기본으로 사용합니다. 월은 yyyy-MM, 일자는 yyyy-MM-dd 형식입니다. 요청 값 검증 실패, 소유권 불일치, 존재하지 않는 리소스는 공통 에러 응답으로 반환됩니다."),
        table([
            ("항목", "형식"),
            ("Authorization", "Bearer <token> (로그인·회원가입, health, prometheus, Swagger 외 모든 API 필수)"),
            ("401", "토큰 없음·만료·위조·무효화(로그아웃, 비밀번호 변경) → 로그인 화면으로"),
            ("403", "다른 사용자의 데이터에 접근"),
            ("429", "로그인 실패 반복으로 잠시 차단"),
            ("성공 응답", "각 API별 JSON 객체 또는 목록"),
            ("에러 응답", '{"timestamp": "...", "status": 400, "code": "VALIDATION_ERROR", "message": "요청 값이 올바르지 않습니다.", "path": "...", "fields": {...}}'),
        ], [36 * mm, 132 * mm]),
        Spacer(1, 8),
        p("5. 상세 API 설명", "h1"),
    ]

    for title, rows in DETAILS:
        story.append(p(title, "h2"))
        story.append(table([("API", "내용", "요청 예시", "응답 예시")] + rows, [43 * mm, 58 * mm, 38 * mm, 39 * mm]))
        story.append(Spacer(1, 6))

    story.extend([
        PageBreak(),
        p("6. 운영 · 배포", "h1"),
        table([
            ("항목", "내용", "비고"),
            ("DB 마이그레이션", "Flyway V1 스키마 · V2 요청 로그 · V3 반복 규칙·카드 연결 · V4 인증·잔액 원장·AI 근거 · V5 토큰 무효화", "Hibernate validate 로 스키마 검증"),
            ("배포", "Dockerfile + docker-compose.prod.yml (앱 + PostgreSQL). 시간대 Asia/Seoul 고정, DB 포트 비공개", "README '홈서버 배포' 참고"),
            ("요청 로그", "api_request_logs 에 사용자, 경로, 상태, 처리 시간 기록 (401 로 막힌 요청 포함)", "Flyway V2"),
            ("오류 응답", "{timestamp, status, code, message, path, fields}. 500 은 내부 메시지를 노출하지 않음", "ApiExceptionHandler"),
        ], [36 * mm, 88 * mm, 44 * mm]),
        Spacer(1, 8),
        p("7. 외부 키가 필요한 영역", "h1"),
        table([
            ("항목", "설명"),
            ("실제 AI 모델 호출", "AI_PROVIDER=openai-compatible, AI_API_KEY 설정 후 재시작. 키가 없으면 규칙 기반 답변기가 같은 도구로 실제 수치를 답합니다. Tool Calling 요청 형식은 가짜 AI 서버로 검증 완료."),
            ("파인튜닝/별도 모델 학습", "학습 데이터셋, 모델 제공자, 비용/보안 정책 확정이 필요합니다. 현재 백엔드는 모델 교체 가능한 어댑터까지 구현했습니다."),
        ], [44 * mm, 124 * mm]),
    ])

    doc.build(story)


if __name__ == "__main__":
    build()
    print(OUTPUT)

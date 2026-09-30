package com.moneyweather.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 로그인 실패 횟수 제한. 최근 {@code window} 동안
 * 같은 이메일로 {@code maxPerEmail}번(한 계정 비밀번호 대입), 같은 IP 에서 {@code maxPerIp}번(여러 계정 대입) 넘게 실패하면
 * 잠시 로그인을 막는다(429). 로그인에 성공하면 그 이메일의 실패 기록은 지운다.
 *
 * <p>서버 한 대에서 도는 것을 전제로 메모리에만 기록한다. 서버를 재시작하면 기록이 사라진다.
 * 리버스 프록시 뒤에서 돌 때는 {@code FORWARD_HEADERS_STRATEGY=framework}로 실제 접속 IP 를 쓰게 해야 한다.
 * 그렇지 않으면 모든 요청이 프록시 IP 하나로 보여 IP 제한이 전체 사용자를 함께 막는다.
 */
@Component
public class LoginAttemptService {
    /** 기록된 키가 이만큼 쌓이면 오래된 기록을 한꺼번에 정리한다. */
    private static final int PRUNE_THRESHOLD = 10_000;

    private final int maxPerEmail;
    private final int maxPerIp;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> failures = new HashMap<>();

    @Autowired
    public LoginAttemptService(@Value("${money-weather.auth.login.max-failures-per-email:5}") int maxPerEmail,
                               @Value("${money-weather.auth.login.max-failures-per-ip:20}") int maxPerIp,
                               @Value("${money-weather.auth.login.window-minutes:15}") long windowMinutes) {
        this(maxPerEmail, maxPerIp, Duration.ofMinutes(windowMinutes), Clock.systemUTC());
    }

    LoginAttemptService(int maxPerEmail, int maxPerIp, Duration window, Clock clock) {
        this.maxPerEmail = maxPerEmail;
        this.maxPerIp = maxPerIp;
        this.window = window;
        this.clock = clock;
    }

    /** 비밀번호를 확인하기 전에 부른다. 막혀 있으면 429. */
    public synchronized void checkAllowed(String email, String ip) {
        Instant now = clock.instant();
        long waitEmail = retryAfterSeconds(emailKey(email), maxPerEmail, now);
        long waitIp = retryAfterSeconds(ipKey(ip), maxPerIp, now);
        long wait = Math.max(waitEmail, waitIp);
        if (wait > 0) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "로그인 실패가 너무 많습니다. %d분 뒤에 다시 시도하세요.".formatted(Math.max(1, (wait + 59) / 60)));
        }
    }

    public synchronized void recordFailure(String email, String ip) {
        Instant now = clock.instant();
        if (failures.size() > PRUNE_THRESHOLD) pruneAll(now);
        failures.computeIfAbsent(emailKey(email), k -> new ArrayDeque<>()).addLast(now);
        failures.computeIfAbsent(ipKey(ip), k -> new ArrayDeque<>()).addLast(now);
    }

    public synchronized void recordSuccess(String email) {
        failures.remove(emailKey(email));
    }

    /** 테스트에서 다른 테스트의 실패 기록이 섞이지 않게 비운다. */
    public synchronized void clear() {
        failures.clear();
    }

    /** 제한에 걸렸으면 가장 오래된 실패가 창 밖으로 나갈 때까지 남은 초, 아니면 0. */
    private long retryAfterSeconds(String key, int max, Instant now) {
        Deque<Instant> recent = recent(key, now);
        if (recent.size() < max) return 0;
        return Math.max(1, Duration.between(now, recent.peekFirst().plus(window)).toSeconds());
    }

    private Deque<Instant> recent(String key, Instant now) {
        Deque<Instant> times = failures.get(key);
        if (times == null) return new ArrayDeque<>();
        Instant cutoff = now.minus(window);
        while (!times.isEmpty() && !times.peekFirst().isAfter(cutoff)) times.pollFirst();
        if (times.isEmpty()) failures.remove(key);
        return times;
    }

    private void pruneAll(Instant now) {
        failures.keySet().stream().toList().forEach(key -> recent(key, now));
    }

    private static String emailKey(String email) {
        return "email:" + (email == null ? "" : email.trim().toLowerCase(Locale.ROOT));
    }

    private static String ipKey(String ip) {
        return "ip:" + (ip == null ? "unknown" : ip);
    }
}

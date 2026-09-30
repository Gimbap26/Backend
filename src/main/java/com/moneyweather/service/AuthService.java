package com.moneyweather.service;

import com.moneyweather.domain.DefaultCategories;
import com.moneyweather.domain.Enums.UserStatus;
import com.moneyweather.domain.entity.UserEntity;
import com.moneyweather.repository.CategoryRepository;
import com.moneyweather.repository.UserRepository;
import com.moneyweather.security.AuthTokenService;
import com.moneyweather.security.LoginAttemptService;
import com.moneyweather.security.UserContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** 이메일·비밀번호 회원가입, 로그인, 로그아웃, 비밀번호 변경. 비밀번호는 BCrypt 해시로만 저장한다. */
@Service
@Transactional
public class AuthService {
    private static final String INVALID_LOGIN = "이메일 또는 비밀번호가 올바르지 않습니다.";

    private final UserRepository users;
    private final CategoryRepository categories;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService tokens;
    private final LoginAttemptService loginAttempts;
    /** 없는 이메일로 로그인할 때도 비밀번호 검사 시간을 똑같이 써서, 응답 시간으로 가입 여부를 알 수 없게 한다. */
    private final String timingDummyHash;

    public AuthService(UserRepository users, CategoryRepository categories, PasswordEncoder passwordEncoder,
                       AuthTokenService tokens, LoginAttemptService loginAttempts) {
        this.users = users;
        this.categories = categories;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.loginAttempts = loginAttempts;
        this.timingDummyHash = passwordEncoder.encode("timing-equalizer");
    }

    public record User(Long userId, String name, String email, YearMonth baseMonth, UserStatus status) {}

    /** 로그인한 사용자 정보. */
    @Transactional(readOnly = true)
    public User me() {
        UserEntity user = requireCurrentUser();
        return new User(user.getId(), user.getName(), user.getEmail(), YearMonth.parse(user.getBaseMonth()), user.getStatus());
    }

    /** 가입하면 이번 달을 기준 월로 하고 기본 카테고리를 만들어 바로 쓸 수 있게 한다. */
    public Map<String, Object> signup(String email, String password, String name) {
        String normalized = normalize(email);
        if (users.existsByEmail(normalized)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 가입된 이메일입니다.");
        }
        UserEntity user = users.save(new UserEntity(users.nextUserId(), name, YearMonth.now().toString(), UserStatus.ACTIVE,
                normalized, passwordEncoder.encode(password)));
        categories.saveAll(DefaultCategories.forUser(user.getId()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", user.getId());
        body.put("email", user.getEmail());
        body.put("name", user.getName());
        body.putAll(issue(user));
        return body;
    }

    /**
     * 이메일과 비밀번호 중 무엇이 틀렸는지 알려주지 않는다.
     * 실패가 반복되면 비밀번호를 확인하기 전에 막는다({@link LoginAttemptService}).
     */
    @Transactional(readOnly = true)
    public Map<String, Object> login(String email, String password, String clientIp) {
        String normalized = normalize(email);
        loginAttempts.checkAllowed(normalized, clientIp);

        Optional<UserEntity> user = users.findByEmail(normalized);
        String hash = user.map(UserEntity::getPasswordHash).orElse(timingDummyHash);
        boolean matches = passwordEncoder.matches(password, hash);
        if (user.isEmpty() || !matches || user.get().getStatus() != UserStatus.ACTIVE) {
            loginAttempts.recordFailure(normalized, clientIp);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_LOGIN);
        }
        loginAttempts.recordSuccess(normalized);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", user.get().getId());
        body.putAll(issue(user.get()));
        return body;
    }

    /** 이 사용자에게 발급된 모든 토큰을 무효로 만든다(모든 기기에서 로그아웃). */
    public Map<String, Object> logout() {
        UserEntity user = requireCurrentUser();
        user.revokeTokens();
        return Map.of("loggedOut", true, "at", LocalDateTime.now());
    }

    /**
     * 비밀번호를 바꾸고 다른 기기의 기존 토큰을 무효로 만든다.
     * 지금 쓰는 기기는 계속 쓸 수 있도록 새 토큰을 돌려준다.
     */
    public Map<String, Object> changePassword(String currentPassword, String newPassword) {
        UserEntity user = requireCurrentUser();
        // 로그인된 상태이므로 401 이 아니라 400 으로 알린다(401 이면 프론트가 로그인이 풀린 것으로 오해한다)
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "현재 비밀번호가 올바르지 않습니다.");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "새 비밀번호가 현재 비밀번호와 같습니다.");
        }
        user.changePassword(passwordEncoder.encode(newPassword));
        users.flush();
        return issue(user);
    }

    private UserEntity requireCurrentUser() {
        Long userId = UserContext.currentUserId();
        if (userId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required.");
        return users.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required."));
    }

    private Map<String, Object> issue(UserEntity user) {
        return Map.of("tokenType", "Bearer", "accessToken", tokens.issue(user.getId(), user.getTokenVersion()), "expiresIn", tokens.ttlSeconds());
    }

    private static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}

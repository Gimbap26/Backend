package com.moneyweather.security;

import com.moneyweather.domain.Enums.UserStatus;
import com.moneyweather.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * {@code Authorization: Bearer <JWT>} 헤더로 사용자를 식별한다. 다른 방법(헤더·쿼리의 사용자 ID)은 받지 않는다.
 *
 * <p>토큰이 없거나, 위조·만료됐거나, 토큰의 사용자가 없거나 비활성이거나, 로그아웃 등으로 무효화됐으면
 * 인증하지 않은 채로 넘긴다.
 * 공개되지 않은 경로라면 Spring Security 가 401 로 막는다.
 * Spring Security 필터 체인 안에서만 쓰도록 빈으로 등록하지 않는다(서블릿 필터로 중복 등록되지 않게).
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    /** 요청 로그가 인증 결과를 알 수 있도록 남기는 요청 속성. */
    public static final String USER_ID_ATTRIBUTE = "moneyWeather.userId";

    private final AuthTokenService tokens;
    private final UserRepository users;

    public JwtAuthenticationFilter(AuthTokenService tokens, UserRepository users) {
        this.tokens = tokens;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        try {
            resolveActiveUser(request).ifPresent(userId -> {
                UserContext.setCurrentUserId(userId);
                request.setAttribute(USER_ID_ATTRIBUTE, userId);
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(userId, null, List.of()));
            });
            chain.doFilter(request, response);
        } finally {
            UserContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private Optional<Long> resolveActiveUser(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) return Optional.empty();
        // 로그아웃·비밀번호 변경으로 토큰 버전이 올라갔으면, 서명이 맞아도 예전 토큰은 받지 않는다
        return tokens.verify(authorization.substring("Bearer ".length()).trim())
                .filter(claims -> users.findById(claims.userId())
                        .map(u -> u.getStatus() == UserStatus.ACTIVE && u.getTokenVersion() == claims.tokenVersion())
                        .orElse(false))
                .map(AuthTokenService.TokenClaims::userId);
    }
}

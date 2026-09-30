package com.moneyweather.observability;

import com.moneyweather.security.JwtAuthenticationFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * 업무 API 요청을 기록한다. 인증보다 바깥에서 돌아 401 로 막힌 요청도 남긴다.
 * 사용자 ID 는 인증 필터가 남긴 요청 속성에서 읽는다(인증 필터는 요청이 끝나면 UserContext 를 비우므로).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiRequestLoggingFilter extends OncePerRequestFilter {
    private final ApiRequestLogRepository logs;

    public ApiRequestLoggingFilter(ApiRequestLogRepository logs) {
        this.logs = logs;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.startsWith("/h2-console") || path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        long started = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - started) / 1_000_000;
            Object userId = request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE);
            logs.save(new ApiRequestLogEntity(
                    userId instanceof Long id ? id : null,
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    durationMs,
                    request.getRemoteAddr(),
                    request.getHeader("User-Agent"),
                    LocalDateTime.now()
            ));
        }
    }
}

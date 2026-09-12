package com.moneyweather.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UserContextFilter extends OncePerRequestFilter {
    private final AuthTokenService authTokenService;

    public UserContextFilter(AuthTokenService authTokenService) {
        this.authTokenService = authTokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {
            UserContext.setCurrentUserId(resolveUserId(request));
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }

    private Long resolveUserId(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authTokenService.verify(authorization.substring("Bearer ".length())).orElse(null);
        }
        String header = request.getHeader("X-User-Id");
        if (header != null && !header.isBlank()) {
            return parse(header);
        }
        String query = request.getParameter("userId");
        if (query != null && !query.isBlank()) {
            return parse(query);
        }
        return null;
    }

    private Long parse(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}

package com.moneyweather.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneyweather.api.ApiExceptionHandler;
import com.moneyweather.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.security.config.Customizer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 인증 규칙. 로그인·회원가입, 헬스 체크, API 문서만 공개하고 나머지는 JWT 가 있어야 한다.
 * 토큰 기반 API 라 세션을 만들지 않고, 브라우저 폼 제출을 막는 CSRF 보호도 쓰지 않는다.
 */
@Configuration
public class SecurityConfig {
    private static final String[] PUBLIC_PATHS = {
            "/api/v1/auth/signup", "/api/v1/auth/login",
            "/actuator/health/**", "/actuator/info", "/actuator/prometheus",
            "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**",
            "/error"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthTokenService tokens, UserRepository users, ObjectMapper objectMapper,
                                            @Value("${spring.h2.console.enabled:false}") boolean h2ConsoleEnabled) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(PUBLIC_PATHS).permitAll();
                    // 개발 중 H2_CONSOLE_ENABLED=true 로 직접 켰을 때만 연다. 브라우저 콘솔은 JWT 를 보낼 수 없다.
                    if (h2ConsoleEnabled) auth.requestMatchers("/h2-console/**").permitAll();
                    auth.anyRequest().authenticated();
                })
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, ex) -> writeError(response, objectMapper, HttpStatus.UNAUTHORIZED,
                                "UNAUTHORIZED", "로그인이 필요합니다. Authorization: Bearer <토큰> 헤더를 보내세요.", request.getRequestURI()))
                        .accessDeniedHandler((request, response, ex) -> writeError(response, objectMapper, HttpStatus.FORBIDDEN,
                                "FORBIDDEN", "접근 권한이 없습니다.", request.getRequestURI())))
                // H2 콘솔을 켰을 때 프레임 안에서 열리도록 같은 출처 프레임은 허용한다
                .headers(h -> h.frameOptions(f -> f.sameOrigin()))
                .addFilterBefore(new JwtAuthenticationFilter(tokens, users), UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 브라우저에서 다른 주소(프론트 개발 서버 등)로 API 를 부를 수 있게 허용할 출처.
     * 기본은 React 개발 서버(Vite 5173·5174, CRA 3000). 배포할 때는 {@code CORS_ALLOWED_ORIGINS}에 실제 프론트 주소를 쉼표로 넣는다.
     * 토큰은 쿠키가 아니라 Authorization 헤더로 보내므로 자격 증명(쿠키) 전송은 허용하지 않는다.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${money-weather.cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins.stream().map(String::trim).filter(s -> !s.isEmpty()).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private static void writeError(HttpServletResponse response, ObjectMapper objectMapper, HttpStatus status,
                                   String code, String message, String path) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiExceptionHandler.body(status, code, message, path, Map.of()));
    }
}

package com.moneyweather.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class AuthTokenService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final ObjectMapper objectMapper;
    private final String secret;
    private final long ttlSeconds;

    public AuthTokenService(
            ObjectMapper objectMapper,
            @Value("${money-weather.auth.jwt-secret}") String secret,
            @Value("${money-weather.auth.ttl-seconds:86400}") long ttlSeconds
    ) {
        if (secret == null || secret.length() < 16) {
            throw new IllegalStateException("JWT_SECRET 은 16자 이상이어야 합니다.");
        }
        this.objectMapper = objectMapper;
        this.secret = secret;
        this.ttlSeconds = ttlSeconds;
    }

    public long ttlSeconds() {
        return ttlSeconds;
    }

    /** 토큰에서 꺼낸 사용자와, 발급 당시 사용자의 토큰 버전. */
    public record TokenClaims(Long userId, int tokenVersion) {}

    /**
     * @param tokenVersion 발급 시점의 {@code users.token_version}. 로그아웃·비밀번호 변경으로 버전이 오르면
     *                     이 토큰은 서명이 맞아도 거부된다.
     */
    public String issue(Long userId, int tokenVersion) {
        try {
            Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sub", String.valueOf(userId));
            payload.put("ver", tokenVersion);
            payload.put("iat", Instant.now().getEpochSecond());
            payload.put("exp", Instant.now().plusSeconds(ttlSeconds).getEpochSecond());
            String unsigned = encodeJson(header) + "." + encodeJson(payload);
            return unsigned + "." + sign(unsigned);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to issue auth token.", e);
        }
    }

    /** 서명과 만료만 확인한다. 토큰 버전이 사용자의 현재 버전과 같은지는 호출하는 쪽에서 확인한다. */
    public Optional<TokenClaims> verify(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return Optional.empty();
            String unsigned = parts[0] + "." + parts[1];
            if (!constantTimeEquals(sign(unsigned), parts[2])) return Optional.empty();
            Map<String, Object> payload = objectMapper.readValue(base64UrlDecode(parts[1]), new TypeReference<>() {});
            long exp = ((Number) payload.getOrDefault("exp", 0)).longValue();
            if (exp < Instant.now().getEpochSecond()) return Optional.empty();
            int version = ((Number) payload.getOrDefault("ver", 0)).intValue();
            return Optional.of(new TokenClaims(Long.parseLong(String.valueOf(payload.get("sub"))), version));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private String encodeJson(Map<String, Object> value) throws Exception {
        return base64UrlEncode(objectMapper.writeValueAsBytes(value));
    }

    private String sign(String value) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
        return base64UrlEncode(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private String base64UrlEncode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private byte[] base64UrlDecode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private boolean constantTimeEquals(String left, String right) {
        byte[] a = left.getBytes(StandardCharsets.UTF_8);
        byte[] b = right.getBytes(StandardCharsets.UTF_8);
        if (a.length != b.length) return false;
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }
}

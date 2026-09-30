package com.moneyweather.security;

/**
 * 현재 요청을 보낸 사용자 ID. JWT 인증 필터가 요청마다 채우고 요청이 끝나면 비운다.
 * 인증되지 않은 요청이면 {@code null}이다 — 예전처럼 기본 사용자로 대신하지 않는다.
 */
public final class UserContext {
    private static final ThreadLocal<Long> CURRENT_USER_ID = new ThreadLocal<>();

    private UserContext() {
    }

    public static Long currentUserId() {
        return CURRENT_USER_ID.get();
    }

    public static void setCurrentUserId(Long userId) {
        CURRENT_USER_ID.set(userId);
    }

    public static void clear() {
        CURRENT_USER_ID.remove();
    }
}

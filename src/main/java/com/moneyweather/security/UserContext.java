package com.moneyweather.security;

public final class UserContext {
    private static final Long DEFAULT_USER_ID = 1L;
    private static final ThreadLocal<Long> CURRENT_USER_ID = ThreadLocal.withInitial(() -> DEFAULT_USER_ID);

    private UserContext() {
    }

    public static Long currentUserId() {
        return CURRENT_USER_ID.get();
    }

    public static void setCurrentUserId(Long userId) {
        CURRENT_USER_ID.set(userId == null ? DEFAULT_USER_ID : userId);
    }

    public static void clear() {
        CURRENT_USER_ID.remove();
    }
}

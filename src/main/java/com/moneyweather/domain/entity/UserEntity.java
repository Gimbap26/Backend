package com.moneyweather.domain.entity;

import com.moneyweather.domain.Enums.UserStatus;
import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class UserEntity {
    @Id
    private Long id;
    private String name;
    private String baseMonth;
    @Enumerated(EnumType.STRING)
    private UserStatus status;
    private String email;
    private String passwordHash;
    /** 발급한 토큰에 함께 담는 값. 올리면 그 전에 발급한 토큰이 모두 무효가 된다. */
    private int tokenVersion;

    protected UserEntity() {
    }

    public UserEntity(Long id, String name, String baseMonth, UserStatus status) {
        this(id, name, baseMonth, status, null, null);
    }

    public UserEntity(Long id, String name, String baseMonth, UserStatus status, String email, String passwordHash) {
        this.id = id;
        this.name = name;
        this.baseMonth = baseMonth;
        this.status = status;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getBaseMonth() { return baseMonth; }
    public UserStatus getStatus() { return status; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public int getTokenVersion() { return tokenVersion; }

    /** 로그아웃: 모든 기기의 기존 토큰을 무효로 만든다. */
    public void revokeTokens() {
        this.tokenVersion++;
    }

    /** 비밀번호를 바꾸면 다른 기기에 남아 있는 기존 토큰도 함께 무효로 만든다. */
    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
        revokeTokens();
    }
}

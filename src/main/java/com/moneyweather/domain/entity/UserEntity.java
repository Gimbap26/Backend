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

    protected UserEntity() {
    }

    public UserEntity(Long id, String name, String baseMonth, UserStatus status) {
        this.id = id;
        this.name = name;
        this.baseMonth = baseMonth;
        this.status = status;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getBaseMonth() { return baseMonth; }
    public UserStatus getStatus() { return status; }
}

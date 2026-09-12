package com.moneyweather.observability;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "api_request_logs")
public class ApiRequestLogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private String method;
    @Column(length = 1000)
    private String path;
    private int status;
    private long durationMs;
    private String clientIp;
    @Column(length = 1000)
    private String userAgent;
    private LocalDateTime createdAt;

    protected ApiRequestLogEntity() {
    }

    public ApiRequestLogEntity(Long userId, String method, String path, int status, long durationMs, String clientIp, String userAgent, LocalDateTime createdAt) {
        this.userId = userId;
        this.method = method;
        this.path = path;
        this.status = status;
        this.durationMs = durationMs;
        this.clientIp = clientIp;
        this.userAgent = userAgent;
        this.createdAt = createdAt;
    }
}

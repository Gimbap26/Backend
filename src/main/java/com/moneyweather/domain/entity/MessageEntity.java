package com.moneyweather.domain.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "agent_messages")
public class MessageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long conversationId;
    private String role;
    @Column(length = 4000)
    private String content;
    private String sources;
    private LocalDateTime createdAt;

    protected MessageEntity() {
    }

    public MessageEntity(Long conversationId, String role, String content, String sources, LocalDateTime createdAt) {
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.sources = sources;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getConversationId() { return conversationId; }
    public String getRole() { return role; }
    public String getContent() { return content; }
    public String getSources() { return sources; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

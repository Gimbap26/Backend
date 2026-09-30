package com.moneyweather.domain.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "agent_messages")
public class MessageEntity {
    public static final int MAX_CONTENT_LENGTH = 20_000;
    public static final int MAX_TOOL_TRACE_LENGTH = 100_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long conversationId;
    private String role;
    @Column(length = MAX_CONTENT_LENGTH)
    private String content;
    private String sources;
    private LocalDateTime createdAt;
    /** 답변 당시 호출한 도구의 인자와 결과(JSON). 근거 조회 시 재계산하지 않고 이 값을 돌려준다. */
    @Column(length = MAX_TOOL_TRACE_LENGTH)
    private String toolTrace;

    protected MessageEntity() {
    }

    public MessageEntity(Long conversationId, String role, String content, String sources, LocalDateTime createdAt) {
        this(conversationId, role, content, sources, createdAt, null);
    }

    public MessageEntity(Long conversationId, String role, String content, String sources, LocalDateTime createdAt, String toolTrace) {
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.sources = sources;
        this.createdAt = createdAt;
        this.toolTrace = toolTrace;
    }

    public Long getId() { return id; }
    public Long getConversationId() { return conversationId; }
    public String getRole() { return role; }
    public String getContent() { return content; }
    public String getSources() { return sources; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getToolTrace() { return toolTrace; }
}

package com.example.pagebuilder.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_message")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "html_page_id")
    private HtmlPage htmlPage;

    // 저장 전 임시 세션용 (비저장 채팅)
    @Column(name = "session_id", length = 36)
    private String sessionId;

    // USER 또는 ASSISTANT
    @Column(nullable = false, length = 20)
    private String role;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // 어시스턴트 메시지 메타데이터 (생성 정보)
    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "elapsed_ms")
    private Long elapsedMs;

    @Column(name = "token_count")
    private Integer tokenCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Getters & Setters
    public Long getId() { return id; }

    public HtmlPage getHtmlPage() { return htmlPage; }
    public void setHtmlPage(HtmlPage htmlPage) { this.htmlPage = htmlPage; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public Long getElapsedMs() { return elapsedMs; }
    public void setElapsedMs(Long elapsedMs) { this.elapsedMs = elapsedMs; }

    public Integer getTokenCount() { return tokenCount; }
    public void setTokenCount(Integer tokenCount) { this.tokenCount = tokenCount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}

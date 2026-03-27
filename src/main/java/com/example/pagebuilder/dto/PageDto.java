package com.example.pagebuilder.dto;

import com.example.pagebuilder.entity.HtmlPage;

import java.time.LocalDateTime;

public class PageDto {

    private Long id;
    private String uuid;
    private String title;
    private String description;
    private String htmlContent;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String publicUrl;

    private Long projectId;
    private String pageName;

    public static PageDto from(HtmlPage page) {
        PageDto dto = new PageDto();
        dto.id = page.getId();
        dto.uuid = page.getUuid();
        dto.title = page.getTitle() != null ? page.getTitle() : "제목 없음";
        dto.description = page.getDescription();
        dto.htmlContent = page.getHtmlContent();
        dto.createdAt = page.getCreatedAt();
        dto.updatedAt = page.getUpdatedAt();
        dto.publicUrl = "/page/" + page.getUuid();
        dto.pageName = page.getPageName();
        if (page.getProject() != null) dto.projectId = page.getProject().getId();
        return dto;
    }

    // Getters
    public Long getProjectId() { return projectId; }
    public String getPageName() { return pageName; }
    public void setPageName(String pageName) { this.pageName = pageName; }

    public Long getId() { return id; }
    public String getUuid() { return uuid; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getHtmlContent() { return htmlContent; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public String getPublicUrl() { return publicUrl; }

    // Setters (저장 요청용)
    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setHtmlContent(String htmlContent) { this.htmlContent = htmlContent; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    private String sessionId;
    public String getSessionId() { return sessionId; }
}

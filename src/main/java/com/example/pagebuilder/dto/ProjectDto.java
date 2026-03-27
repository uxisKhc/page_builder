package com.example.pagebuilder.dto;

import com.example.pagebuilder.entity.Project;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public class ProjectDto {

    private Long id;
    private String uuid;
    private String name;
    private String description;
    private int pageCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<PageSummary> pages;

    public static class PageSummary {
        public Long id;
        public String pageName;
        public String title;
        public String uuid;

        public PageSummary(Long id, String pageName, String title, String uuid) {
            this.id = id;
            this.pageName = pageName != null ? pageName : (title != null ? title : "페이지 " + id);
            this.title = title;
            this.uuid = uuid;
        }
    }

    public static ProjectDto from(Project p) {
        ProjectDto dto = new ProjectDto();
        dto.id = p.getId();
        dto.uuid = p.getUuid();
        dto.name = p.getName();
        dto.description = p.getDescription();
        dto.createdAt = p.getCreatedAt();
        dto.updatedAt = p.getUpdatedAt();
        dto.pageCount = p.getPages().size();
        dto.pages = p.getPages().stream()
                .map(page -> new PageSummary(page.getId(), page.getPageName(), page.getTitle(), page.getUuid()))
                .collect(Collectors.toList());
        return dto;
    }

    // Getters
    public Long getId() { return id; }
    public String getUuid() { return uuid; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getPageCount() { return pageCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public List<PageSummary> getPages() { return pages; }
}

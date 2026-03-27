package com.example.pagebuilder.dto;

import java.util.List;

public class ChatRequest {

    private String message;
    private String sessionId;        // 비저장 상태 임시 세션
    private Long pageId;             // 기존 페이지 수정 시
    private List<Long> fileIds;      // 참고할 파일 ID 목록
    private String modelId;          // 사용할 모델 ID (null이면 설정값 기본 모델)

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public Long getPageId() { return pageId; }
    public void setPageId(Long pageId) { this.pageId = pageId; }

    public List<Long> getFileIds() { return fileIds; }
    public void setFileIds(List<Long> fileIds) { this.fileIds = fileIds; }

    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }
}

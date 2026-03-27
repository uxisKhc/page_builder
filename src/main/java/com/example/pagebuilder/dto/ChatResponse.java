package com.example.pagebuilder.dto;

public class ChatResponse {

    private boolean success;
    private String message;       // AI의 설명 텍스트
    private String htmlContent;   // 생성된 HTML
    private String sessionId;     // 임시 세션 ID
    private String error;

    public static ChatResponse ok(String message, String htmlContent, String sessionId) {
        ChatResponse r = new ChatResponse();
        r.success = true;
        r.message = message;
        r.htmlContent = htmlContent;
        r.sessionId = sessionId;
        return r;
    }

    public static ChatResponse error(String error) {
        ChatResponse r = new ChatResponse();
        r.success = false;
        r.error = error;
        return r;
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public String getHtmlContent() { return htmlContent; }
    public String getSessionId() { return sessionId; }
    public String getError() { return error; }
}

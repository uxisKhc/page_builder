package com.example.pagebuilder.repository;

import com.example.pagebuilder.entity.ChatMessage;
import com.example.pagebuilder.entity.HtmlPage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByHtmlPageOrderByCreatedAtAsc(HtmlPage htmlPage);
    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(String sessionId);
    void deleteBySessionId(String sessionId);
}

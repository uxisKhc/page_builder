package com.example.pagebuilder.service;

import com.example.pagebuilder.dto.ChatRequest;
import com.example.pagebuilder.dto.ChatResponse;
import com.example.pagebuilder.dto.PageDto;
import com.example.pagebuilder.entity.ChatMessage;
import com.example.pagebuilder.entity.HtmlPage;
import com.example.pagebuilder.entity.Member;
import com.example.pagebuilder.repository.ChatMessageRepository;
import com.example.pagebuilder.repository.HtmlPageRepository;
import com.example.pagebuilder.service.FileParseService.ReferenceData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class PageService {

    private static final Logger log = LoggerFactory.getLogger(PageService.class);

    @Autowired private HtmlPageRepository pageRepository;
    @Autowired private ChatMessageRepository chatMessageRepository;
    @Autowired private FileParseService fileParseService;
    @Autowired private OllamaService ollamaService;

    // ─────────────────────────────────────────
    // 스트리밍용 컨텍스트
    // ─────────────────────────────────────────

    public static class StreamContext {
        public final List<Map<String, String>> history;
        public final String referenceText;
        public final List<String> images;
        public final String sessionId;
        public final Long pageId;

        StreamContext(List<Map<String, String>> history, String referenceText,
                      List<String> images, String sessionId, Long pageId) {
            this.history = history;
            this.referenceText = referenceText;
            this.images = images;
            this.sessionId = sessionId;
            this.pageId = pageId;
        }
    }

    /** 스트리밍 전 히스토리/참고자료 준비 (읽기 전용 트랜잭션) */
    @Transactional(readOnly = true)
    public StreamContext buildStreamContext(ChatRequest request, Member member) {
        ReferenceData ref = fileParseService.collectReference(request.getFileIds(), member);
        List<Map<String, String>> history = new ArrayList<>();
        String sessionId = request.getSessionId();
        Long pageId = request.getPageId();

        if (pageId != null) {
            HtmlPage page = pageRepository.findByIdAndMember(pageId, member)
                    .orElseThrow(() -> new IllegalArgumentException("페이지를 찾을 수 없습니다."));
            List<ChatMessage> existing = chatMessageRepository.findByHtmlPageOrderByCreatedAtAsc(page);
            for (ChatMessage msg : existing) {
                history.add(Map.of("role", msg.getRole().toLowerCase(), "content", msg.getContent()));
            }
            if (history.isEmpty() && page.getHtmlContent() != null) {
                history.add(Map.of("role", "assistant", "content", page.getHtmlContent()));
            }
        } else {
            if (sessionId == null || sessionId.isBlank()) {
                sessionId = UUID.randomUUID().toString();
            }
            List<ChatMessage> sessionMessages =
                    chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
            for (ChatMessage msg : sessionMessages) {
                history.add(Map.of("role", msg.getRole().toLowerCase(), "content", msg.getContent()));
            }
        }
        history.add(Map.of("role", "user", "content", request.getMessage()));
        return new StreamContext(history, ref.text, ref.images, sessionId, pageId);
    }

    /** 스트리밍 완료 후 결과 저장. 신규 세션이면 sessionId 반환, 기존 페이지 수정이면 null */
    public String saveStreamedResult(StreamContext ctx, String generatedHtml, Member member,
                                      String modelName, long elapsedMs, int tokenCount) {
        String userMsg = ctx.history.get(ctx.history.size() - 1).get("content");
        if (ctx.pageId != null) {
            HtmlPage page = pageRepository.findByIdAndMember(ctx.pageId, member)
                    .orElseThrow(() -> new IllegalArgumentException("페이지를 찾을 수 없습니다."));
            page.setHtmlContent(generatedHtml);
            pageRepository.save(page);
            saveMessage(null, page, "user", userMsg, "TEXT", null, null, null);
            saveMessage(null, page, "assistant", generatedHtml, "HTML", modelName, elapsedMs, tokenCount);
            log.info("채팅 메시지 저장 완료 — pageId={}, model={}, elapsed={}ms, tokens={}", ctx.pageId, modelName, elapsedMs, tokenCount);
            return null;
        } else {
            saveMessage(ctx.sessionId, null, "user", userMsg, "TEXT", null, null, null);
            saveMessage(ctx.sessionId, null, "assistant", generatedHtml, "HTML", modelName, elapsedMs, tokenCount);
            log.info("채팅 메시지 저장 완료 — sessionId={}, model={}, elapsed={}ms, tokens={}", ctx.sessionId, modelName, elapsedMs, tokenCount);
            return ctx.sessionId;
        }
    }

    /** 대화형 텍스트 메시지 저장 (HTML 생성 아님) */
    public void saveTextMessage(StreamContext ctx, String userMsg, String aiText, Member member, String modelName) {
        if (ctx.pageId != null) {
            HtmlPage page = pageRepository.findByIdAndMember(ctx.pageId, member)
                    .orElseThrow(() -> new IllegalArgumentException("페이지를 찾을 수 없습니다."));
            saveMessage(null, page, "user", userMsg, "TEXT", null, null, null);
            saveMessage(null, page, "assistant", aiText, "TEXT", modelName, null, null);
        } else {
            saveMessage(ctx.sessionId, null, "user", userMsg, "TEXT", null, null, null);
            saveMessage(ctx.sessionId, null, "assistant", aiText, "TEXT", modelName, null, null);
        }
    }

    /**
     * 채팅 메시지로 HTML 생성 (신규 or 기존 페이지 수정)
     */
    public ChatResponse generateFromChat(ChatRequest request, Member member) {
        try {
            List<Map<String, String>> history = new ArrayList<>();

            // 파일 참고 데이터 수집 (텍스트 + 이미지)
            ReferenceData ref = fileParseService.collectReference(request.getFileIds(), member);

            if (request.getPageId() != null) {
                // ── 기존 페이지 수정 모드 ──
                HtmlPage page = pageRepository.findByIdAndMember(request.getPageId(), member)
                        .orElseThrow(() -> new IllegalArgumentException("페이지를 찾을 수 없습니다."));

                // 기존 채팅 히스토리 로드
                List<ChatMessage> existing = chatMessageRepository.findByHtmlPageOrderByCreatedAtAsc(page);
                for (ChatMessage msg : existing) {
                    history.add(Map.of("role", msg.getRole().toLowerCase(), "content", msg.getContent()));
                }
                // 현재 HTML을 컨텍스트로 (히스토리 없을 때)
                if (history.isEmpty() && page.getHtmlContent() != null) {
                    history.add(Map.of("role", "assistant", "content", page.getHtmlContent()));
                }
                history.add(Map.of("role", "user", "content", request.getMessage()));

                String generatedHtml = ollamaService.generateHtml(history, ref.text, ref.images);
                page.setHtmlContent(generatedHtml);
                pageRepository.save(page);
                saveMessage(null, page, "user", request.getMessage(), "TEXT", null, null, null);
                saveMessage(null, page, "assistant", generatedHtml, "HTML", null, null, null);

                return ChatResponse.ok("페이지가 수정되었습니다.", generatedHtml, null);

            } else {
                // ── 신규 페이지 모드 (세션 기반) ──
                String sessionId = request.getSessionId();
                if (sessionId == null || sessionId.isBlank()) {
                    sessionId = UUID.randomUUID().toString();
                }

                // 세션 히스토리 로드
                List<ChatMessage> sessionMessages =
                        chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
                for (ChatMessage msg : sessionMessages) {
                    history.add(Map.of("role", msg.getRole().toLowerCase(), "content", msg.getContent()));
                }
                history.add(Map.of("role", "user", "content", request.getMessage()));

                String generatedHtml = ollamaService.generateHtml(history, ref.text, ref.images);
                saveMessage(sessionId, null, "user", request.getMessage(), "TEXT", null, null, null);
                saveMessage(sessionId, null, "assistant", generatedHtml, "HTML", null, null, null);

                return ChatResponse.ok("페이지가 생성되었습니다.", generatedHtml, sessionId);
            }

        } catch (Exception e) {
            log.error("HTML 생성 실패", e);
            return ChatResponse.error(e.getMessage());
        }
    }

    /**
     * 세션의 HTML을 페이지로 저장
     */
    public PageDto savePage(String sessionId, String title, String description,
                             String htmlContent, Member member) {
        HtmlPage page = new HtmlPage();
        page.setMember(member);
        page.setTitle(title != null && !title.isBlank() ? title : "제목 없음");
        page.setDescription(description);
        page.setHtmlContent(htmlContent);
        HtmlPage saved = pageRepository.save(page);

        // 세션 메시지를 페이지에 연결
        if (sessionId != null && !sessionId.isBlank()) {
            List<ChatMessage> sessionMsgs = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
            log.info("세션 메시지 이전 — sessionId={}, 메시지 수={}, pageId={}", sessionId, sessionMsgs.size(), saved.getId());
            sessionMsgs.forEach(msg -> {
                msg.setHtmlPage(saved);
                msg.setSessionId(null);
                chatMessageRepository.save(msg);
            });
        }
        return PageDto.from(saved);
    }

    /**
     * 페이지 제목/설명 업데이트
     */
    public PageDto updatePageMeta(Long pageId, String title, String description, Member member) {
        HtmlPage page = pageRepository.findByIdAndMember(pageId, member)
                .orElseThrow(() -> new IllegalArgumentException("페이지를 찾을 수 없습니다."));
        page.setTitle(title);
        page.setDescription(description);
        return PageDto.from(pageRepository.save(page));
    }

    /** 내 페이지 목록 */
    @Transactional(readOnly = true)
    public List<PageDto> getMyPages(Member member) {
        return pageRepository.findByMemberOrderByCreatedAtDesc(member)
                .stream().map(PageDto::from).collect(Collectors.toList());
    }

    /** 페이지 상세 (소유자 확인) */
    @Transactional(readOnly = true)
    public PageDto getMyPage(Long pageId, Member member) {
        return PageDto.from(
                pageRepository.findByIdAndMember(pageId, member)
                        .orElseThrow(() -> new IllegalArgumentException("페이지를 찾을 수 없습니다."))
        );
    }

    /** 공개 UUID로 페이지 조회 */
    @Transactional(readOnly = true)
    public HtmlPage getPublicPage(String uuid) {
        return pageRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("페이지를 찾을 수 없습니다."));
    }

    /** 페이지 삭제 */
    public void deletePage(Long pageId, Member member) {
        HtmlPage page = pageRepository.findByIdAndMember(pageId, member)
                .orElseThrow(() -> new IllegalArgumentException("페이지를 찾을 수 없습니다."));
        pageRepository.delete(page);
    }

    /** 채팅 히스토리 조회 — 페이지 ID로 (에디터 재진입 시) */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getChatHistory(Long pageId, Member member) {
        HtmlPage page = pageRepository.findByIdAndMember(pageId, member)
                .orElseThrow(() -> new IllegalArgumentException("페이지를 찾을 수 없습니다."));
        return chatMessageRepository.findByHtmlPageOrderByCreatedAtAsc(page)
                .stream()
                .map(this::toHistoryMap)
                .collect(Collectors.toList());
    }

    /** 채팅 히스토리 조회 — sessionId로 (새 페이지 새로고침 복원용) */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSessionChatHistory(String sessionId) {
        return chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)
                .stream()
                .map(this::toHistoryMap)
                .collect(Collectors.toList());
    }

    // ── 버전 히스토리 ──────────────────────────────────────────

    /** 버전 목록 (메타데이터만 — HTML 미포함) */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getVersionList(Long pageId, Member member) {
        HtmlPage page = pageRepository.findByIdAndMember(pageId, member)
                .orElseThrow(() -> new IllegalArgumentException("페이지를 찾을 수 없습니다."));
        List<ChatMessage> all = chatMessageRepository.findByHtmlPageOrderByCreatedAtAsc(page);
        List<Map<String, Object>> versions = new ArrayList<>();
        int vNum = 0;
        for (ChatMessage msg : all) {
            if ("assistant".equalsIgnoreCase(msg.getRole()) && !"TEXT".equals(msg.getMsgType())) {
                vNum++;
                Map<String, Object> v = new HashMap<>();
                v.put("id",         msg.getId());
                v.put("versionNum", vNum);
                v.put("createdAt",  msg.getCreatedAt().toString());
                v.put("modelName",  msg.getModelName());
                v.put("elapsedMs",  msg.getElapsedMs());
                v.put("tokenCount", msg.getTokenCount());
                versions.add(v);
            }
        }
        return versions;
    }

    /** 특정 버전 HTML 조회 */
    @Transactional(readOnly = true)
    public String getVersionHtml(Long pageId, Long msgId, Member member) {
        HtmlPage page = pageRepository.findByIdAndMember(pageId, member)
                .orElseThrow(() -> new IllegalArgumentException("페이지를 찾을 수 없습니다."));
        ChatMessage msg = chatMessageRepository.findById(msgId)
                .orElseThrow(() -> new IllegalArgumentException("버전을 찾을 수 없습니다."));
        if (!msg.getHtmlPage().getId().equals(page.getId()))
            throw new IllegalArgumentException("권한이 없습니다.");
        return msg.getContent();
    }

    /** 특정 버전을 게시 버전으로 설정 (page.htmlContent 교체) */
    public void publishVersion(Long pageId, Long msgId, Member member) {
        HtmlPage page = pageRepository.findByIdAndMember(pageId, member)
                .orElseThrow(() -> new IllegalArgumentException("페이지를 찾을 수 없습니다."));
        ChatMessage msg = chatMessageRepository.findById(msgId)
                .orElseThrow(() -> new IllegalArgumentException("버전을 찾을 수 없습니다."));
        if (!msg.getHtmlPage().getId().equals(page.getId()))
            throw new IllegalArgumentException("권한이 없습니다.");
        page.setHtmlContent(msg.getContent());
        pageRepository.save(page);
        log.info("버전 게시 완료 — pageId={}, msgId={}", pageId, msgId);
    }

    private Map<String, Object> toHistoryMap(ChatMessage msg) {
        Map<String, Object> m = new HashMap<>();
        m.put("role", msg.getRole());
        m.put("content", msg.getContent());
        if (msg.getModelName() != null) m.put("modelName", msg.getModelName());
        if (msg.getElapsedMs()  != null) m.put("elapsedMs",  msg.getElapsedMs());
        if (msg.getTokenCount() != null) m.put("tokenCount", msg.getTokenCount());
        return m;
    }

    private void saveMessage(String sessionId, HtmlPage page, String role, String content,
                              String msgType, String modelName, Long elapsedMs, Integer tokenCount) {
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setHtmlPage(page);
        msg.setRole(role);
        msg.setContent(content);
        msg.setMsgType(msgType);
        msg.setModelName(modelName);
        msg.setElapsedMs(elapsedMs);
        msg.setTokenCount(tokenCount);
        chatMessageRepository.save(msg);
    }
}

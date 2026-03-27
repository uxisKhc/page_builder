package com.example.pagebuilder.controller;

import com.example.pagebuilder.dto.PageDto;
import com.example.pagebuilder.entity.Member;
import com.example.pagebuilder.service.MemberService;
import com.example.pagebuilder.service.PageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pages")
public class PageApiController {

    @Autowired private MemberService memberService;
    @Autowired private PageService pageService;

    /**
     * 내 페이지 목록
     * GET /api/pages
     */
    @GetMapping
    public ResponseEntity<List<PageDto>> list(Authentication auth) {
        Member member = memberService.findByUsername(auth.getName());
        return ResponseEntity.ok(pageService.getMyPages(member));
    }

    /**
     * 페이지 상세 조회
     * GET /api/pages/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<PageDto> get(@PathVariable Long id, Authentication auth) {
        Member member = memberService.findByUsername(auth.getName());
        return ResponseEntity.ok(pageService.getMyPage(id, member));
    }

    /**
     * 신규 페이지 저장 (세션 → DB)
     * POST /api/pages
     * Body: { title, description, htmlContent, sessionId }
     */
    @PostMapping
    public ResponseEntity<PageDto> save(@RequestBody PageDto dto, Authentication auth) {
        Member member = memberService.findByUsername(auth.getName());
        PageDto saved = pageService.savePage(
                dto.getSessionId(),
                dto.getTitle(),
                dto.getDescription(),
                dto.getHtmlContent(),
                member
        );
        return ResponseEntity.ok(saved);
    }

    /**
     * 페이지 제목/설명 업데이트
     * PUT /api/pages/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<PageDto> update(@PathVariable Long id,
                                           @RequestBody Map<String, String> body,
                                           Authentication auth) {
        Member member = memberService.findByUsername(auth.getName());
        PageDto updated = pageService.updatePageMeta(id, body.get("title"), body.get("description"), member);
        return ResponseEntity.ok(updated);
    }

    /**
     * 페이지 삭제
     * DELETE /api/pages/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Boolean>> delete(@PathVariable Long id, Authentication auth) {
        Member member = memberService.findByUsername(auth.getName());
        pageService.deletePage(id, member);
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * 채팅 히스토리 조회 — 페이지 ID (에디터 재진입 시)
     * GET /api/pages/{id}/history
     */
    @GetMapping("/{id}/history")
    public ResponseEntity<List<Map<String, Object>>> history(@PathVariable Long id, Authentication auth) {
        Member member = memberService.findByUsername(auth.getName());
        return ResponseEntity.ok(pageService.getChatHistory(id, member));
    }

    /**
     * 채팅 히스토리 조회 — sessionId (새 페이지 새로고침 복원용)
     * GET /api/pages/session-history?sessionId=xxx
     */
    @GetMapping("/session-history")
    public ResponseEntity<List<Map<String, Object>>> sessionHistory(
            @RequestParam String sessionId,
            Authentication auth) {
        return ResponseEntity.ok(pageService.getSessionChatHistory(sessionId));
    }

    /**
     * HTML 직접 저장 (인플레이스 편집 후)
     * PUT /api/pages/{id}/html
     */
    @PutMapping("/{id}/html")
    public ResponseEntity<Map<String, Boolean>> saveHtml(@PathVariable Long id,
                                                          @RequestBody Map<String, String> body,
                                                          Authentication auth) {
        Member member = memberService.findByUsername(auth.getName());
        pageService.updateHtmlContent(id, body.get("htmlContent"), member);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ── 버전 히스토리 ──────────────────────────────────────

    /**
     * 버전 목록 (메타데이터만)
     * GET /api/pages/{id}/versions
     */
    @GetMapping("/{id}/versions")
    public ResponseEntity<List<Map<String, Object>>> versions(
            @PathVariable Long id, Authentication auth) {
        Member member = memberService.findByUsername(auth.getName());
        return ResponseEntity.ok(pageService.getVersionList(id, member));
    }

    /**
     * 특정 버전 HTML 조회
     * GET /api/pages/{id}/versions/{msgId}
     */
    @GetMapping("/{id}/versions/{msgId}")
    public ResponseEntity<Map<String, Object>> versionHtml(
            @PathVariable Long id, @PathVariable Long msgId, Authentication auth) {
        Member member = memberService.findByUsername(auth.getName());
        String html = pageService.getVersionHtml(id, msgId, member);
        return ResponseEntity.ok(Map.of("html", html));
    }

    /**
     * 특정 버전을 게시 버전으로 설정
     * POST /api/pages/{id}/versions/{msgId}/publish
     */
    @PostMapping("/{id}/versions/{msgId}/publish")
    public ResponseEntity<Void> publishVersion(
            @PathVariable Long id, @PathVariable Long msgId, Authentication auth) {
        Member member = memberService.findByUsername(auth.getName());
        pageService.publishVersion(id, msgId, member);
        return ResponseEntity.ok().build();
    }
}

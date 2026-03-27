package com.example.pagebuilder.controller;

import com.example.pagebuilder.dto.ChatRequest;
import com.example.pagebuilder.dto.ChatResponse;
import com.example.pagebuilder.entity.Member;
import com.example.pagebuilder.service.MemberService;
import com.example.pagebuilder.service.OllamaService;
import com.example.pagebuilder.service.PageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/chat")
public class ChatApiController {

    @Autowired private MemberService memberService;
    @Autowired private PageService pageService;
    @Autowired private OllamaService ollamaService;

    private static final ExecutorService streamExecutor = Executors.newCachedThreadPool();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 대화형 텍스트 스트리밍 — 요구사항 수집 (HTML 생성 없음)
     * POST /api/chat/talk
     */
    @PostMapping("/talk")
    public SseEmitter talk(@RequestBody ChatRequest request, Authentication auth) {
        Member member = memberService.findByUsername(auth.getName());
        SseEmitter emitter = new SseEmitter(0L);

        streamExecutor.execute(() -> {
            try {
                PageService.StreamContext ctx = pageService.buildStreamContext(request, member);
                String modelId = request.getModelId();
                String modelName = ollamaService.getModelName(false, modelId);

                emitter.send(SseEmitter.event().name("status")
                        .data("AI가 답변을 준비하고 있습니다... (모델: " + modelName + ")"));

                StringBuilder fullText = new StringBuilder();

                // 히스토리에서 마지막 user 메시지는 이미 포함됨 — 그대로 전달
                List<Map<String, String>> history = new ArrayList<>(ctx.history);

                ollamaService.streamText(
                    history,
                    modelId,
                    token -> {
                        try {
                            fullText.append(token);
                            emitter.send(SseEmitter.event().name("token").data(token));
                        } catch (Exception ignored) {}
                    },
                    (text, tokenCount) -> {
                        try {
                            long elapsed = 0;
                            pageService.saveTextMessage(ctx, request.getMessage(), text, member, modelName);

                            Map<String, Object> payload = new HashMap<>();
                            payload.put("text", text);
                            payload.put("tokenCount", tokenCount);
                            payload.put("modelName", modelName);
                            payload.put("sessionId",
                                ctx.sessionId != null ? ctx.sessionId : "");

                            emitter.send(SseEmitter.event().name("complete")
                                    .data(objectMapper.writeValueAsString(payload)));
                            emitter.complete();
                        } catch (Exception e) {
                            try {
                                emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                                emitter.complete();
                            } catch (Exception ignored) {}
                        }
                    }
                );
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                    emitter.complete();
                } catch (Exception ignored) {}
            }
        });

        return emitter;
    }

    /**
     * Groq Rate limit 사용량 조회
     * GET /api/groq/usage
     */
    @GetMapping("/groq-usage")
    public ResponseEntity<Map<String, Map<String, String>>> groqUsage() {
        return ResponseEntity.ok(ollamaService.getRateLimits());
    }

    /**
     * 채팅으로 HTML 생성 (동기 방식 — 기존 호환)
     * POST /api/chat/generate
     */
    @PostMapping("/generate")
    public ResponseEntity<ChatResponse> generate(@RequestBody ChatRequest request,
                                                  Authentication auth) {
        Member member = memberService.findByUsername(auth.getName());
        ChatResponse response = pageService.generateFromChat(request, member);
        return ResponseEntity.ok(response);
    }

    /**
     * SSE 스트리밍 방식 HTML 생성 — 토큰마다 실시간 전송
     * POST /api/chat/stream
     *
     * SSE 이벤트:
     *   event: status  — 상태 메시지 (모델명 포함)
     *   event: token   — 생성 토큰 (HTML 조각)
     *   event: complete — 완료 JSON {html, tokenCount, modelName, elapsedMs, sessionId}
     *   event: error   — 오류 메시지
     */
    @PostMapping("/stream")
    public SseEmitter stream(@RequestBody ChatRequest request, Authentication auth) {
        Member member = memberService.findByUsername(auth.getName());
        // 타임아웃 0 = 무제한 (codellama는 HTML 생성에 수 분이 걸릴 수 있음)
        SseEmitter emitter = new SseEmitter(0L);

        streamExecutor.execute(() -> {
            long startMs = System.currentTimeMillis();
            try {
                // 히스토리·참고자료 준비 (트랜잭션 내)
                PageService.StreamContext ctx = pageService.buildStreamContext(request, member);
                boolean useVision = ctx.images != null && !ctx.images.isEmpty();
                String modelName = ollamaService.getModelName(useVision, request.getModelId());

                // 상태 메시지 전송
                emitter.send(SseEmitter.event().name("status").data(
                    useVision
                        ? "이미지를 분석하여 HTML을 생성하고 있습니다... (모델: " + modelName + ")"
                        : "AI가 HTML 코드를 생성하고 있습니다... (모델: " + modelName + ")"
                ));

                // 스트리밍 시작
                ollamaService.streamHtml(
                    ctx.history, ctx.referenceText, ctx.images,
                    token -> {
                        try {
                            emitter.send(SseEmitter.event().name("token").data(token));
                        } catch (Exception ignored) {}  // IOException + IllegalStateException
                    },
                    (finalHtml, tokenCount) -> {
                        try {
                            long elapsed = System.currentTimeMillis() - startMs;
                            String savedSessionId = pageService.saveStreamedResult(
                                    ctx, finalHtml, member, modelName, elapsed, tokenCount);

                            Map<String, Object> payload = new HashMap<>();
                            payload.put("html", finalHtml);
                            payload.put("tokenCount", tokenCount);
                            payload.put("modelName", modelName);
                            payload.put("elapsedMs", elapsed);
                            payload.put("sessionId",
                                savedSessionId != null ? savedSessionId : ctx.sessionId);

                            emitter.send(SseEmitter.event().name("complete")
                                    .data(objectMapper.writeValueAsString(payload)));
                            emitter.complete();
                        } catch (Exception e) {
                            try {
                                emitter.send(SseEmitter.event().name("error")
                                        .data("완료 처리 실패: " + e.getMessage()));
                                emitter.complete();
                            } catch (Exception ignored) {}
                        }
                    }
                );

            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                    emitter.complete();
                } catch (Exception ignored) {}
            }
        });

        return emitter;
    }
}

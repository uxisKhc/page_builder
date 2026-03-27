package com.example.pagebuilder.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OllamaService {

    private static final Logger log = LoggerFactory.getLogger(OllamaService.class);

    @Value("${ollama.base-url}")
    private String baseUrl;

    @Value("${ollama.model}")
    private String model;

    @Value("${ollama.vision-model:llava}")
    private String visionModel;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // HTML 생성 시작 지점 (pre-fill — 모델이 이어서 쓰도록 강제)
    static final String PREFILL = "<!DOCTYPE html>\n<html lang=\"ko\">\n<head>\n<meta charset=\"UTF-8\">";

    // ─────────────────────────────────────────
    // 시스템 프롬프트
    // ─────────────────────────────────────────

    private static final String SYSTEM_PROMPT =
        "You are a world-class frontend developer and UI/UX designer. " +
        "Create stunning, production-ready HTML pages that look professional.\n\n" +
        "=== OUTPUT RULE (CRITICAL) ===\n" +
        "Output ONLY valid HTML. Start with <!DOCTYPE html>, end with </html>.\n" +
        "NO explanations. NO markdown. NO text outside HTML tags.\n\n" +
        "=== REQUIRED LIBRARIES (always include all) ===\n" +
        "<link href=\"https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css\" rel=\"stylesheet\">\n" +
        "<link href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\" rel=\"stylesheet\">\n" +
        "<link href=\"https://fonts.googleapis.com/css2?family=Noto+Sans+KR:wght@400;500;700&display=swap\" rel=\"stylesheet\">\n" +
        "<script src=\"https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js\"></script>\n\n" +
        "=== DESIGN STANDARDS ===\n" +
        "1. TYPOGRAPHY: Set body { font-family: 'Noto Sans KR', sans-serif; } always\n" +
        "2. COLOR SYSTEM: Define CSS variables in :root { --primary: #X; --secondary: #Y; } and use consistently\n" +
        "3. CARDS: Always border: none; border-radius: 12px; box-shadow: 0 2px 15px rgba(0,0,0,0.08);\n" +
        "4. BUTTONS: Use rounded-pill for main CTAs; add transition: all 0.2s ease; and hover lift effect\n" +
        "5. SPACING: Use generous padding (py-4, px-4 for sections); proper margin between elements\n" +
        "6. NAVBAR: Include a proper top navbar with brand name, navigation links, and user menu\n" +
        "7. FOOTER: Include a simple, clean footer\n" +
        "8. RESPONSIVE: Mobile-first; use col-md-X col-lg-X breakpoints properly\n\n" +
        "=== QUALITY REQUIREMENTS ===\n" +
        "- Fill with REALISTIC Korean sample data (not placeholder text)\n" +
        "- Add hover effects on all interactive elements\n" +
        "- Include loading/empty states for tables and lists\n" +
        "- Use Font Awesome icons meaningfully throughout\n" +
        "- Add smooth CSS transitions (0.2s ease) on hover states\n" +
        "- Forms: use floating labels, proper validation attributes\n" +
        "- Tables: striped, hover, with pagination controls\n\n" +
        "=== PAGE TYPE GUIDE ===\n" +
        "DASHBOARD: gradient stat cards (4 across top) + chart placeholder + recent activity table\n" +
        "LOGIN/REGISTER: centered card with gradient header, social login buttons, remember me, forgot password\n" +
        "LANDING: hero section with gradient bg + CTA button, features grid, testimonials, pricing table\n" +
        "LIST/TABLE: search bar + filter buttons + sortable table + pagination + bulk action bar\n" +
        "FORM: progress steps indicator (if multi-step), grouped fields, real-time validation hints\n" +
        "PROFILE: cover image area, avatar, stats row, tabbed sections\n\n" +
        "=== MANDATORY CSS TEMPLATE ===\n" +
        "<style>\n" +
        "  body { font-family: 'Noto Sans KR', sans-serif; background: #f8f9fa; }\n" +
        "  :root { --primary: [CHOOSE]; --secondary: [CHOOSE]; }\n" +
        "  .card { border: none !important; border-radius: 12px; box-shadow: 0 2px 15px rgba(0,0,0,0.08); }\n" +
        "  .btn { transition: all 0.2s ease; }\n" +
        "  .btn-primary { background: linear-gradient(135deg, var(--primary), var(--secondary)); border: none; }\n" +
        "  .btn-primary:hover { transform: translateY(-2px); box-shadow: 0 4px 15px rgba(0,0,0,0.2); }\n" +
        "  a { text-decoration: none; }\n" +
        "</style>\n\n" +
        "Language: Korean for all UI text unless user specifies otherwise.";

    private static final String VISION_SYSTEM_PROMPT =
        "You are a world-class frontend developer. The user provided a REFERENCE IMAGE of a UI design.\n" +
        "Recreate this design as a complete, pixel-faithful HTML page.\n\n" +
        "=== OUTPUT RULE (CRITICAL) ===\n" +
        "Output ONLY valid HTML. Start with <!DOCTYPE html>, end with </html>.\n" +
        "NO explanations. NO markdown. NO text outside HTML.\n\n" +
        "=== REQUIRED LIBRARIES ===\n" +
        "<link href=\"https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css\" rel=\"stylesheet\">\n" +
        "<link href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\" rel=\"stylesheet\">\n" +
        "<link href=\"https://fonts.googleapis.com/css2?family=Noto+Sans+KR:wght@400;500;700&display=swap\" rel=\"stylesheet\">\n" +
        "<script src=\"https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js\"></script>\n\n" +
        "=== IMAGE ANALYSIS CHECKLIST ===\n" +
        "1. LAYOUT: sections, columns, sidebar?\n" +
        "2. COLORS: primary color, background, text colors?\n" +
        "3. TYPOGRAPHY: heading vs body sizes and weights?\n" +
        "4. COMPONENTS: cards, tables, charts, forms, buttons?\n" +
        "5. SPACING: dense or spacious?\n" +
        "6. STYLE: flat, shadow/depth, rounded or sharp corners?\n\n" +
        "Replicate faithfully: color palette, layout, typography, components.\n" +
        "Use realistic Korean sample data. Add hover effects.\n" +
        "Language: Korean unless image shows specific text.";

    // ─────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────

    /** 사용할 모델 이름 반환 */
    public String getModelName(boolean useVision) {
        return useVision ? visionModel : model;
    }

    /** 동기 방식 (기존 호환) */
    public String generateHtml(List<Map<String, String>> chatHistory,
                                String referenceText,
                                List<String> imageBase64List) {
        boolean useVision = imageBase64List != null && !imageBase64List.isEmpty();
        String modelName = useVision ? visionModel : model;
        String sysPrompt = useVision ? VISION_SYSTEM_PROMPT : SYSTEM_PROMPT;

        try {
            ObjectNode body = buildMessagesJson(modelName, sysPrompt, chatHistory, referenceText, imageBase64List);
            body.put("stream", false);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);

            ResponseEntity<String> response = restTemplate.postForEntity(baseUrl + "/api/chat", entity, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            String content = PREFILL + root.path("message").path("content").asText();
            return extractHtml(content);

        } catch (Exception e) {
            log.error("동기 Ollama 호출 실패", e);
            throw new RuntimeException("AI 서버 오류: " + e.getMessage());
        }
    }

    /**
     * 스트리밍 방식 — 토큰마다 onToken 호출, 완료 시 onComplete(finalHtml, tokenCount) 호출
     */
    public void streamHtml(List<Map<String, String>> chatHistory,
                            String referenceText,
                            List<String> imageBase64List,
                            Consumer<String> onToken,
                            BiConsumer<String, Integer> onComplete) throws IOException {

        boolean useVision = imageBase64List != null && !imageBase64List.isEmpty();
        String modelName = useVision ? visionModel : model;
        String sysPrompt = useVision ? VISION_SYSTEM_PROMPT : SYSTEM_PROMPT;

        ObjectNode body;
        try {
            body = buildMessagesJson(modelName, sysPrompt, chatHistory, referenceText, imageBase64List);
        } catch (Exception e) {
            throw new IOException("요청 생성 실패: " + e.getMessage(), e);
        }
        body.put("stream", true);
        String requestStr = body.toString();

        URL url = new URL(baseUrl + "/api/chat");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(0); // 무제한 — 스트리밍 생성 중 중단 방지 (10분 이상 걸릴 수 있음)

        try (OutputStream os = conn.getOutputStream()) {
            os.write(requestStr.getBytes(StandardCharsets.UTF_8));
        }

        StringBuilder accumulated = new StringBuilder(PREFILL);
        int tokenCount = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                try {
                    JsonNode chunk = objectMapper.readTree(line);
                    String token = chunk.path("message").path("content").asText();
                    boolean done = chunk.path("done").asBoolean();
                    if (!token.isEmpty()) {
                        accumulated.append(token);
                        tokenCount++;
                        onToken.accept(token);
                    }
                    if (done) break;
                } catch (Exception ignored) { /* 파싱 실패 라인 건너뜀 */ }
            }
        }

        onComplete.accept(extractHtml(accumulated.toString()), tokenCount);
    }

    // ─────────────────────────────────────────
    // 공통: 메시지 JSON 빌더
    // ─────────────────────────────────────────

    private ObjectNode buildMessagesJson(String modelName, String sysPrompt,
                                          List<Map<String, String>> chatHistory,
                                          String referenceText,
                                          List<String> imageBase64List) {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", modelName);

        ArrayNode messages = objectMapper.createArrayNode();

        // 시스템 메시지
        ObjectNode systemMsg = objectMapper.createObjectNode();
        systemMsg.put("role", "system");
        String sysContent = sysPrompt;
        if (referenceText != null && !referenceText.isBlank()) {
            sysContent += "\n\nReference document content:\n" + referenceText;
        }
        systemMsg.put("content", sysContent);
        messages.add(systemMsg);

        // 히스토리 (마지막 메시지 제외)
        for (int i = 0; i < chatHistory.size() - 1; i++) {
            Map<String, String> msg = chatHistory.get(i);
            ObjectNode node = objectMapper.createObjectNode();
            node.put("role", msg.get("role"));
            node.put("content", msg.get("content"));
            messages.add(node);
        }

        // 마지막 사용자 메시지 — HTML 즉시 생성 지시 + 이미지 첨부
        if (!chatHistory.isEmpty()) {
            Map<String, String> last = chatHistory.get(chatHistory.size() - 1);
            ObjectNode lastNode = objectMapper.createObjectNode();
            lastNode.put("role", last.get("role"));
            lastNode.put("content", last.get("content") +
                "\n\n[IMPORTANT: Output ONLY complete HTML code. " +
                "Start immediately with <!DOCTYPE html>. " +
                "Do NOT ask questions. Do NOT explain. Just output the HTML.]");

            if (imageBase64List != null && !imageBase64List.isEmpty()) {
                ArrayNode imagesNode = objectMapper.createArrayNode();
                for (String b64 : imageBase64List) imagesNode.add(b64);
                lastNode.set("images", imagesNode);
            }
            messages.add(lastNode);
        }

        // Pre-fill: 모델이 HTML부터 이어서 생성하도록 강제
        ObjectNode prefill = objectMapper.createObjectNode();
        prefill.put("role", "assistant");
        prefill.put("content", PREFILL);
        messages.add(prefill);

        requestBody.set("messages", messages);
        return requestBody;
    }

    // ─────────────────────────────────────────
    // HTML 추출
    // ─────────────────────────────────────────

    String extractHtml(String raw) {
        if (raw == null || raw.isBlank()) return fallback("내용을 생성할 수 없습니다.");

        String cleaned = raw.replaceAll("(?s)```html\\s*", "").replaceAll("(?s)```\\s*", "").trim();

        Matcher m1 = Pattern.compile("(?si)<!DOCTYPE\\s+html[^>]*>.*?</html>").matcher(cleaned);
        if (m1.find()) return m1.group().trim();

        Matcher m2 = Pattern.compile("(?si)<html[^>]*>.*?</html>").matcher(cleaned);
        if (m2.find()) return "<!DOCTYPE html>\n" + m2.group().trim();

        log.warn("HTML 추출 실패. 원본 앞 200자: {}", raw.substring(0, Math.min(200, raw.length())));
        return fallback(cleaned);
    }

    private String fallback(String msg) {
        return "<!DOCTYPE html><html lang=\"ko\"><head><meta charset=\"UTF-8\"><title>Page</title>" +
               "<link href=\"https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css\" rel=\"stylesheet\">" +
               "</head><body class=\"p-4\"><div class=\"alert alert-warning\"><strong>생성 결과:</strong><br>" +
               msg.replace("<", "&lt;").replace(">", "&gt;") + "</div></body></html>";
    }
}

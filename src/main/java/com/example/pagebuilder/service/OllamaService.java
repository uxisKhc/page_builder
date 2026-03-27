package com.example.pagebuilder.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OllamaService {

    private static final Logger log = LoggerFactory.getLogger(OllamaService.class);

    @Value("${groq.api-key}")
    private String apiKey;

    @Value("${groq.model:llama-3.3-70b-versatile}")
    private String model;

    @Value("${groq.vision-model:meta-llama/llama-4-scout-17b-16e-instruct}")
    private String visionModel;

    @Value("${groq.base-url:https://api.groq.com/openai/v1}")
    private String baseUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 마지막 API 응답의 Rate limit 헤더 (모델별)
    private final Map<String, Map<String, String>> rateLimitCache = new ConcurrentHashMap<>();

    // HTML 생성 시작 지점 (pre-fill)
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

    // 대화형 요구사항 수집 프롬프트
    private static final String CHAT_SYSTEM_PROMPT =
        "당신은 웹 페이지 기획을 도와주는 친절한 AI 컨설턴트입니다.\n" +
        "사용자와 대화를 통해 만들고 싶은 웹 페이지의 요구사항을 파악하세요.\n\n" +
        "== 파악할 내용 ==\n" +
        "1. 페이지 종류 (대시보드, 랜딩 페이지, 로그인, 목록, 폼 등)\n" +
        "2. 주요 기능과 포함할 섹션\n" +
        "3. 색상/스타일 선호도 (밝은/어두운, 색상 테마)\n" +
        "4. 표시할 데이터나 콘텐츠\n" +
        "5. 대상 사용자\n\n" +
        "== 규칙 ==\n" +
        "- 한 번에 한 가지 질문만 하세요.\n" +
        "- 답변은 3~5줄 이내로 간결하게 하세요.\n" +
        "- HTML 코드를 절대 출력하지 마세요.\n" +
        "- 요구사항이 충분히 파악되면 (보통 2~3번 대화 후) 마지막에 반드시 이 문구를 포함하세요:\n" +
        "  '✅ 요구사항 정리 완료! [HTML 생성] 버튼을 눌러 페이지를 만들어보세요.'\n" +
        "- 항상 한국어로 답변하세요.";

    // ─────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────

    /** 사용할 모델 이름 반환 (modelId 우선, 없으면 설정값) */
    public String getModelName(boolean useVision) {
        return useVision ? visionModel : model;
    }

    public String getModelName(boolean useVision, String modelId) {
        if (modelId != null && !modelId.isBlank()) return modelId;
        return getModelName(useVision);
    }

    /** Rate limit 정보 반환 */
    public Map<String, Map<String, String>> getRateLimits() {
        return Collections.unmodifiableMap(rateLimitCache);
    }

    /**
     * 대화형 텍스트 스트리밍 — HTML 생성 없이 요구사항 수집 대화
     */
    public void streamText(List<Map<String, String>> chatHistory,
                           String modelId,
                           Consumer<String> onToken,
                           BiConsumer<String, Integer> onComplete) throws IOException {
        String modelName = (modelId != null && !modelId.isBlank()) ? modelId : model;

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", modelName);
        body.put("stream", true);
        body.put("temperature", 0.7);
        body.put("max_tokens", 1024);

        ArrayNode messages = objectMapper.createArrayNode();

        // 시스템 프롬프트
        ObjectNode sysMsg = objectMapper.createObjectNode();
        sysMsg.put("role", "system");
        sysMsg.put("content", CHAT_SYSTEM_PROMPT);
        messages.add(sysMsg);

        // 전체 히스토리
        for (Map<String, String> msg : chatHistory) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("role", msg.get("role"));
            node.put("content", msg.get("content"));
            messages.add(node);
        }
        body.set("messages", messages);

        URL url = new URL(baseUrl + "/chat/completions");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(60_000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }

        StringBuilder accumulated = new StringBuilder();
        int tokenCount = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            captureRateLimits(conn, modelName);
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || !line.startsWith("data: ")) continue;
                String data = line.substring(6).trim();
                if ("[DONE]".equals(data)) break;
                try {
                    JsonNode chunk = objectMapper.readTree(data);
                    String token = chunk.path("choices").path(0).path("delta").path("content").asText();
                    if (!token.isEmpty()) {
                        accumulated.append(token);
                        tokenCount++;
                        onToken.accept(token);
                    }
                } catch (Exception ignored) {}
            }
        }
        onComplete.accept(accumulated.toString(), tokenCount);
    }

    /** 동기 방식 (기존 호환) */
    public String generateHtml(List<Map<String, String>> chatHistory,
                                String referenceText,
                                List<String> imageBase64List) {
        boolean useVision = imageBase64List != null && !imageBase64List.isEmpty();
        String modelName = useVision ? visionModel : model;
        String sysPrompt = useVision ? VISION_SYSTEM_PROMPT : SYSTEM_PROMPT;

        try {
            ObjectNode body = buildRequestJson(modelName, sysPrompt, chatHistory, referenceText, imageBase64List, false);
            String responseStr = sendRequest(body.toString());
            JsonNode root = objectMapper.readTree(responseStr);
            String content = root.path("choices").path(0).path("message").path("content").asText();
            return extractHtml(PREFILL + content);
        } catch (Exception e) {
            log.error("Groq 동기 호출 실패", e);
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
            body = buildRequestJson(modelName, sysPrompt, chatHistory, referenceText, imageBase64List, true);
        } catch (Exception e) {
            throw new IOException("요청 생성 실패: " + e.getMessage(), e);
        }

        URL url = new URL(baseUrl + "/chat/completions");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(0); // 무제한

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }

        StringBuilder accumulated = new StringBuilder(PREFILL);
        int tokenCount = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            captureRateLimits(conn, modelName);
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || !line.startsWith("data: ")) continue;
                String data = line.substring(6).trim();
                if ("[DONE]".equals(data)) break;
                try {
                    JsonNode chunk = objectMapper.readTree(data);
                    String token = chunk.path("choices").path(0).path("delta").path("content").asText();
                    if (!token.isEmpty()) {
                        accumulated.append(token);
                        tokenCount++;
                        onToken.accept(token);
                    }
                } catch (Exception ignored) {}
            }
        }

        onComplete.accept(extractHtml(accumulated.toString()), tokenCount);
    }

    // ─────────────────────────────────────────
    // 내부 유틸
    // ─────────────────────────────────────────

    /** Groq 응답 헤더에서 Rate limit 정보 캡처 */
    private void captureRateLimits(HttpURLConnection conn, String modelName) {
        Map<String, String> limits = new HashMap<>();
        List<String> keys = List.of(
            "x-ratelimit-limit-requests", "x-ratelimit-remaining-requests",
            "x-ratelimit-limit-tokens",   "x-ratelimit-remaining-tokens",
            "x-ratelimit-reset-requests", "x-ratelimit-reset-tokens"
        );
        for (String key : keys) {
            String val = conn.getHeaderField(key);
            if (val != null) limits.put(key, val);
        }
        if (!limits.isEmpty()) rateLimitCache.put(modelName, limits);
    }

    private String sendRequest(String requestBody) throws IOException {
        URL url = new URL(baseUrl + "/chat/completions");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(60_000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(requestBody.getBytes(StandardCharsets.UTF_8));
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private ObjectNode buildRequestJson(String modelName, String sysPrompt,
                                        List<Map<String, String>> chatHistory,
                                        String referenceText,
                                        List<String> imageBase64List,
                                        boolean stream) {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", modelName);
        requestBody.put("stream", stream);
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 8192);

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

            String userText = last.get("content") +
                "\n\n[IMPORTANT: Output ONLY complete HTML code. " +
                "Start immediately with <!DOCTYPE html>. " +
                "Do NOT ask questions. Do NOT explain. Just output the HTML.]";

            boolean hasImages = imageBase64List != null && !imageBase64List.isEmpty();
            if (hasImages) {
                // 비전: content를 배열 형태로 (OpenAI vision 형식)
                ArrayNode contentArr = objectMapper.createArrayNode();

                ObjectNode textPart = objectMapper.createObjectNode();
                textPart.put("type", "text");
                textPart.put("text", userText);
                contentArr.add(textPart);

                for (String b64 : imageBase64List) {
                    ObjectNode imgPart = objectMapper.createObjectNode();
                    imgPart.put("type", "image_url");
                    ObjectNode imgUrl = objectMapper.createObjectNode();
                    imgUrl.put("url", "data:" + detectMimeType(b64) + ";base64," + b64);
                    imgPart.set("image_url", imgUrl);
                    contentArr.add(imgPart);
                }
                lastNode.set("content", contentArr);
            } else {
                lastNode.put("content", userText);
            }
            messages.add(lastNode);
        }

        // Pre-fill: 모델이 HTML부터 이어서 생성하도록 유도
        ObjectNode prefill = objectMapper.createObjectNode();
        prefill.put("role", "assistant");
        prefill.put("content", PREFILL);
        messages.add(prefill);

        requestBody.set("messages", messages);
        return requestBody;
    }

    /** base64 앞부분으로 이미지 MIME 타입 감지 */
    private String detectMimeType(String base64) {
        if (base64.startsWith("/9j/"))   return "image/jpeg";
        if (base64.startsWith("iVBOR")) return "image/png";
        if (base64.startsWith("R0lGOD")) return "image/gif";
        if (base64.startsWith("UklGR")) return "image/webp";
        return "image/png"; // 기본값
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

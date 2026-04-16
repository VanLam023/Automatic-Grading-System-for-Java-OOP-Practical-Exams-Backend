package agsfjope.backend.infrastructure.ai;

import agsfjope.backend.core.entities.SystemConfig;
import agsfjope.backend.core.repositories.config.SystemConfigRepository;
import agsfjope.backend.infrastructure.security.AesEncryptionUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * AI OOP Review Service — provider-agnostic.
 *
 * <h3>Design: Strategy Pattern</h3>
 * The correct {@link LLMAdapter} is selected at runtime from {@link LLMAdapterFactory}
 * based on the {@code AI_PROVIDER} value in {@code SystemConfig}.
 * Switching providers (Gemini → ChatGPT → OpenRouter) requires only a config change —
 * no code changes needed.
 *
 * <h3>2-Prompt Strategy:</h3>
 * <ol>
 *   <li><b>Prompt 1 (Analysis)</b>: Provide exam question UML context + student source code.
 *       AI performs deep OOP analysis across 5 criteria (encapsulation, inheritance,
 *       polymorphism, design quality, code integrity / anti-cheat).</li>
 *   <li><b>Prompt 2 (Result)</b>: Ask AI to return its analysis as structured JSON
 *       with per-criterion scores, violations list, hard-coded values, and overall verdict.</li>
 * </ol>
 *
 * <p>SystemConfig keys used:</p>
 * <ul>
 *   <li>{@code AI_PROVIDER} — provider name or URL (e.g., "gemini", "openai", "https://...")</li>
 *   <li>{@code AI_API_KEY} (AES-encrypted) — API key</li>
 *   <li>{@code AI_MODEL} — model ID (e.g., "gemini-2.0-flash", "gpt-4o")</li>
 *   <li>{@code AI_LANGUAGE} — language for review comments (e.g., "Vietnamese")</li>
 * </ul>
 *
 * <p>If AI call fails → {@link AIReviewResult#failure} is returned — the grading pipeline
 * continues without interruption.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LLMReviewService {

    private static final int MAX_SOURCE_CHARS = 12_000;

    private final SystemConfigRepository systemConfigRepository;
    private final AesEncryptionUtil      encryptionUtil;
    private final ObjectMapper           objectMapper;
    private final LLMAdapterFactory      adapterFactory;

    // ─── PUBLIC API ──────────────────────────────────────────────────────────

    /**
     * Evaluates student source code against the exam question's OOP requirements.
     *
     * @param request exam question context + student source code + target language
     * @return AI evaluation result; never null — fails gracefully with {@link AIReviewResult#failure}
     */
    public AIReviewResult review(AIReviewRequest request) {
        // Lấy cấu hình AI từ DB (provider, model, api key, ngôn ngữ phản hồi)
        AIConfig config;
        try {
            config = loadConfig();
        } catch (Exception e) {
            log.error("AI config load failed: {}", e.getMessage());
            // AI chưa cấu hình — trả về failure nhưng không crash pipeline chấm bài
            return AIReviewResult.failure("AI chưa được cấu hình: " + e.getMessage());
        }

        // Chọn Adapter phù hợp theo provider (Gemini, OpenAI, hay URL tương thích OpenAI)
        LLMAdapter adapter = adapterFactory.getAdapter(config.provider());

        // Bảo vệ: không thể chấm bài nếu không có source code — bỏ qua luôn, không lỗi
        // Guard: cannot review without source code
        if (request.sourceCode() == null || request.sourceCode().isBlank()) {
            log.warn("AI review skipped for question '{}' — no student source code available.",
                    request.questionTitle());
            return AIReviewResult.failure("AI review skipped: no student Java source files found in submission.");
        }

        try {
            log.debug("AI review: provider={}, model={}, question={}",
                    config.provider(), config.model(), request.questionTitle());

            // [PERF-STEP2] Use callWithRetry() instead of direct adapter calls to handle
            // Gemini rate limits / transient errors without failing the whole grading.
            // Sử dụng callWithRetry() thay cho gọi trực tiếp adapter — tự động thử lại khi gặp lỗi.

            // Chiến lược 2-PROMPT:
            //   Prompt 1: yêu cầu AI phân tích sâu — kết quả là văn bản tự do
            //   Prompt 2: yêu cầu AI đóng gói phân tích thành JSON có cấu trúc để hệ thống đọc được
            // Tách 2 bước giúp tăng chất lượng phân tích không bị ảnh hưởng bởi format output.

            // Prompt 1: Deep OOP analysis with exam context
            // [OLD] String analysis = adapter.chat(
            //         buildAnalysisPrompt(request), config.apiKey(), config.model());
            String analysis = callWithRetry(adapter, buildAnalysisPrompt(request),
                    config.apiKey(), config.model(), false, request.questionTitle());

            // Prompt 2: Return structured JSON — use chatJson() for providers that support
            // JSON mode (responseMimeType) to avoid markdown wrapping and truncation.
            // [OLD] String resultJson = adapter.chatJson(
            //         buildResultPrompt(analysis, config.language()), config.apiKey(), config.model());
            // Prompt 2: Bước này dùng chatJson() — với Gemini sẽ bật JSON mode để tránh markdown wrapper
            String resultJson = callWithRetry(adapter, buildResultPrompt(analysis, config.language(), request.maxScore()),
                    config.apiKey(), config.model(), true, request.questionTitle());

            // Phân tích JSON trả về thành AIReviewResult object
            return parseResult(resultJson, request.maxScore());

        } catch (Exception e) {
            // Mọi lỗi đều được bắt ở đây — trả về failure không crash luồng chấm bài
            log.error("AI review failed [provider={}, question={}]: {}",
                    config.provider(), request.questionTitle(), e.getMessage());
            return AIReviewResult.failure("AI trả về lỗi: " + e.getMessage());
        }
    }

    // ─── RETRY HELPER ────────────────────────────────────────────────────────

    /**
     * [PERF-STEP2] Calls AI adapter with exponential-backoff retry.
     *
     * <p>Motivation: Gemini rate limits (429) or transient network errors can cause
     * the response to be empty or the JSON to be truncated mid-stream. Retrying
     * with a short pause almost always succeeds on the 2nd attempt.
     *
     * @param adapter      the LLM adapter to call
     * @param prompt       the prompt to send
     * @param apiKey       provider API key
     * @param model        model ID
     * @param jsonMode     true → call {@code chatJson()}, false → call {@code chat()}
     * @param questionHint short label for log messages (e.g. question title)
     * @return response text from the model
     * @throws Exception re-thrown after all retries exhausted
     */
    private String callWithRetry(LLMAdapter adapter, String prompt,
                                  String apiKey, String model,
                                  boolean jsonMode, String questionHint) throws Exception {
        // [PERF-STEP2] Max 3 attempts: original + 2 retries
        // Tối đa 3 lần: lần 1 (gọc) + 2 lần thử lại nếu gặp rate limit hoặc network error
        int maxAttempts = 3;
        // Base delay in ms; doubles each retry: 2s → 4s → 6s
        // Thời gian chờ tăng dần: lần 1 thất bại chờ 2s, lần 2 chờ 4s (exponential backoff nhẹ)
        long baseDelayMs = 2_000L;

        Exception lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                // Gọi AI theo mode: chatJson() cho prompt 2, chat() cho prompt 1
                return jsonMode
                        ? adapter.chatJson(prompt, apiKey, model)
                        : adapter.chat(prompt, apiKey, model);
            } catch (Exception e) {
                lastException = e;
                if (attempt < maxAttempts) {
                    long delayMs = baseDelayMs * attempt; // 2s, 4s, (would be 6s but no 3rd delay)
                    // Chưa hết số lần thử — log cảnh báo và chờ rồi thử lại
                    log.warn("[AI-RETRY] Attempt {}/{} failed for question '{}': {}. Retrying in {}ms...",
                            attempt, maxAttempts, questionHint, e.getMessage(), delayMs);
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        // Thread bị interrupt giữa chờ — phuc hồi cờ interrupt và throw ngay
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("AI retry interrupted", ie);
                    }
                } else {
                    // Đã hết số lần thử — log error, throw ra ngoài cho review() bắt và trả về failure
                    log.error("[AI-RETRY] All {} attempts failed for question '{}': {}",
                            maxAttempts, questionHint, e.getMessage());
                }
            }
        }
        throw lastException;
    }

    // ─── PROMPT BUILDERS ─────────────────────────────────────────────────────

    private String buildAnalysisPrompt(AIReviewRequest request) {
        String src = request.sourceCode();
        if (src != null && src.length() > MAX_SOURCE_CHARS) {
            src = src.substring(0, MAX_SOURCE_CHARS) + "\n... [TRUNCATED]";
        }

        return """
                You are an expert Java OOP examiner. Your task is to evaluate a student's Java code submission \
                against the exam question requirements and provide a detailed OOP analysis.

                ═══════════════════════════════════════════════
                EXAM QUESTION CONTEXT
                ═══════════════════════════════════════════════
                Title: %s

                Description and Class Diagram:
                %s

                IMPORTANT — Reading the class diagram:
                • Fields/methods starting with "-" are PRIVATE (must be private in code)
                • Fields/methods starting with "+" are PUBLIC (must be public in code)
                • "has-a" relationship MUST use a collection (ArrayList, List, Set, Map, array, or any \
                  appropriate data structure) — NOT extends
                • "is-a" relationship MUST use extends/implements — NOT a collection field

                ═══════════════════════════════════════════════
                STUDENT SOURCE CODE
                ═══════════════════════════════════════════════
                %s

                ═══════════════════════════════════════════════
                GRADING INSTRUCTIONS
                ═══════════════════════════════════════════════
                The exam description above specifies the OOP criteria for THIS specific question.
                Read and follow ONLY those criteria. Do NOT evaluate any OOP criterion that is
                not mentioned in the exam description.

                // ─── DEFAULT 5-CRITERIA REMOVED — criteria now come from the exam description ──────────────
                // A. ENCAPSULATION (0–2):
                //    • Are all fields marked private as required by the diagram?
                //    • Are getter/setter methods provided for ALL private fields?
                //    • Is data hidden and accessed only through methods?
                //
                // B. INHERITANCE & RELATIONSHIPS (0–2):
                //    • Are "has-a" relationships implemented using the correct data structure — NOT extends?
                //    • Are "is-a" relationships implemented using extends/implements — NOT a collection field?
                //    • Check ALL data structures and relationships, not just ArrayList
                //    • Are extends/implements used exactly as specified in the diagram?
                //
                // C. POLYMORPHISM (0–2):
                //    • Are abstract classes/methods used correctly per the diagram?
                //    • Are interfaces implemented correctly per the diagram?
                //    • Is method overriding correct (same signature, @Override annotation)?
                //
                // D. DESIGN QUALITY (0–2):
                //    • Are methods placed in the correct class (no misplaced logic)?
                //    • Does the code follow Single Responsibility per the diagram?
                //    • Is the naming consistent and structure clean?
                // ─────────────────────────────────────────────────────────────────────────────────────────────

                ANTI-CHEAT RULE (ALWAYS APPLY for every question, regardless of exam criteria):
                Hardcode means the student DELIBERATELY returns a fixed value to pass a test
                instead of implementing the correct algorithm.

                ✅ NOT hardcode (LEGITIMATE constants — do NOT flag):
                • Error/format messages required by the problem: e.g., wrong format, Invalid
                • String prefixes/suffixes from the spec: e.g., New_, DATA_
                • File extensions or filenames: e.g., .txt, data.txt, New_DATA.txt
                • Status labels from the problem: e.g., PASS, FAIL, ACTIVE
                • toString() format strings matching the required output format in the spec

                ❌ TRUE hardcode (flag ONLY these):
                • Returning exact expected output without computation: e.g., return Student[S001, John, 3.5]
                • Returning a fixed number instead of computing it: e.g., return 8.5 instead of return this.score
                • Checking specific input to return specific answer: e.g., if (id.equals(S001)) return 3.5
                • All meaningful logic placed inside main() to bypass class structure

                Perform a thorough analysis. Note ALL violations with specific examples from the code.
                """.formatted(
                request.questionTitle(),
                request.questionDescription() != null ? request.questionDescription() : "(no description)",
                src != null ? src : "(no source code)"
        );
    }

    private String buildResultPrompt(String analysis, String language, java.math.BigDecimal maxScore) {
        // Lấy điểm tối đa của câu từ đề thi; fallback về 10 nếu null (không nên xảy ra)
        String maxScoreStr = (maxScore != null) ? maxScore.stripTrailingZeros().toPlainString() : "10";

        return """
                Below is your OOP analysis of a student's Java submission:

                ─── ANALYSIS ───────────────────────────────────────────────────
                %s
                ────────────────────────────────────────────────────────────────

                Based on the analysis above, return a structured JSON evaluation result.

                RULES:
                1. Return ONLY valid JSON — no markdown, no text outside JSON
                2. All comments and violation descriptions must be in: %s
                3. oopScore = total earned score for this question (max = %s as defined in the exam).
                   Distribute points proportionally across the applicable criteria listed in the exam description.
                     4. isOopViolated = true if the student earned LESS THAN 50%% of the max score
                         (i.e., oopScore < %s * 0.5).
                         Hardcode findings must be reflected in score deduction/comments, but do NOT
                         automatically set isOopViolated=true unless the final criteria-based result justifies it.
                5. The "comment" field MUST list each graded criterion on a separate numbered line:
                   - Student meets the criterion:  "[N]. [CriterionName]: [score] điểm - [specific positive feedback, cite class/method names]"
                   - Student violates the criterion: "[N]. [CriterionName]: -[deduction] điểm - [specific violation with code example]"
                   - Anti-cheat (Code Integrity) MUST always be the LAST item in the list.
                     6. Include "violations" ONLY IF violations are detected.
                         If no violations are found, OMIT the "violations" field entirely.
                     7. Include "hardCodedValues" ONLY IF true hardcode/cheat is detected.
                         If no hardcode is found, OMIT the "hardCodedValues" field entirely.
                         Do NOT include error messages, format strings, or spec-required constants.

                Return exactly this JSON:
                {
                  "oopScore": <number 0-%s>,
                                    "violations": ["<specific violation with code example>"] (optional; omit if none),
                                    "hardCodedValues": ["<only TRUE cheat values — NOT error messages or spec-required constants>"] (optional; omit if none),
                  "comment": "<numbered list of criteria results in %s — follow format from RULE 5. Each criterion on a new line.>",
                  "isOopViolated": <true|false>
                }
                """.formatted(analysis, language, maxScoreStr, maxScoreStr, maxScoreStr, language);
    }

    // ─── RESULT PARSING ──────────────────────────────────────────────────────

    /**
     * Parses the AI response for the new flexible prompt format.
     *
     * <p>The new format does NOT include a {@code criteriaBreakdown} block —
     * criteria results are embedded in the free-text {@code comment} field
     * as a numbered list. All breakdown fields are set to ZERO.
     * Use {@link #parseResultLegacy(String)} if you need per-criterion scores.
     */
    private AIReviewResult parseResult(String rawJson, BigDecimal maxScore) {
        try {
            String json = extractJsonBlock(rawJson);
            JsonNode node = objectMapper.readTree(json);

            // Validate bắt buộc field quan trọng
            JsonNode oopScoreNode = node.path("oopScore");
            String comment = node.path("comment").asText("");
            if (oopScoreNode.isMissingNode() || oopScoreNode.isNull() || comment.isBlank()) {
                throw new IllegalArgumentException("Missing required fields: oopScore/comment");
            }

            // Lấy tổng điểm OOP và ép về miền hợp lệ [0..maxScore]
            BigDecimal parsedScore = asBigDecimal(oopScoreNode);
            BigDecimal safeMax = (maxScore != null && maxScore.compareTo(BigDecimal.ZERO) > 0)
                    ? maxScore : BigDecimal.TEN;
            BigDecimal oopScore = parsedScore.max(BigDecimal.ZERO).min(safeMax);
            boolean oopViolated  = node.path("isOopViolated").asBoolean(false);

            // Danh sách vi phạm và hardcode (null-safe)
            JsonNode violationsNode = node.path("violations");
            List<String> violations = violationsNode.isMissingNode() || violationsNode.isNull()
                    ? Collections.emptyList()
                    : objectMapper.convertValue(violationsNode, new TypeReference<>() {});
            if (violations == null) {
                violations = Collections.emptyList();
            }

            JsonNode hardCodedNode = node.path("hardCodedValues");
            List<String> hardCoded = hardCodedNode.isMissingNode() || hardCodedNode.isNull()
                    ? Collections.emptyList()
                    : objectMapper.convertValue(hardCodedNode, new TypeReference<>() {});
            if (hardCoded == null) {
                hardCoded = Collections.emptyList();
            }

            // criteriaBreakdown không còn trong format mới — đặt về ZERO
            return new AIReviewResult(
                    oopScore,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO,
                    violations, hardCoded,
                    comment, oopViolated,
                    false, null
            );
        } catch (Exception e) {
            log.warn("Flexible parser failed, trying legacy parser: {}", e.getMessage());
            AIReviewResult legacy = parseResultLegacy(rawJson);
            if (!legacy.aiError()) {
                // Legacy parser thành công
                return legacy;
            }
            log.error("Failed to parse AI result JSON (both flexible and legacy): {}", e.getMessage());
            return AIReviewResult.failure("Failed to parse AI JSON: " + e.getMessage());
        }
    }

    /**
     * Legacy parser — kept for backward compatibility or rollback.
     *
     * <p>Parses the OLD 5-criteria fixed prompt format which includes a
     * {@code criteriaBreakdown} JSON block with per-criterion scores
     * (encapsulation, inheritance, polymorphism, designQuality, codeIntegrity).
     * Use this if you need to switch back to the old rigid grading format.
     */
    private AIReviewResult parseResultLegacy(String rawJson) {
        try {
            String json = extractJsonBlock(rawJson);
            JsonNode node = objectMapper.readTree(json);

            BigDecimal oopScore      = asBigDecimal(node.path("oopScore"));
            JsonNode   bd            = node.path("criteriaBreakdown");
            BigDecimal encapsulation = asBigDecimal(bd.path("encapsulation"));
            BigDecimal inheritance   = asBigDecimal(bd.path("inheritance"));
            BigDecimal polymorphism  = asBigDecimal(bd.path("polymorphism"));
            BigDecimal designQuality = asBigDecimal(bd.path("designQuality"));
            BigDecimal codeIntegrity = asBigDecimal(bd.path("codeIntegrity"));

            List<String> violations = objectMapper.convertValue(
                    node.path("violations"), new TypeReference<>() {});
            if (violations == null) {
                violations = Collections.emptyList();
            }
            List<String> hardCoded  = objectMapper.convertValue(
                    node.path("hardCodedValues"), new TypeReference<>() {});
            if (hardCoded == null) {
                hardCoded = Collections.emptyList();
            }
            String  comment         = node.path("comment").asText("");
            boolean oopViolated     = node.path("isOopViolated").asBoolean(false);

            return new AIReviewResult(
                    oopScore, encapsulation, inheritance, polymorphism,
                    designQuality, codeIntegrity,
                    violations, hardCoded,
                    comment, oopViolated,
                    false, null
            );
        } catch (Exception e) {
            log.error("Failed to parse AI result JSON (legacy): {}", e.getMessage());
            return AIReviewResult.failure("Failed to parse AI JSON: " + e.getMessage());
        }
    }

    /** Strips markdown fences and extracts the first {...} JSON block. */
    private String extractJsonBlock(String raw) {
        if (raw == null) return "{}";
        String s = raw.trim();
        if (s.startsWith("```")) {
            int nl = s.indexOf('\n');
            if (nl != -1) s = s.substring(nl + 1);
            if (s.endsWith("```")) s = s.substring(0, s.length() - 3).trim();
        }
        int start = s.indexOf('{');
        int end   = s.lastIndexOf('}');
        return (start != -1 && end > start) ? s.substring(start, end + 1) : s;
    }

    private BigDecimal asBigDecimal(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? BigDecimal.ZERO : node.decimalValue();
    }

    // ─── CONFIG ──────────────────────────────────────────────────────────────

    private AIConfig loadConfig() {
        List<SystemConfig> configs = systemConfigRepository.findByConfigKeyIn(
                List.of("AI_PROVIDER", "AI_API_KEY", "AI_MODEL", "AI_LANGUAGE"));

        Map<String, String> map = new java.util.HashMap<>();
        for (SystemConfig c : configs) {
            String value = Boolean.TRUE.equals(c.getIsEncrypted())
                    ? encryptionUtil.decrypt(c.getConfigValue())
                    : c.getConfigValue();
            map.put(c.getConfigKey(), value);
        }

        String apiKey = map.get("AI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("AI_API_KEY không được cấu hình trong SystemConfig");
        }

        String provider = Optional.ofNullable(map.get("AI_PROVIDER")).filter(s -> !s.isBlank())
                                  .orElse("gemini");
        String model    = Optional.ofNullable(map.get("AI_MODEL")).filter(s -> !s.isBlank())
                                  .orElse("gemini-3-flash-preview");
        String language = resolveLanguageName(
                Optional.ofNullable(map.get("AI_LANGUAGE")).filter(s -> !s.isBlank())
                        .orElse("vi"));

        return new AIConfig(provider, apiKey, model, language);
    }

    /**
     * Maps an ISO language code or full language name to the full English name used
     * in AI prompts (e.g. {@code "vi"} → {@code "Vietnamese"}).
     *
     * <p>New languages can be added here without changing DB schema or frontend.</p>
     *
     * @param code value stored in {@code SystemConfigs.AI_LANGUAGE}
     * @return full language name safe to embed directly in the prompt
     */
    private String resolveLanguageName(String code) {
        if (code == null) return "Vietnamese";
        return switch (code.toLowerCase().strip()) {
            case "vi", "vie", "vietnamese" -> "Vietnamese";
            case "en", "eng", "english"    -> "English";
            default -> code; // admin entered full name directly (e.g. "Japanese")
        };
    }

    private record AIConfig(String provider, String apiKey, String model, String language) {}
}

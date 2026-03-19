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
        AIConfig config;
        try {
            config = loadConfig();
        } catch (Exception e) {
            log.error("AI config load failed: {}", e.getMessage());
            return AIReviewResult.failure("AI chưa được cấu hình: " + e.getMessage());
        }

        LLMAdapter adapter = adapterFactory.getAdapter(config.provider());

        try {
            log.debug("AI review: provider={}, model={}, question={}",
                    config.provider(), config.model(), request.questionTitle());

            // Prompt 1: Deep OOP analysis with exam context
            String analysis = adapter.chat(
                    buildAnalysisPrompt(request), config.apiKey(), config.model());

            // Prompt 2: Return structured JSON result from the analysis
            String resultJson = adapter.chat(
                    buildResultPrompt(analysis, config.language()), config.apiKey(), config.model());

            return parseResult(resultJson);

        } catch (Exception e) {
            log.error("AI review failed [provider={}, question={}]: {}",
                    config.provider(), request.questionTitle(), e.getMessage());
            return AIReviewResult.failure("AI trả về lỗi: " + e.getMessage());
        }
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
                ANALYSIS REQUIRED — Evaluate against these 5 criteria (0–2 points each):
                ═══════════════════════════════════════════════

                A. ENCAPSULATION (0–2):
                   • Are all fields marked private as required by the diagram?
                   • Are getter/setter methods provided for ALL private fields?
                   • Is data hidden and accessed only through methods?

                B. INHERITANCE & RELATIONSHIPS (0–2):
                   • Are "has-a" relationships implemented using the correct data structure — NOT extends?
                   • Are "is-a" relationships implemented using extends/implements — NOT a collection field?
                   • Check ALL data structures and relationships, not just ArrayList
                   • Are extends/implements used exactly as specified in the diagram?

                C. POLYMORPHISM (0–2):
                   • Are abstract classes/methods used correctly per the diagram?
                   • Are interfaces implemented correctly per the diagram?
                   • Is method overriding correct (same signature, @Override annotation)?

                D. DESIGN QUALITY (0–2):
                   • Are methods placed in the correct class (no misplaced logic)?
                   • Does the code follow Single Responsibility per the diagram?
                   • Is the naming consistent and structure clean?

                E. CODE INTEGRITY / ANTI-CHEAT (0–2):
                   • Hard-coded output strings: e.g., return "Student[S001, John, 3.5]"
                   • Hard-coded numeric values: e.g., return 3.5 instead of return this.gpa
                   • Misplaced logic: ALL meaningful logic placed in main() instead of proper classes
                   • Code designed to bypass tests rather than implement correctly

                Perform a thorough analysis. Note ALL violations with specific examples from the code.
                """.formatted(
                request.questionTitle(),
                request.questionDescription() != null ? request.questionDescription() : "(no description)",
                src != null ? src : "(no source code)"
        );
    }

    private String buildResultPrompt(String analysis, String language) {
        return """
                Below is your OOP analysis of a student's Java submission:

                ─── ANALYSIS ───────────────────────────────────────────────────
                %s
                ────────────────────────────────────────────────────────────────

                Based on the analysis above, return a structured JSON evaluation result.

                RULES:
                1. Return ONLY valid JSON — no markdown, no text outside JSON
                2. All comments and violation descriptions must be in: %s
                3. oopScore = sum of all 5 criteria scores (max 10)
                4. isOopViolated = true if oopScore <= 4 OR any criterion E violation found
                5. Criterion scores: 0 = wrong, 1 = partially correct, 2 = fully correct

                Return exactly this JSON:
                {
                  "oopScore": <number 0-10>,
                  "criteriaBreakdown": {
                    "encapsulation": <0|1|2>,
                    "inheritance": <0|1|2>,
                    "polymorphism": <0|1|2>,
                    "designQuality": <0|1|2>,
                    "codeIntegrity": <0|1|2>
                  },
                  "violations": ["<specific violation with code example>"],
                  "hardCodedValues": ["<exact hard-coded literal found>"],
                  "comment": "<comprehensive review in %s — be specific, cite class/method names>",
                  "isOopViolated": <true|false>
                }
                """.formatted(analysis, language, language);
    }

    // ─── RESULT PARSING ──────────────────────────────────────────────────────

    private AIReviewResult parseResult(String rawJson) {
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
            List<String> hardCoded  = objectMapper.convertValue(
                    node.path("hardCodedValues"), new TypeReference<>() {});
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
            log.error("Failed to parse AI result JSON: {}", e.getMessage());
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
                                  .orElse("gemini-2.0-flash");
        String language = Optional.ofNullable(map.get("AI_LANGUAGE")).filter(s -> !s.isBlank())
                                  .orElse("Vietnamese");

        return new AIConfig(provider, apiKey, model, language);
    }

    private record AIConfig(String provider, String apiKey, String model, String language) {}
}

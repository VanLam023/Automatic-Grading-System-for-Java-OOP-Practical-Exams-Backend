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
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * AI rubric review service.
 *
 * <p>Current design intentionally uses ONE prompt only. The old 2-prompt flow
 * created very large payloads for rubric-heavy Java questions and was the main
 * source of truncated / half-JSON responses from Gemini Flash.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LLMReviewService {

    private static final int MAX_SOURCE_CHARS = 20_000;
    private static final int MAX_ANALYSIS_CHARS = 12_000;
    private static final BigDecimal SCORE_EPSILON = new BigDecimal("0.05");

    private final SystemConfigRepository systemConfigRepository;
    private final AesEncryptionUtil encryptionUtil;
    private final ObjectMapper objectMapper;
    private final LLMAdapterFactory adapterFactory;

    // ─── PUBLIC API ──────────────────────────────────────────────────────────

    public AIReviewResult review(AIReviewRequest request) {
        AIConfig config;
        try {
            config = loadConfig();
        } catch (Exception e) {
            log.error("AI config load failed: {}", e.getMessage());
            return AIReviewResult.failure("AI chưa được cấu hình: " + e.getMessage());
        }

        if (request.sourceCode() == null || request.sourceCode().isBlank()) {
            log.warn("AI review skipped for question '{}' — no student source code available.",
                    request.questionTitle());
            return AIReviewResult.failure("AI review skipped: no student Java source files found in submission.");
        }

        try {
            LLMAdapter adapter = adapterFactory.getAdapter(config.provider());
            String prompt = buildRubricPrompt(request, config.language());

            log.debug("AI review: provider={}, model={}, question={}, promptLength={}",
                    config.provider(), config.model(), request.questionTitle(), prompt.length());

            String resultJson = callWithRetry(
                    adapter,
                    prompt,
                    config.apiKey(),
                    config.model(),
                    true,
                    request.questionTitle());

            return parseStrictResult(resultJson, request.maxScore(), config.language());
        } catch (Exception e) {
            log.error("AI review failed [question={}]: {}", request.questionTitle(), e.getMessage());
            return AIReviewResult.failure("AI trả về lỗi: " + e.getMessage());
        }
    }

    // ─── RETRY HELPER ────────────────────────────────────────────────────────

    /**
     * [OLD]
     * private String callWithRetry(...) {
     *     // always retried 3 times, even for MAX_TOKENS / truncated JSON
     * }
     */
    private String callWithRetry(LLMAdapter adapter, String prompt,
                                 String apiKey, String model,
                                 boolean jsonMode, String questionHint) throws Exception {
        int maxAttempts = 3;
        long baseDelayMs = 2_000L;

        Exception lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return jsonMode
                        ? adapter.chatJson(prompt, apiKey, model)
                        : adapter.chat(prompt, apiKey, model);
            } catch (Exception e) {
                lastException = e;

                if (!isRetryableException(e) || attempt >= maxAttempts) {
                    if (attempt >= maxAttempts) {
                        log.error("[AI-RETRY] All {} attempts failed for question '{}': {}",
                                maxAttempts, questionHint, e.getMessage());
                    }
                    throw e;
                }

                long delayMs = baseDelayMs * attempt;
                log.warn("[AI-RETRY] Attempt {}/{} failed for question '{}': {}. Retrying in {}ms...",
                        attempt, maxAttempts, questionHint, e.getMessage(), delayMs);
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("AI retry interrupted", ie);
                }
            }
        }
        throw lastException;
    }

    private boolean isRetryableException(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";

        if (msg.contains("truncated")
                || msg.contains("max_tokens")
                || msg.contains("max tokens")
                || msg.contains("failed to parse ai json")
                || msg.contains("missing required fields")
                || msg.contains("score mismatch")) {
            return false;
        }

        return msg.contains("429")
                || msg.contains("rate limit")
                || msg.contains("timeout")
                || msg.contains("temporarily unavailable")
                || msg.contains("connection reset")
                || msg.contains("503")
                || msg.contains("502")
                || msg.contains("504");
    }

    // ─── PROMPT BUILDERS ─────────────────────────────────────────────────────

    /**
     * [OLD]
     * private String buildAnalysisPrompt(AIReviewRequest request) { ... }
     *
     * private String buildResultPrompt(String analysis, String language, BigDecimal maxScore) { ... }
     *
     * Old flow used 2 prompts:
     * 1) long free-form analysis
     * 2) convert analysis to JSON + long comment per criterion
     * This was kept here as a reference only. New flow uses ONE compact JSON prompt.
     */

    private String buildRubricPrompt(AIReviewRequest request, String language) {
        String sourceCode = limitText(request.sourceCode(), MAX_SOURCE_CHARS, "SOURCE_CODE");
        String structuredAnalysis = limitText(
                Optional.ofNullable(request.structuredAnalysis())
                        .filter(s -> !s.isBlank())
                        .orElse("(Static analysis not available for this submission)"),
                MAX_ANALYSIS_CHARS,
                "STATIC_ANALYSIS");

        String maxScoreStr = safeMaxScore(request.maxScore()).stripTrailingZeros().toPlainString();

        return """
                You are a strict Java programming examiner.
                Grade ONE student submission against the EXACT rubric contained in the exam description.

                Return ONLY valid JSON. No markdown. No explanation outside JSON.

                LANGUAGE:
                - All natural-language reasons and summary must be in %s.
                - Preserve Java identifiers exactly as written in code/spec (class names, method names, fields).

                CORE RULES:
                1. Use ONLY rubric criteria and point values that appear in the exam description.
                2. Do NOT invent new criteria, bonus points, penalties, or hidden checks.
                3. Do NOT normalize scores. Do NOT rescale scores.
                4. Do NOT double-deduct for the same mistake.
                5. If a method/class signature is wrong, dependent logic criteria for that method/class must receive 0.
                6. Anti-cheat / hardcode findings must be reflected only when TRUE hardcode exists.
                7. Keep each reason short (max 160 characters).
                8. Omit optional arrays when empty.
                9. oopScore MUST equal the sum of criteriaResults[*].earnedPoints, rounded to 2 decimals.
                10. oopScore MUST stay in range [0, %s].

                OUTPUT JSON SCHEMA:
                {
                  "oopScore": <number>,
                  "criteriaResults": [
                    {
                      "name": "<criterion name exactly from rubric>",
                      "maxPoints": <number>,
                      "earnedPoints": <number>,
                      "deductedPoints": <number>,
                      "status": "met|partial|violated",
                      "reason": "<short evidence-based reason; omit when full marks>"
                    }
                  ],
                  "violations": ["<optional>", "<optional>"],
                  "hardCodedValues": ["<optional>", "<optional>"],
                  "summary": "<2 short lines max>",
                  "isOopViolated": <true|false>
                }

                EXAM QUESTION
                Title: %s

                Description and Rubric:
                %s

                STATIC ANALYSIS (objective precomputed facts)
                %s

                STUDENT SOURCE CODE
                %s
                """.formatted(
                language,
                maxScoreStr,
                request.questionTitle(),
                Optional.ofNullable(request.questionDescription()).orElse("(no description)"),
                structuredAnalysis,
                sourceCode
        );
    }

    private String limitText(String value, int maxChars, String label) {
        if (value == null) return "";
        if (value.length() <= maxChars) return value;
        return value.substring(0, maxChars) + "\n... [" + label + " TRUNCATED BY SERVER]";
    }

    // ─── RESULT PARSING ──────────────────────────────────────────────────────

    /**
     * [OLD]
     * private AIReviewResult parseResult(String rawJson, BigDecimal maxScore) {
     *     // accepted comment-only payloads and could silently fall back to legacy parser
     * }
     */
    private AIReviewResult parseStrictResult(String rawJson, BigDecimal maxScore, String language) {
        try {
            String json = extractJsonBlock(rawJson);
            JsonNode node = objectMapper.readTree(json);

            JsonNode oopScoreNode = node.get("oopScore");
            JsonNode criteriaNode = node.get("criteriaResults");
            if (oopScoreNode == null || criteriaNode == null || !criteriaNode.isArray() || criteriaNode.isEmpty()) {
                throw new IllegalArgumentException("Missing required fields: oopScore/criteriaResults");
            }

            BigDecimal safeMax = safeMaxScore(maxScore);
            BigDecimal reportedOopScore = clampScore(asBigDecimal(oopScoreNode), safeMax);
            List<Map<String, Object>> normalizedCriteria = normalizeCriteriaResults(criteriaNode, safeMax);

            BigDecimal criteriaSum = normalizedCriteria.stream()
                    .map(this::extractEarnedPoints)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);

            if (reportedOopScore.subtract(criteriaSum).abs().compareTo(SCORE_EPSILON) > 0) {
                throw new IllegalArgumentException(
                        "Score mismatch between oopScore=" + reportedOopScore + " and criteria sum=" + criteriaSum);
            }

            List<String> violations = readStringArray(node.get("violations"));
            List<String> hardCoded = readStringArray(node.get("hardCodedValues"));
            String summary = node.path("summary").asText("").trim();
            boolean oopViolated = node.path("isOopViolated").asBoolean(false);

            String comment = buildCompactComment(normalizedCriteria, summary, language);

            return new AIReviewResult(
                    reportedOopScore,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    violations,
                    hardCoded,
                    normalizedCriteria,
                    comment,
                    oopViolated,
                    false,
                    null
            );
        } catch (Exception e) {
            log.error("Failed to parse strict AI result JSON: {}", e.getMessage());
            return AIReviewResult.failure("Failed to parse AI JSON: " + e.getMessage());
        }
    }

    private List<Map<String, Object>> normalizeCriteriaResults(JsonNode criteriaNode, BigDecimal safeMax) {
        List<Map<String, Object>> results = new ArrayList<>();

        for (JsonNode criterionNode : criteriaNode) {
            String name = criterionNode.path("name").asText("").trim();
            if (name.isBlank()) {
                throw new IllegalArgumentException("Each criterion must contain a non-empty name");
            }

            BigDecimal maxPoints = asBigDecimal(criterionNode.get("maxPoints"));
            if (maxPoints.compareTo(BigDecimal.ZERO) < 0 || maxPoints.compareTo(safeMax) > 0) {
                throw new IllegalArgumentException("Invalid maxPoints for criterion '" + name + "': " + maxPoints);
            }

            BigDecimal earnedPoints = clampScore(asBigDecimal(criterionNode.get("earnedPoints")), maxPoints);
            BigDecimal deductedPoints = criterionNode.hasNonNull("deductedPoints")
                    ? clampScore(asBigDecimal(criterionNode.get("deductedPoints")), maxPoints)
                    : maxPoints.subtract(earnedPoints).max(BigDecimal.ZERO);

            BigDecimal recomputedEarned = maxPoints.subtract(deductedPoints).setScale(2, RoundingMode.HALF_UP);
            if (recomputedEarned.compareTo(earnedPoints) != 0) {
                deductedPoints = maxPoints.subtract(earnedPoints).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            }

            String status = criterionNode.path("status").asText("").trim();
            if (status.isBlank()) {
                status = deriveStatus(earnedPoints, maxPoints);
            }

            String reason = criterionNode.path("reason").asText("").trim();
            if (earnedPoints.compareTo(maxPoints) == 0) {
                reason = "";
            }

            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("name", name);
            normalized.put("maxPoints", maxPoints.setScale(2, RoundingMode.HALF_UP));
            normalized.put("earnedPoints", earnedPoints.setScale(2, RoundingMode.HALF_UP));
            normalized.put("deductedPoints", deductedPoints.setScale(2, RoundingMode.HALF_UP));
            normalized.put("status", status);
            if (!reason.isBlank()) {
                normalized.put("reason", truncateReason(reason));
            }
            results.add(normalized);
        }

        return results;
    }

    private String buildCompactComment(List<Map<String, Object>> criteriaResults, String summary, String language) {
        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (Map<String, Object> item : criteriaResults) {
            BigDecimal maxPoints = extractBigDecimal(item.get("maxPoints"));
            BigDecimal earnedPoints = extractBigDecimal(item.get("earnedPoints"));
            BigDecimal deductedPoints = extractBigDecimal(item.get("deductedPoints"));
            String name = Objects.toString(item.get("name"), "Tiêu chí");
            String reason = Objects.toString(item.get("reason"), "").trim();

            sb.append(index++)
                    .append(". ")
                    .append(name)
                    .append(" (tối đa ")
                    .append(formatScore(maxPoints))
                    .append(" điểm): đạt ")
                    .append(formatScore(earnedPoints))
                    .append(" điểm, bị trừ ")
                    .append(formatScore(deductedPoints))
                    .append(" điểm");
            if (!reason.isBlank()) {
                sb.append(" - ").append(reason);
            }
            sb.append("\n");
        }

        if (!summary.isBlank()) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(language.equalsIgnoreCase("Vietnamese") ? "Tóm tắt: " : "Summary: ")
                    .append(summary);
        }

        return sb.toString().trim();
    }

    private String truncateReason(String reason) {
        if (reason == null) return "";
        String normalized = reason.replace("\r", " ").replace("\n", " ").trim();
        return normalized.length() <= 160 ? normalized : normalized.substring(0, 157) + "...";
    }

    private BigDecimal clampScore(BigDecimal value, BigDecimal maxAllowed) {
        BigDecimal safeValue = value != null ? value : BigDecimal.ZERO;
        BigDecimal safeMax = maxAllowed != null ? maxAllowed : BigDecimal.ZERO;
        return safeValue.max(BigDecimal.ZERO).min(safeMax).setScale(2, RoundingMode.HALF_UP);
    }

    private String deriveStatus(BigDecimal earnedPoints, BigDecimal maxPoints) {
        if (earnedPoints.compareTo(BigDecimal.ZERO) == 0) {
            return "violated";
        }
        if (earnedPoints.compareTo(maxPoints) == 0) {
            return "met";
        }
        return "partial";
    }

    private List<String> readStringArray(JsonNode node) {
        if (node == null || node.isNull() || !node.isArray()) {
            return Collections.emptyList();
        }
        List<String> values = objectMapper.convertValue(node, new TypeReference<>() {});
        return values != null ? values : Collections.emptyList();
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
        int end = s.lastIndexOf('}');
        return (start != -1 && end > start) ? s.substring(start, end + 1) : s;
    }

    private BigDecimal asBigDecimal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(node.asText());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal extractBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal extractEarnedPoints(Map<String, Object> item) {
        return extractBigDecimal(item.get("earnedPoints"));
    }

    private String formatScore(BigDecimal value) {
        BigDecimal safe = value != null ? value : BigDecimal.ZERO;
        return safe.stripTrailingZeros().toPlainString();
    }

    private BigDecimal safeMaxScore(BigDecimal maxScore) {
        return (maxScore != null && maxScore.compareTo(BigDecimal.ZERO) > 0)
                ? maxScore
                : BigDecimal.TEN;
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
        String model = Optional.ofNullable(map.get("AI_MODEL")).filter(s -> !s.isBlank())
                .orElse("gemini-3-flash-preview");
        String language = resolveLanguageName(
                Optional.ofNullable(map.get("AI_LANGUAGE")).filter(s -> !s.isBlank())
                        .orElse("vi"));

        return new AIConfig(provider, apiKey, model, language);
    }

    private String resolveLanguageName(String code) {
        if (code == null) return "Vietnamese";
        return switch (code.toLowerCase().strip()) {
            case "vi", "vie", "vietnamese" -> "Vietnamese";
            case "en", "eng", "english" -> "English";
            default -> code;
        };
    }

    private record AIConfig(String provider, String apiKey, String model, String language) {
    }
}

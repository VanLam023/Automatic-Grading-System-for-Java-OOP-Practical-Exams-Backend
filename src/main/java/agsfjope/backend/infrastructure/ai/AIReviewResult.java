package agsfjope.backend.infrastructure.ai;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Parsed result from the Gemini AI OOP evaluation.
 *
 * @param oopScore           total OOP score (0–10); null if AI evaluation failed
 * @param encapsulation      score for encapsulation criterion (0–2)
 * @param inheritance        score for inheritance/relationship criterion (0–2)
 * @param polymorphism       score for polymorphism criterion (0–2)
 * @param designQuality      score for design quality criterion (0–2)
 * @param codeIntegrity      score for code integrity / anti-cheat criterion (0–2)
 * @param violations         list of specific OOP violations found
 * @param hardCodedValues    list of hard-coded return values detected
 * @param criteriaResults    dynamic per-criterion evaluation list returned by AI prompt
 * @param comment            full review comment in the configured language
 * @param oopViolated        true if code fundamentally violates OOP principles
 * @param aiError            true if AI call failed (all scores null, fallback applied)
 * @param errorMessage       error detail if aiError is true
 */
public record AIReviewResult(
        BigDecimal oopScore,
        BigDecimal encapsulation,
        BigDecimal inheritance,
        BigDecimal polymorphism,
        BigDecimal designQuality,
        BigDecimal codeIntegrity,
        List<String> violations,
        List<String> hardCodedValues,
        List<Map<String, Object>> criteriaResults,
        String comment,
        boolean oopViolated,
        boolean aiError,
        String errorMessage
) {
    /** Convenience factory — AI evaluation failed (graceful fallback, no score). */
    public static AIReviewResult failure(String reason) {
        return new AIReviewResult(
                null, null, null, null, null, null,
                List.of(), List.of(),
                List.of(),
                "AI evaluation failed: " + reason,
                false, true, reason
        );
    }
}

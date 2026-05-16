package agsfjope.backend.infrastructure.grading.criteria;

import agsfjope.backend.core.entities.Answer;
import agsfjope.backend.core.entities.CriteriaResult;
import agsfjope.backend.core.entities.GradingCriteria;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Abstract base for all {@link CriterionHandler} implementations.
 *
 * <p>Provides shared helpers for building {@link CriteriaResult} instances
 * and reading typed values from the {@code checkParams} JSONB map.</p>
 */
public abstract class AbstractCriterionHandler implements CriterionHandler {

    // ─── Result builders ─────────────────────────────────────────────────────

    /** Build a passing result with full score. */
    protected CriteriaResult pass(Answer answer, GradingCriteria criteria, String feedback) {
        return CriteriaResult.builder()
                .answer(answer)
                .criteria(criteria)
                .passed(true)
                .earnedScore(criteria.getMaxScore())
                .feedback(feedback)
                .build();
    }

    /** Build a failing result with zero score. */
    protected CriteriaResult fail(Answer answer, GradingCriteria criteria, String feedback) {
        return CriteriaResult.builder()
                .answer(answer)
                .criteria(criteria)
                .passed(false)
                .earnedScore(BigDecimal.ZERO)
                .feedback(feedback)
                .build();
    }

    /** Build a partial result (score < maxScore, passed = false). */
    protected CriteriaResult partial(Answer answer, GradingCriteria criteria,
                                     BigDecimal earned, String feedback) {
        return CriteriaResult.builder()
                .answer(answer)
                .criteria(criteria)
                .passed(false)
                .earnedScore(earned)
                .feedback(feedback)
                .build();
    }

    // ─── Param accessors ─────────────────────────────────────────────────────

    protected String param(Map<String, Object> params, String key) {
        Object val = params.get(key);
        return val != null ? val.toString() : null;
    }

    protected String paramRequired(Map<String, Object> params, String key, String criteriaCode) {
        String val = param(params, key);
        if (val == null || val.isBlank()) {
            throw new IllegalArgumentException(
                "Criterion " + criteriaCode + " is missing required param '" + key + "'");
        }
        return val;
    }

    @SuppressWarnings("unchecked")
    protected java.util.List<String> paramList(Map<String, Object> params, String key) {
        Object val = params.get(key);
        if (val instanceof java.util.List<?> list) {
            return (java.util.List<String>) list;
        }
        return java.util.List.of();
    }

    protected boolean paramBool(Map<String, Object> params, String key, boolean defaultValue) {
        Object val = params.get(key);
        if (val instanceof Boolean b) return b;
        if (val instanceof String s) return Boolean.parseBoolean(s);
        return defaultValue;
    }
}

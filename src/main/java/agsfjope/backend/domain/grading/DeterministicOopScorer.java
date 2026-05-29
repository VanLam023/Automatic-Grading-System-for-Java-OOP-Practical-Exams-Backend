package agsfjope.backend.domain.grading;

import agsfjope.backend.core.entities.Answer;
import agsfjope.backend.core.entities.CriteriaResult;
import agsfjope.backend.core.entities.GradingCriteria;
import agsfjope.backend.core.enums.CriterionType;
import agsfjope.backend.infrastructure.grading.criteria.CriterionHandler;
import agsfjope.backend.infrastructure.grading.criteria.SourceCodeContext;
import agsfjope.backend.infrastructure.grading.criteria.handlers.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Evaluates all structural OOP grading criteria for a single answer using
 * JavaParser (AST) and/or Java Reflection (when .jar is available).
 *
 * <p>Produces a {@link List<CriteriaResult>} that the pipeline persists to
 * the {@code criteria_results} table. OOP score = SUM(earnedScore).</p>
 *
 * <p>Stateless — one instance handles all answers concurrently.</p>
 */
@Slf4j
@Component
public class DeterministicOopScorer {

    /** Handler registry: one handler per CriterionType. */
    private final Map<CriterionType, CriterionHandler> handlers;

    public DeterministicOopScorer(
            ClassExistsHandler classExists,
            InterfaceExistsHandler interfaceExists,
            FieldCheckHandler fieldCheck,
            ConstructorCheckHandler constructorCheck,
            MethodSignatureHandler methodSignature,
            GetterSetterHandler getterSetter,
            ExtendsCheckHandler extendsCheck,
            ImplementsCheckHandler implementsCheck,
            NamingConventionHandler namingConvention) {

        this.handlers = Map.of(
            CriterionType.CLASS_EXISTS,       classExists,
            CriterionType.INTERFACE_EXISTS,   interfaceExists,
            CriterionType.FIELD_CHECK,        fieldCheck,
            CriterionType.CONSTRUCTOR_CHECK,  constructorCheck,
            CriterionType.METHOD_SIGNATURE,   methodSignature,
            CriterionType.GETTER_SETTER,      getterSetter,
            CriterionType.EXTENDS_CHECK,      extendsCheck,
            CriterionType.IMPLEMENTS_CHECK,   implementsCheck,
            CriterionType.NAMING_CONVENTION,  namingConvention
        );
    }

    /**
     * Evaluates all STRUCTURAL criteria for a single answer.
     *
     * @param answer   the answer entity (used for FK in CriteriaResult)
     * @param context  pre-built AST + loaded classes for this answer's source
     * @param criteria ordered list of criteria for this question
     * @return list of CriteriaResult (not yet persisted)
     */
    public List<CriteriaResult> evaluate(Answer answer,
                                          SourceCodeContext context,
                                          List<GradingCriteria> criteria) {
        List<CriteriaResult> results = new ArrayList<>();

        for (GradingCriteria c : criteria) {
            // Skip BEHAVIORAL criteria (reserved for future TestCase linking)
            if ("BEHAVIORAL".equalsIgnoreCase(c.getCriteriaGroup())) {
                log.debug("[OOP] Skipping BEHAVIORAL criterion '{}' ({})", c.getCriteriaCode(), c.getCriterionType());
                continue;
            }

            CriterionHandler handler = handlers.get(c.getCriterionType());
            if (handler == null) {
                log.warn("[OOP] No handler registered for CriterionType={}. Skipping '{}'.",
                        c.getCriterionType(), c.getCriteriaCode());
                continue;
            }

            try {
                CriteriaResult result = handler.check(context, c, answer);
                results.add(result);
                log.debug("[OOP] {} [{}] — passed={}, score={}",
                        c.getCriteriaCode(), c.getCriterionType(),
                        result.isPassed(), result.getEarnedScore());
            } catch (Exception e) {
                // Never crash the pipeline — record a failing result with error detail
                log.error("[OOP] Handler error for criterion '{}': {}", c.getCriteriaCode(), e.getMessage(), e);
                results.add(CriteriaResult.builder()
                        .answer(answer)
                        .criteria(c)
                        .passed(false)
                        .earnedScore(BigDecimal.ZERO)
                        .feedback("⚠️ Grading engine error: " + e.getMessage())
                        .build());
            }
        }

        return results;
    }

    /**
     * Computes the total OOP raw score from a list of evaluated criteria results.
     * OOP raw score = SUM(earnedScore).
     *
     * @param results the list returned by {@link #evaluate}
     * @return total OOP score
     */
    public BigDecimal sumOopScore(List<CriteriaResult> results) {
        return results.stream()
                .map(CriteriaResult::getEarnedScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Determines if the submission fundamentally violates OOP principles.
     *
     * <p>OOP "violated" = any STRUCTURAL criterion that covers encapsulation,
     * inheritance, or polymorphism FAILED (earned 0 on a non-zero maxScore criterion).</p>
     *
     * <p>Only relevant when {@code GradingModeConfig.failIfOopViolated = true}.</p>
     *
     * @param results the list returned by {@link #evaluate}
     * @return true if at least one OOP-fundamental criterion failed
     */
    public boolean isOopViolated(List<CriteriaResult> results) {
        return results.stream().anyMatch(r ->
                !r.isPassed()
                && r.getCriteria().getMaxScore().compareTo(BigDecimal.ZERO) > 0
                && isOopFundamental(r.getCriteria().getCriterionType()));
    }

    /** Returns true if the criterion type covers encapsulation, inheritance, or polymorphism. */
    private boolean isOopFundamental(CriterionType type) {
        return switch (type) {
            case FIELD_CHECK,          // encapsulation: private field
            GETTER_SETTER,             // encapsulation: access methods
            EXTENDS_CHECK,             // inheritance
            IMPLEMENTS_CHECK,          // polymorphism
            CONSTRUCTOR_CHECK,         // constructor signature impacts OOP design
            CLASS_EXISTS,              // missing class indicates structural violation
            INTERFACE_EXISTS           // missing interface indicates structural violation
                -> true;
            default -> false;
        };
    }
}

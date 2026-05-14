package agsfjope.backend.infrastructure.grading.criteria;

import agsfjope.backend.core.entities.CriteriaResult;
import agsfjope.backend.core.entities.GradingCriteria;

/**
 * Strategy interface for evaluating a single OOP structural criterion.
 *
 * <p>Each implementation handles one {@link agsfjope.backend.core.enums.CriterionType}.
 * Handlers are registered in {@code DeterministicOopScorer} via a Map.</p>
 *
 * <p>Implementations must be stateless — one handler instance handles all answers.</p>
 */
public interface CriterionHandler {

    /**
     * Evaluates the given criterion against the student's source/bytecode.
     *
     * @param context  pre-parsed AST and loaded classes for this answer
     * @param criteria the criterion definition (type, params, maxScore)
     * @param answer   the answer entity (for FK reference in the result)
     * @return a {@link CriteriaResult} (not yet persisted; caller saves it)
     */
    CriteriaResult check(SourceCodeContext context,
                         GradingCriteria criteria,
                         agsfjope.backend.core.entities.Answer answer);
}

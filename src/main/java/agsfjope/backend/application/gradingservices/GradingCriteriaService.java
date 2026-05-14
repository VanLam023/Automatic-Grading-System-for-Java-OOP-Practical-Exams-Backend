package agsfjope.backend.application.gradingservices;

import agsfjope.backend.application.dtos.requests.grading.GradingCriteriaRequest;
import agsfjope.backend.application.dtos.responses.grading.GradingCriteriaResponse;
import agsfjope.backend.core.entities.GradingCriteria;
import agsfjope.backend.core.entities.Question;
import agsfjope.backend.core.repositories.grading.GradingCriteriaRepository;
import agsfjope.backend.core.repositories.exampaper.QuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Application service for managing structural OOP grading criteria.
 *
 * <p>Provides CRUD + batch-replace operations on {@link GradingCriteria}
 * nested under a {@link Question}. Business rules enforced:</p>
 * <ul>
 *   <li>criteriaCode must be unique per question (DB constraint).</li>
 *   <li>Batch replace: existing criteria are deleted and re-created atomically.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GradingCriteriaService {

    private final GradingCriteriaRepository criteriaRepo;
    private final QuestionRepository questionRepo;

    // ─── List ────────────────────────────────────────────────────────────────

    /**
     * Returns all criteria for a question ordered by displayOrder ASC.
     */
    @Transactional(readOnly = true)
    public List<GradingCriteriaResponse> listByQuestion(UUID questionId) {
        return criteriaRepo.findByQuestion_QuestionIdOrderByDisplayOrderAsc(questionId)
                .stream()
                .map(GradingCriteriaResponse::from)
                .toList();
    }

    // ─── Create ──────────────────────────────────────────────────────────────

    /**
     * Creates a single criterion for the given question.
     */
    @Transactional
    public GradingCriteriaResponse create(UUID questionId, GradingCriteriaRequest req) {
        Question question = findQuestionOrThrow(questionId);
        GradingCriteria saved = criteriaRepo.save(toEntity(question, req));
        log.info("[Criteria] Created '{}' for question {}", saved.getCriteriaCode(), questionId);
        return GradingCriteriaResponse.from(saved);
    }

    // ─── Update ──────────────────────────────────────────────────────────────

    /**
     * Updates an existing criterion. Throws if criteriaId not found.
     */
    @Transactional
    public GradingCriteriaResponse update(UUID questionId, UUID criteriaId, GradingCriteriaRequest req) {
        GradingCriteria existing = criteriaRepo.findById(criteriaId)
                .orElseThrow(() -> new IllegalArgumentException("Tiêu chí không tồn tại: " + criteriaId));

        // Ensure it belongs to the right question
        if (!existing.getQuestion().getQuestionId().equals(questionId)) {
            throw new IllegalArgumentException("Tiêu chí không thuộc câu hỏi này.");
        }

        existing.setCriteriaCode(req.getCriteriaCode());
        existing.setCriteriaGroup(req.getCriteriaGroup() != null ? req.getCriteriaGroup() : "STRUCTURAL");
        existing.setCriterionType(req.getCriterionType());
        existing.setDescription(req.getDescription());
        existing.setMaxScore(req.getMaxScore());
        existing.setCheckParamsJson(req.getCheckParamsJson() != null ? req.getCheckParamsJson() : "{}");
        existing.setDisplayOrder(req.getDisplayOrder() != null ? req.getDisplayOrder() : 0);

        GradingCriteria saved = criteriaRepo.save(existing);
        log.info("[Criteria] Updated '{}' for question {}", saved.getCriteriaCode(), questionId);
        return GradingCriteriaResponse.from(saved);
    }

    // ─── Delete ──────────────────────────────────────────────────────────────

    /**
     * Deletes a single criterion by ID.
     */
    @Transactional
    public void delete(UUID questionId, UUID criteriaId) {
        GradingCriteria existing = criteriaRepo.findById(criteriaId)
                .orElseThrow(() -> new IllegalArgumentException("Tiêu chí không tồn tại: " + criteriaId));
        if (!existing.getQuestion().getQuestionId().equals(questionId)) {
            throw new IllegalArgumentException("Tiêu chí không thuộc câu hỏi này.");
        }
        criteriaRepo.delete(existing);
        log.info("[Criteria] Deleted {} from question {}", criteriaId, questionId);
    }

    // ─── Batch replace ───────────────────────────────────────────────────────

    /**
     * Atomically replaces ALL criteria for a question with the provided list.
     *
     * <p>Strategy: delete all existing → insert new list in displayOrder sequence.
     * This avoids unique-constraint conflicts on criteriaCode rename.</p>
     *
     * @param questionId target question
     * @param requests   ordered list of criteria (empty list = remove all)
     * @return saved criteria as response DTOs
     */
    @Transactional
    public List<GradingCriteriaResponse> saveBatch(UUID questionId, List<GradingCriteriaRequest> requests) {
        Question question = findQuestionOrThrow(questionId);

        // Delete existing criteria for this question
        List<GradingCriteria> existing = criteriaRepo.findByQuestion_QuestionIdOrderByDisplayOrderAsc(questionId);
        criteriaRepo.deleteAll(existing);
        criteriaRepo.flush();

        // Re-insert with fresh displayOrder
        List<GradingCriteria> toSave = new java.util.ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            GradingCriteriaRequest req = requests.get(i);
            GradingCriteria entity = toEntity(question, req);
            entity.setDisplayOrder(i); // enforce sequential order
            toSave.add(entity);
        }

        List<GradingCriteria> saved = criteriaRepo.saveAll(toSave);
        log.info("[Criteria] Batch saved {} criteria for question {}", saved.size(), questionId);
        return saved.stream().map(GradingCriteriaResponse::from).toList();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Question findQuestionOrThrow(UUID questionId) {
        return questionRepo.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Câu hỏi không tồn tại: " + questionId));
    }

    private GradingCriteria toEntity(Question question, GradingCriteriaRequest req) {
        return GradingCriteria.builder()
                .question(question)
                .criteriaCode(req.getCriteriaCode())
                .criteriaGroup(req.getCriteriaGroup() != null ? req.getCriteriaGroup() : "STRUCTURAL")
                .criterionType(req.getCriterionType())
                .description(req.getDescription())
                .maxScore(req.getMaxScore())
                .checkParamsJson(req.getCheckParamsJson() != null ? req.getCheckParamsJson() : "{}")
                .displayOrder(req.getDisplayOrder() != null ? req.getDisplayOrder() : 0)
                .build();
    }
}

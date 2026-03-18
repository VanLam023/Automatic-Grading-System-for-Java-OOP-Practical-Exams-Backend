package agsfjope.backend.core.repositories.exampaper;

import agsfjope.backend.core.entities.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link TestCase} entity.
 *
 * <p>TestCases are cascade-deleted by DB when their parent Question is removed.
 * This repository provides bulk-save and a convenience delete for the overwrite flow.</p>
 */
@Repository
public interface TestCaseRepository extends JpaRepository<TestCase, UUID> {

    /**
     * Returns all test cases for the given question, ordered by test case number ascending.
     *
     * @param questionId the question's UUID
     * @return ordered list of test cases
     */
    List<TestCase> findByQuestion_QuestionIdOrderByTestCaseNumberAsc(UUID questionId);

    /**
     * Bulk-deletes all test cases that belong to any question of the given exam paper.
     * Used during the overwrite flow (BR-09) before deleting questions,
     * to avoid FK constraint violations if DB cascade is not fully applied at JPA level.
     *
     * @param examPaperId the exam paper's UUID
     */
    @Modifying
    @Query("DELETE FROM TestCase tc WHERE tc.question.examPaper.examPaperId = :examPaperId")
    void deleteByQuestion_ExamPaper_ExamPaperId(@Param("examPaperId") UUID examPaperId);
}

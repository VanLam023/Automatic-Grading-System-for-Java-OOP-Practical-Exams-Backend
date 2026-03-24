package agsfjope.backend.application.examservices;

import agsfjope.backend.application.dtos.requests.exam.CreateExamRequest;
import agsfjope.backend.application.dtos.requests.exam.UpdateExamRequest;
import agsfjope.backend.application.dtos.responses.exam.ExamResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for Exam management use cases.
 * Encapsulates business logic for creating, retrieving, updating, and deleting exams.
 * Only {@code EXAM_STAFF} role is authorized to invoke these operations.
 */
public interface ExamService {

    /**
     * Creates a new exam after validating all business rules.
     * Validates start time is in the future, duration &lt;= 5 hours, and no duplicate name+semester.
     *
     * @param request exam creation request payload
     * @return created exam information
     */
    ExamResponse createExam(CreateExamRequest request);

    /**
     * Returns all active (non-deleted) exams in the system.
     *
     * @return list of exam responses
     */
    List<ExamResponse> getAllExams();

    /**
     * Returns a paginated list of active (non-deleted) exams.
     * Supports sorting via {@link Pageable} (default: createdAt desc).
     *
     * @param pageable paging and sorting parameters
     * @return paginated exam responses
     */
    Page<ExamResponse> getAllExams(Pageable pageable);

    /**
     * Searches exams by name/semester (contains, ignore case) and filters by academic year.
     * Null/empty params are treated as "no filter".
     *
     * @param name         partial name to search
     * @param semester     partial semester code to search
     * @param academicYear exact academic year filter (e.g., "2025-2026")
     * @param pageable     paging and sorting parameters
     * @return paginated exam responses
     */
    Page<ExamResponse> searchExams(String name, String semester, String academicYear, Pageable pageable);

    /**
     * Returns full details of a single active exam by its ID.
     *
     * @param examId exam identifier
     * @return exam information
     * @throws agsfjope.backend.core.exceptions.auth.NotFoundException if exam not found or deleted
     */
    ExamResponse getExamById(UUID examId);

    /**
     * Updates mutable fields of an existing exam.
     * Only fields provided in the request will be updated.
     *
     * @param examId  exam identifier
     * @param request update payload
     * @return updated exam information
     */
    ExamResponse updateExam(UUID examId, UpdateExamRequest request);

    /**
     * Soft-deletes an exam by setting its {@code deletedAt} timestamp.
     * Throws {@link agsfjope.backend.core.exceptions.exam.ExamConflictException}
     * if the exam has existing submissions (BR-12).
     *
     * @param examId exam identifier
     */
    void deleteExam(UUID examId);
}

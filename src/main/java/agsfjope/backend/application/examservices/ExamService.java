package agsfjope.backend.application.examservices;

import agsfjope.backend.application.dtos.requests.auth.CreateExamRequest;
import agsfjope.backend.application.dtos.responses.auth.ExamResponse;

import java.util.List;
import java.util.UUID;

public interface ExamService {

    /**
     * Creates a new exam.
     * Validates exam information before saving.
     *
     * @param request exam creation request
     * @return created exam information
     */
    ExamResponse createExam(CreateExamRequest request);

    /**
     * Returns all exams in the system.
     *
     * @return list of exams
     */
    List<ExamResponse> getAllExams();

    /**
     * Returns exam details by id.
     *
     * @param examId exam identifier
     * @return exam information
     */
    ExamResponse getExamById(UUID examId);

    /**
     * Deletes an exam by id.
     *
     * @param examId exam identifier
     */
    void deleteExam(UUID examId);
}

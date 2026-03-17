package agsfjope.backend.application.examservices.impl;

import agsfjope.backend.application.dtos.requests.auth.CreateExamRequest;
import agsfjope.backend.application.dtos.responses.auth.ExamResponse;
import agsfjope.backend.application.examservices.ExamService;
import agsfjope.backend.core.entities.Exam;
import agsfjope.backend.core.entities.User;
import agsfjope.backend.core.exceptions.auth.NotFoundException;
import agsfjope.backend.core.repositories.ExamRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import agsfjope.backend.infrastructure.security.SecurityUtils;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
    public class ExamServiceImpl implements ExamService {

        private final ExamRepository examRepository;

        private static final int MAX_EXAM_DURATION_HOURS = 5;

        /**
         * Create exam flow:
         * 1. Validate startTime must be in the future.
         * 2. Validate exam duration <= 5 hours.
         * 3. Check duplicate exam (name + semester).
         * 4. Save exam to database.
         * 5. Return exam response.
         *
         * @param request exam creation request
         * @return created exam
         */
        @Override
        @Transactional
        public ExamResponse createExam(CreateExamRequest request) {

            User currentUser = SecurityUtils.getCurrentUser();
            // === STEP 1: Validate start time ===
            if (request.getStartTime().isBefore(OffsetDateTime.now())) {
                throw new IllegalArgumentException("Exam start time must be in the future");
            }

            // === STEP 2: Validate exam duration ===
            long durationHours = Duration.between(
                    request.getStartTime(),
                    request.getEndTime()
            ).toHours();

            if (durationHours > MAX_EXAM_DURATION_HOURS) {
                throw new IllegalArgumentException("Exam duration cannot exceed 5 hours");
            }

            // === STEP 3: Check duplicate exam ===
            boolean exists = examRepository.existsByNameAndSemester(
                    request.getName(),
                    request.getSemester()
            );

            if (exists) {
                throw new IllegalArgumentException("Exam already exists for this semester");
            }

            // === STEP 4: Create exam entity ===
            Exam exam = Exam.builder()

                    .name(request.getName())
                    .semester(request.getSemester())
                    .academicYear(request.getAcademicYear())
                    .description(request.getDescription())
                    .startTime(request.getStartTime())
                    .endTime(request.getEndTime())
                    .gradingMode(request.getGradingMode())
                    .createdBy(currentUser)
                    .build();

            examRepository.save(exam);

            // === STEP 5: Map to response DTO ===
            return mapToResponse(exam);
        }

        /**
         * Returns all exams.
         *
         * @return list of exam responses
         */
        @Override
        public List<ExamResponse> getAllExams() {

            return examRepository.findAll()
                    .stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());
        }

        /**
         * Get exam by id.
         *
         * @param examId exam identifier
         * @return exam response
         */
        @Override
        public ExamResponse getExamById(UUID examId) {

            Exam exam = examRepository.findById(examId)
                    .orElseThrow(() -> new NotFoundException("Exam not found"));

            return mapToResponse(exam);
        }

        /**
         * Delete exam by id.
         *
         * @param examId exam identifier
         */
        @Override
        @Transactional
        public void deleteExam(UUID examId) {

            Exam exam = examRepository.findById(examId)
                    .orElseThrow(() -> new NotFoundException("Exam not found"));

            examRepository.delete(exam);
        }

        /**
         * Maps Exam entity to ExamResponse DTO.
         */
        private ExamResponse mapToResponse(Exam exam) {
            return ExamResponse.builder()
                    .examId(exam.getExamId())
                    .name(exam.getName())
                    .semester(exam.getSemester())
                    .academicYear(exam.getAcademicYear())
                    .description(exam.getDescription())
                    .startTime(exam.getStartTime())
                    .endTime(exam.getEndTime())
                    .gradingMode(exam.getGradingMode())
                    .build();
        }
}

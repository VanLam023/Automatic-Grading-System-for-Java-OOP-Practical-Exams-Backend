package agsfjope.backend.presentation.controllers;

import agsfjope.backend.application.dtos.requests.auth.CreateExamRequest;
import agsfjope.backend.application.dtos.responses.auth.ExamResponse;
import agsfjope.backend.application.examservices.ExamService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;



    /**
     * REST Controller exposing Exam management APIs.
     *
     * Handles exam-related operations such as:
     * - Creating exams
     * - Retrieving exam list
     * - Getting exam details
     * - Deleting exams
     *
     * Business logic is delegated to {@link ExamService}.
     */
    @RestController
    @RequestMapping("/api/exams")
    @RequiredArgsConstructor
    @Tag(name = "Exams", description = "APIs for managing exams")
    public class ExamController {

        private final ExamService examService;

        /**
         * Tạo kỳ thi mới.
         *
         * Flow:
         * 1. Validate request body.
         * 2. Call ExamService to create exam.
         * 3. Return created exam data.
         *
         * @param request DTO chứa thông tin kỳ thi
         * @return thông tin exam đã tạo
         */
        @PostMapping
        public ResponseEntity<Map<String, Object>> createExam(@Valid @RequestBody CreateExamRequest request) {
            ExamResponse exam = examService.createExam(request);
            return ResponseEntity.ok(buildSuccessResponse("Tạo kỳ thi thành công", exam));
        }

        /**
         * Lấy danh sách tất cả kỳ thi.
         *
         * Flow:
         * 1. Call service to fetch exams.
         * 2. Return list of exams.
         *
         * @return danh sách exam
         */
        @GetMapping
        public ResponseEntity<Map<String, Object>> getAllExams() {
            List<ExamResponse> exams = examService.getAllExams();
            return ResponseEntity.ok(buildSuccessResponse("Lấy danh sách kỳ thi thành công", exams));
        }

        /**
         * Lấy chi tiết kỳ thi theo ID.
         *
         * Flow:
         * 1. Receive examId from path.
         * 2. Fetch exam from service.
         * 3. Return exam details.
         *
         * @param examId ID của kỳ thi
         * @return exam detail
         */
        @GetMapping("/{examId}")
        public ResponseEntity<Map<String, Object>> getExamById(@PathVariable UUID examId) {
            ExamResponse exam = examService.getExamById(examId);
            return ResponseEntity.ok(buildSuccessResponse("Lấy thông tin kỳ thi thành công", exam));
        }

        /**
         * Xóa kỳ thi.
         *
         * Flow:
         * 1. Receive examId.
         * 2. Call service to delete exam.
         * 3. Return success message.
         *
         * @param examId ID của kỳ thi
         * @return thông báo xóa thành công
         */
        @DeleteMapping("/{examId}")
        public ResponseEntity<Map<String, Object>> deleteExam(@PathVariable UUID examId) {
            examService.deleteExam(examId);
            return ResponseEntity.ok(buildSuccessResponse("Xóa kỳ thi thành công", null));
        }

        // ─────────────────────────────────────────────────────────
        // Helpers
        // ─────────────────────────────────────────────────────────

        /**
         * Helper method: builds the standard success response format.
         * Format: { success, message, data, errors }
         */
        private Map<String, Object> buildSuccessResponse(String message, Object data) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", message);
            response.put("data", data);
            response.put("errors", null);
            return response;
        }
}
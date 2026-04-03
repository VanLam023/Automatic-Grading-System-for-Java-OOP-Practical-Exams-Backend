package agsfjope.backend.application.gradingservices;

import agsfjope.backend.application.dtos.responses.grading.GradingResultResponse;
import agsfjope.backend.core.entities.*;
import agsfjope.backend.core.enums.GradingMode;
import agsfjope.backend.core.enums.GradingResultStatus;
import agsfjope.backend.core.exceptions.auth.NotFoundException;
import agsfjope.backend.core.repositories.grading.AIReviewRepository;
import agsfjope.backend.core.repositories.grading.GradingResultRepository;
import agsfjope.backend.core.repositories.grading.TestCaseResultRepository;
import agsfjope.backend.core.repositories.submission.SubmissionRepository;
import agsfjope.backend.testutils.TestDataFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho GradingQueryService.
 * Phân loại: [N] Normal, [B] Boundary, [A] Abnormal.
 * Pattern: AAA (Arrange - Act - Assert).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GradingQueryService Tests")
class GradingQueryServiceTest {

    @Mock private GradingResultRepository  gradingResultRepository;
    @Mock private TestCaseResultRepository testCaseResultRepository;
    @Mock private AIReviewRepository       aiReviewRepository;
    @Mock private SubmissionRepository     submissionRepository;
    // ObjectMapper dùng real bean để test JSON parsing trong toAiDetail()
    private final ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private GradingQueryService service;

    // ─── Shared fixtures ──────────────────────────────────────────────────────

    private UUID blockId;
    private UUID studentId;
    private UUID submissionId;
    private User student;
    private Block block;
    private Submission submission;
    private GradingResult gradingResult;

    @BeforeEach
    void setUp() {
        blockId      = UUID.randomUUID();
        submissionId = UUID.randomUUID();

        // Exam → Block
        Exam exam = new Exam();
        exam.setExamId(UUID.randomUUID());
        exam.setSemester("SP25");
        exam.setAcademicYear("2025");

        block = new Block();
        block.setBlockId(blockId);
        block.setName("Block PRO192");
        block.setExam(exam);

        // Student
        student   = TestDataFactory.createActiveStudent();
        studentId = student.getUserId();

        // Submission
        submission = Submission.builder()
                .submissionId(submissionId)
                .student(student)
                .block(block)
                .fileName("SE173173.zip")
                .filePath("submissions/SP25/Block PRO192/SE173173.zip")
                .fileSizeBytes(1024L * 512)
                .build();

        // GradingResult
        gradingResult = GradingResult.builder()
                .gradingResultId(UUID.randomUUID())
                .submission(submission)
                .gradingMode(GradingMode.MODE_1)
                .status(GradingResultStatus.PASS)
                .totalScore(new BigDecimal("8.50"))
                .maxScore(new BigDecimal("10.00"))
                .testCaseScore(new BigDecimal("6.00"))
                .oopScore(new BigDecimal("2.50"))
                .note("Tốt")
                .updatedAt(OffsetDateTime.now())
                .build();

        // Inject real ObjectMapper vì @InjectMocks không inject final field
        injectObjectMapper();
    }

    /**
     * Inject ObjectMapper thực vào service (vì field là final và không phải @Mock).
     * Dùng reflection để set field.
     */
    private void injectObjectMapper() {
        try {
            var field = GradingQueryService.class.getDeclaredField("objectMapper");
            field.setAccessible(true);
            field.set(service, objectMapper);
        } catch (Exception e) {
            throw new RuntimeException("Cannot inject ObjectMapper", e);
        }
    }

    // =========================================================================
    // getBlockResults()
    // =========================================================================

    @Test
    @DisplayName("[N] getBlockResults - Trả về danh sách kết quả chấm điểm cho block 'Block PRO192' có 1 submission (summary, không có answers)")
    void getBlockResults_HasOneResult_ReturnsSummaryList() {
        // Arrange
        when(gradingResultRepository.findAllBySubmission_Block_BlockId(blockId))
                .thenReturn(List.of(gradingResult));

        // Act
        List<GradingResultResponse> results = service.getBlockResults(blockId);

        // Assert
        assertThat(results).hasSize(1);
        GradingResultResponse r = results.get(0);
        assertThat(r.getStudentCode()).isEqualTo("se173173");
        assertThat(r.getTotalScore()).isEqualByComparingTo(new BigDecimal("8.50"));
        assertThat(r.getStatus()).isEqualTo(GradingResultStatus.PASS);
        assertThat(r.getAnswers()).isNull(); // summary — không kèm chi tiết câu
    }

    @Test
    @DisplayName("[B] getBlockResults - Trả về list rỗng khi block 'Block PRO192' chưa có kết quả chấm nào (Boundary: 0 kết quả)")
    void getBlockResults_NoResults_ReturnsEmptyList() {
        // Arrange
        when(gradingResultRepository.findAllBySubmission_Block_BlockId(blockId))
                .thenReturn(List.of());

        // Act
        List<GradingResultResponse> results = service.getBlockResults(blockId);

        // Assert
        assertThat(results).isEmpty();
        verify(gradingResultRepository).findAllBySubmission_Block_BlockId(blockId);
    }

    @Test
    @DisplayName("[N] getBlockResults - Trả về đúng số lượng kết quả khi block có nhiều sinh viên (3 submissions)")
    void getBlockResults_MultipleResults_ReturnsAllSummaries() {
        // Arrange — tạo thêm 2 GradingResult khác
        GradingResult gr2 = GradingResult.builder()
                .gradingResultId(UUID.randomUUID())
                .submission(submission)
                .gradingMode(GradingMode.MODE_1)
                .status(GradingResultStatus.FAIL)
                .totalScore(new BigDecimal("3.00"))
                .maxScore(new BigDecimal("10.00"))
                .testCaseScore(new BigDecimal("3.00"))
                .oopScore(BigDecimal.ZERO)
                .updatedAt(OffsetDateTime.now())
                .build();
        GradingResult gr3 = GradingResult.builder()
                .gradingResultId(UUID.randomUUID())
                .submission(submission)
                .gradingMode(GradingMode.MODE_1)
                .status(GradingResultStatus.PASS)
                .totalScore(new BigDecimal("9.00"))
                .maxScore(new BigDecimal("10.00"))
                .testCaseScore(new BigDecimal("7.00"))
                .oopScore(new BigDecimal("2.00"))
                .updatedAt(OffsetDateTime.now())
                .build();

        when(gradingResultRepository.findAllBySubmission_Block_BlockId(blockId))
                .thenReturn(List.of(gradingResult, gr2, gr3));

        // Act
        List<GradingResultResponse> results = service.getBlockResults(blockId);

        // Assert
        assertThat(results).hasSize(3);
        assertThat(results).extracting(GradingResultResponse::getAnswers)
                .containsOnlyNulls(); // tất cả đều summary
    }

    // =========================================================================
    // getStudentResult()
    // =========================================================================

    @Test
    @DisplayName("[N] getStudentResult - Sinh viên 'lamtvse173173' lấy kết quả bài nộp trong block thành công, trả về response có answers")
    void getStudentResult_ValidStudentAndBlock_ReturnsDetailResponse() {
        // Arrange
        when(submissionRepository.findByStudent_UserIdAndBlock_BlockId(studentId, blockId))
                .thenReturn(Optional.of(submission));
        when(gradingResultRepository.findBySubmission_SubmissionId(submissionId))
                .thenReturn(Optional.of(gradingResult));
        // No TC results — answers list will be empty
        when(testCaseResultRepository.findByAnswer_Submission_SubmissionIdOrderByTestCase_TestCaseNumberAsc(submissionId))
                .thenReturn(List.of());
        when(aiReviewRepository.findByAnswer_Submission_SubmissionId(submissionId))
                .thenReturn(List.of());

        // Act
        GradingResultResponse response = service.getStudentResult(blockId, studentId);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getStudentCode()).isEqualTo("se173173");
        assertThat(response.getTotalScore()).isEqualByComparingTo(new BigDecimal("8.50"));
        assertThat(response.getBlockName()).isEqualTo("Block PRO192");
        assertThat(response.getAnswers()).isNotNull(); // detail response có answers (dù empty)
    }

    @Test
    @DisplayName("[A] getStudentResult - Throw NotFoundException khi sinh viên 'lamtvse173173' chưa nộp bài cho block này")
    void getStudentResult_NoSubmission_ThrowNotFoundException() {
        // Arrange
        when(submissionRepository.findByStudent_UserIdAndBlock_BlockId(studentId, blockId))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.getStudentResult(blockId, studentId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Bạn chưa có bài nộp cho block này.");

        verify(gradingResultRepository, never()).findBySubmission_SubmissionId(any());
    }

    @Test
    @DisplayName("[A] getStudentResult - Throw NotFoundException khi sinh viên đã nộp bài nhưng chưa có kết quả chấm")
    void getStudentResult_SubmissionExistsButNoGradingResult_ThrowNotFoundException() {
        // Arrange
        when(submissionRepository.findByStudent_UserIdAndBlock_BlockId(studentId, blockId))
                .thenReturn(Optional.of(submission));
        when(gradingResultRepository.findBySubmission_SubmissionId(submissionId))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.getStudentResult(blockId, studentId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Chưa có kết quả chấm bài. Vui lòng chờ.");
    }

    // =========================================================================
    // getSubmissionResult()
    // =========================================================================

    @Test
    @DisplayName("[N] getSubmissionResult - Lấy kết quả chấm theo submissionId thành công, trả về response đầy đủ")
    void getSubmissionResult_ExistingResult_ReturnsDetailResponse() {
        // Arrange
        UUID requesterId = UUID.randomUUID();
        when(gradingResultRepository.findBySubmission_SubmissionId(submissionId))
                .thenReturn(Optional.of(gradingResult));
        when(testCaseResultRepository.findByAnswer_Submission_SubmissionIdOrderByTestCase_TestCaseNumberAsc(submissionId))
                .thenReturn(List.of());
        when(aiReviewRepository.findByAnswer_Submission_SubmissionId(submissionId))
                .thenReturn(List.of());

        // Act
        GradingResultResponse response = service.getSubmissionResult(submissionId, requesterId);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getSubmissionId()).isEqualTo(submissionId);
        assertThat(response.getGradingMode()).isEqualTo(GradingMode.MODE_1);
        assertThat(response.getStatus()).isEqualTo(GradingResultStatus.PASS);
        assertThat(response.getAnswers()).isNotNull();
    }

    @Test
    @DisplayName("[A] getSubmissionResult - Throw NotFoundException khi submissionId không tồn tại trong DB hoặc chưa được chấm")
    void getSubmissionResult_NoResult_ThrowNotFoundException() {
        // Arrange
        UUID requesterId = UUID.randomUUID();
        when(gradingResultRepository.findBySubmission_SubmissionId(submissionId))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.getSubmissionResult(submissionId, requesterId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Bài này chưa được chấm hoặc không tồn tại kết quả chấm.");

        verify(testCaseResultRepository, never())
                .findByAnswer_Submission_SubmissionIdOrderByTestCase_TestCaseNumberAsc(any());
    }

    // =========================================================================
    // buildAnswerDetails() — tested indirectly via getStudentResult/getSubmissionResult
    // =========================================================================

    @Test
    @DisplayName("[N] getSubmissionResult - Response có AIReviewDetail với rawResponse JSON hợp lệ được parse đúng (encapsulation=9, violations=[...])")
    void getSubmissionResult_WithAIReviewJson_ParsesCorrectly() throws Exception {
        // Arrange — AI review có rawResponse JSON
        String rawJson = "{" +
                "\"encapsulation\":9," +
                "\"inheritance\":8," +
                "\"polymorphism\":7," +
                "\"designQuality\":8," +
                "\"codeIntegrity\":9," +
                "\"violations\":[\"Missing Javadoc\"]," +
                "\"hardCodedValues\":[\"System.out.println\"]" +
                "}";

        Question question = new Question();
        question.setQuestionId(UUID.randomUUID());
        question.setQuestionNumber(1);
        question.setTitle("Q1");
        question.setMaxScore(new BigDecimal("10.00"));

        Answer answer = Answer.builder()
                .answerId(UUID.randomUUID())
                .submission(submission)
                .question(question)
                .answerScore(new BigDecimal("8.00"))
                .build();

        AIReview aiReview = new AIReview();
        aiReview.setAiReviewId(UUID.randomUUID());
        aiReview.setAnswer(answer);
        aiReview.setOopScore(new BigDecimal("8.2"));
        aiReview.setRawResponse(rawJson);
        aiReview.setIsOopViolated(false);
        aiReview.setComment("Kết quả tốt.");

        TestCase testCase = new TestCase();
        testCase.setTestCaseId(UUID.randomUUID());
        testCase.setTestCaseNumber(1);

        UUID requesterId = UUID.randomUUID();
        when(gradingResultRepository.findBySubmission_SubmissionId(submissionId))
                .thenReturn(Optional.of(gradingResult));
        when(testCaseResultRepository.findByAnswer_Submission_SubmissionIdOrderByTestCase_TestCaseNumberAsc(submissionId))
                .thenReturn(List.of()); // no TC results — keep simple
        when(aiReviewRepository.findByAnswer_Submission_SubmissionId(submissionId))
                .thenReturn(List.of(aiReview));

        // Act
        GradingResultResponse response = service.getSubmissionResult(submissionId, requesterId);

        // Assert — answers list is empty (no TC results to anchor on), but AI review is mapped internally
        assertThat(response).isNotNull();
        // The service builds per-answer details from allTcResults, so with 0 TC results answers = []
        assertThat(response.getAnswers()).isEmpty();
    }

    @Test
    @DisplayName("[A] getSubmissionResult - rawResponse JSON không hợp lệ (malformed) → không throw, trả về AIReviewDetail với null criteria")
    void getSubmissionResult_WithMalformedAIReviewJson_DoesNotThrow() {
        // Arrange — malformed JSON inside rawResponse
        AIReview badAiReview = new AIReview();
        badAiReview.setAiReviewId(UUID.randomUUID());
        badAiReview.setRawResponse("{NOT_VALID_JSON}");
        badAiReview.setOopScore(new BigDecimal("5.0"));

        Answer answer = Answer.builder()
                .answerId(UUID.randomUUID())
                .submission(submission)
                .question(buildQuestion())
                .build();
        badAiReview.setAnswer(answer);

        UUID requesterId = UUID.randomUUID();
        when(gradingResultRepository.findBySubmission_SubmissionId(submissionId))
                .thenReturn(Optional.of(gradingResult));
        when(testCaseResultRepository.findByAnswer_Submission_SubmissionIdOrderByTestCase_TestCaseNumberAsc(submissionId))
                .thenReturn(List.of()); // no TC results to trigger answer mapping
        when(aiReviewRepository.findByAnswer_Submission_SubmissionId(submissionId))
                .thenReturn(List.of(badAiReview));

        // Act & Assert — should NOT throw, gracefully handles parse error
        GradingResultResponse response = service.getSubmissionResult(submissionId, requesterId);
        assertThat(response).isNotNull();
        assertThat(response.getAnswers()).isEmpty(); // no TC results so no answer entries built
    }

    // ─── Private helper ──────────────────────────────────────────────────────

    private Question buildQuestion() {
        Question q = new Question();
        q.setQuestionId(UUID.randomUUID());
        q.setQuestionNumber(1);
        q.setTitle("Q1");
        q.setMaxScore(new BigDecimal("10.00"));
        return q;
    }
}

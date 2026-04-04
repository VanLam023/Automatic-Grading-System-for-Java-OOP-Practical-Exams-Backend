package agsfjope.backend.application.submissionservices.impl;

import agsfjope.backend.application.dtos.responses.submission.SubmissionResponse;
import agsfjope.backend.configuration.storage.MinioConfig;
import agsfjope.backend.core.entities.*;
import agsfjope.backend.core.exceptions.auth.NotFoundException;
import agsfjope.backend.core.exceptions.submission.ExamNotOngoingException;
import agsfjope.backend.core.repositories.auth.UserRepository;
import agsfjope.backend.core.repositories.block.BlockRepository;
import agsfjope.backend.core.repositories.config.SystemConfigRepository;
import agsfjope.backend.core.repositories.exam.ExamRepository;
import agsfjope.backend.core.repositories.exampaper.ExamPaperRepository;
import agsfjope.backend.core.repositories.exampaper.QuestionRepository;
import agsfjope.backend.core.repositories.submission.AnswerRepository;
import agsfjope.backend.core.repositories.submission.SubmissionRepository;
import agsfjope.backend.infrastructure.storage.MinioService;
import agsfjope.backend.infrastructure.storage.parser.ParsedSubmission;
import agsfjope.backend.infrastructure.storage.parser.SubmissionZipParser;
import agsfjope.backend.testutils.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for {@link SubmissionServiceImpl}.
 *
 * <p>3 public methods được test:
 * <ul>
 *   <li>{@code submit()} — 8 test cases (N/A/B)</li>
 *   <li>{@code getMySubmission()} — 3 test cases</li>
 *   <li>{@code downloadMySubmission()} — 1 test case</li>
 * </ul>
 * </p>
 *
 * <p>LOC của SubmissionServiceImpl: ~428 dòng.</p>
 */
@ExtendWith(MockitoExtension.class)
class SubmissionServiceImplTest {

    @Mock private ExamRepository        examRepository;
    @Mock private BlockRepository       blockRepository;
    @Mock private ExamPaperRepository   examPaperRepository;
    @Mock private QuestionRepository    questionRepository;
    @Mock private SubmissionRepository  submissionRepository;
    @Mock private AnswerRepository      answerRepository;
    @Mock private UserRepository        userRepository;
    @Mock private SystemConfigRepository systemConfigRepository;
    @Mock private MinioService          minioService;
    @Mock private MinioConfig           minioConfig;
    @Mock private MinioConfig.BucketConfig bucketConfig;
    @Mock private SubmissionZipParser   parser;

    @InjectMocks
    private SubmissionServiceImpl submissionService;

    // ── Shared fixtures ───────────────────────────────────────────────────────
    private UUID   examId;
    private UUID   blockId;
    private UUID   studentId;
    private Exam   exam;
    private Block  block;
    private User   student;

    @BeforeEach
    void setUp() {
        exam      = TestDataFactory.createOngoingExam();
        block     = TestDataFactory.createOngoingBlock(exam);
        student   = TestDataFactory.createActiveStudent();

        examId    = exam.getExamId();
        blockId   = block.getBlockId();
        studentId = student.getUserId();

        // Stub MinioConfig bucket
        lenient().when(minioConfig.getBucket()).thenReturn(bucketConfig);
        lenient().when(bucketConfig.getSubmissions()).thenReturn("submissions");
    }

    // =========================================================================
    // submit() — 8 test cases
    // =========================================================================

    @Test
    @DisplayName("[N] UTCID01 — submit: file .zip hợp lệ, ca thi đang diễn ra → lưu bài thành công")
    void submit_ValidZipOngoingBlock_ReturnSubmissionResponse() throws Exception {
        // Arrange
        MockMultipartFile file = makeZipFile("MySolution.zip", 512 * 1024); // 512 KB
        Question q1 = TestDataFactory.createQuestion(1);
        Submission saved = TestDataFactory.createSubmission(student, block);

        ParsedSubmission parsed = new ParsedSubmission(
                List.of(new ParsedSubmission.ParsedAnswer(1, "1/run/solution.jar", List.of("1/src/Main.java")))
        );

        when(examRepository.existsById(examId)).thenReturn(true);
        when(blockRepository.findByBlockId(blockId)).thenReturn(Optional.of(block));
        when(examPaperRepository.findByBlock_BlockId(blockId)).thenReturn(Optional.of(new ExamPaper()));
        when(systemConfigRepository.findByConfigKey("MAX_UPLOAD_SIZE_MB")).thenReturn(Optional.empty()); // fallback 50MB
        when(submissionRepository.findByStudent_UserIdAndBlock_BlockId(studentId, blockId)).thenReturn(Optional.empty());
        when(userRepository.findById(studentId)).thenReturn(Optional.of(student));
        doNothing().when(minioService).uploadFile(any(), any(), any(), any(), anyLong());
        when(submissionRepository.save(any())).thenReturn(saved);
        when(questionRepository.findByExamPaper_Block_BlockIdOrderByQuestionNumberAsc(blockId)).thenReturn(List.of(q1));
        when(answerRepository.saveAll(any())).thenReturn(List.of());
        when(parser.parseFromTempFile(any(), any())).thenReturn(parsed);

        // Act
        SubmissionResponse response = submissionService.submit(examId, blockId, studentId, file);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.isResubmit()).isFalse();
        verify(submissionRepository).save(any());
        verify(answerRepository).saveAll(any());
    }

    @Test
    @DisplayName("[A] UTCID02 — submit: examId không tồn tại → NotFoundException")
    void submit_ExamNotFound_ThrowsNotFoundException() {
        // Arrange
        MockMultipartFile file = makeZipFile("MySolution.zip", 512 * 1024);
        when(examRepository.existsById(examId)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> submissionService.submit(examId, blockId, studentId, file))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Không tìm thấy kỳ thi");
    }

    @Test
    @DisplayName("[A] UTCID03 — submit: blockId không tồn tại → NotFoundException")
    void submit_BlockNotFound_ThrowsNotFoundException() {
        // Arrange
        MockMultipartFile file = makeZipFile("MySolution.zip", 512 * 1024);
        when(examRepository.existsById(examId)).thenReturn(true);
        when(blockRepository.findByBlockId(blockId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> submissionService.submit(examId, blockId, studentId, file))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Không tìm thấy block");
    }

    @Test
    @DisplayName("[A] UTCID04 — submit: block thuộc exam khác → IllegalArgumentException")
    void submit_BlockBelongsToDifferentExam_ThrowsIllegalArgumentException() {
        // Arrange
        MockMultipartFile file = makeZipFile("MySolution.zip", 512 * 1024);
        Exam anotherExam = TestDataFactory.createOngoingExam(); // exam khác, UUID khác
        UUID anotherExamId = anotherExam.getExamId();

        when(examRepository.existsById(anotherExamId)).thenReturn(true);
        when(blockRepository.findByBlockId(blockId)).thenReturn(Optional.of(block)); // block thuộc examId gốc

        // Act & Assert
        assertThatThrownBy(() -> submissionService.submit(anotherExamId, blockId, studentId, file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không thuộc kỳ thi");
    }

    @Test
    @DisplayName("[A] UTCID05 — submit (BR-14): ca thi chưa bắt đầu → ExamNotOngoingException")
    void submit_BlockNotStartedYet_ThrowsExamNotOngoingException() {
        // Arrange
        MockMultipartFile file = makeZipFile("MySolution.zip", 512 * 1024);
        Block futureBlock = TestDataFactory.createNotStartedBlock(exam);
        UUID futureBlockId = futureBlock.getBlockId();

        when(examRepository.existsById(examId)).thenReturn(true);
        when(blockRepository.findByBlockId(futureBlockId)).thenReturn(Optional.of(futureBlock));

        // Act & Assert
        assertThatThrownBy(() -> submissionService.submit(examId, futureBlockId, studentId, file))
                .isInstanceOf(ExamNotOngoingException.class)
                .hasMessageContaining("chưa bắt đầu");
    }

    @Test
    @DisplayName("[A] UTCID06 — submit (BR-14): ca thi đã kết thúc → ExamNotOngoingException")
    void submit_BlockAlreadyEnded_ThrowsExamNotOngoingException() {
        // Arrange
        MockMultipartFile file = makeZipFile("MySolution.zip", 512 * 1024);
        Block finishedBlock = TestDataFactory.createFinishedBlock(exam);
        UUID finishedBlockId = finishedBlock.getBlockId();

        when(examRepository.existsById(examId)).thenReturn(true);
        when(blockRepository.findByBlockId(finishedBlockId)).thenReturn(Optional.of(finishedBlock));

        // Act & Assert
        assertThatThrownBy(() -> submissionService.submit(examId, finishedBlockId, studentId, file))
                .isInstanceOf(ExamNotOngoingException.class)
                .hasMessageContaining("đã kết thúc");
    }

    @Test
    @DisplayName("[A] UTCID07 — submit (BR-16): file sai định dạng (.txt) → IllegalStateException")
    void submit_InvalidFileExtension_ThrowsIllegalStateException() {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file", "MySolution.txt", "text/plain", new byte[1024]);

        when(examRepository.existsById(examId)).thenReturn(true);
        when(blockRepository.findByBlockId(blockId)).thenReturn(Optional.of(block));
        when(examPaperRepository.findByBlock_BlockId(blockId)).thenReturn(Optional.of(new ExamPaper()));
        when(systemConfigRepository.findByConfigKey("MAX_UPLOAD_SIZE_MB")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> submissionService.submit(examId, blockId, studentId, file))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Định dạng file không được hỗ trợ");
    }

    @Test
    @DisplayName("[N] UTCID08 — submit (BR-17): sinh viên đã nộp trước → xóa cũ, lưu mới (resubmit=true)")
    void submit_ResubmitOverwritesPrevious_ReturnsResubmitResponse() throws Exception {
        // Arrange
        MockMultipartFile file = makeZipFile("MySolution_v2.zip", 512 * 1024);
        Question q1 = TestDataFactory.createQuestion(1);
        Submission oldSubmission = TestDataFactory.createSubmission(student, block);
        Submission newSubmission = TestDataFactory.createSubmission(student, block);

        ParsedSubmission parsed = new ParsedSubmission(
                List.of(new ParsedSubmission.ParsedAnswer(1, "1/run/solution.jar", List.of()))
        );

        when(examRepository.existsById(examId)).thenReturn(true);
        when(blockRepository.findByBlockId(blockId)).thenReturn(Optional.of(block));
        when(examPaperRepository.findByBlock_BlockId(blockId)).thenReturn(Optional.of(new ExamPaper()));
        when(systemConfigRepository.findByConfigKey("MAX_UPLOAD_SIZE_MB")).thenReturn(Optional.empty());
        when(submissionRepository.findByStudent_UserIdAndBlock_BlockId(studentId, blockId))
                .thenReturn(Optional.of(oldSubmission)); // Đã có bài nộp cũ
        doNothing().when(answerRepository).deleteBySubmission_SubmissionId(any());
        doNothing().when(submissionRepository).deleteById(any());
        doNothing().when(minioService).deleteFile(any(), any());
        doNothing().when(submissionRepository).flush();
        when(userRepository.findById(studentId)).thenReturn(Optional.of(student));
        doNothing().when(minioService).uploadFile(any(), any(), any(), any(), anyLong());
        when(submissionRepository.save(any())).thenReturn(newSubmission);
        when(questionRepository.findByExamPaper_Block_BlockIdOrderByQuestionNumberAsc(blockId)).thenReturn(List.of(q1));
        when(answerRepository.saveAll(any())).thenReturn(List.of());
        when(parser.parseFromTempFile(any(), any())).thenReturn(parsed);

        // Act
        SubmissionResponse response = submissionService.submit(examId, blockId, studentId, file);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.isResubmit()).isTrue();
        verify(answerRepository).deleteBySubmission_SubmissionId(oldSubmission.getSubmissionId());
        verify(submissionRepository).deleteById(oldSubmission.getSubmissionId());
    }

    // =========================================================================
    // getMySubmission() — 3 test cases
    // =========================================================================

    @Test
    @DisplayName("[N] UTCID09 — getMySubmission: sinh viên 'lamtvse173173' có bài nộp → trả về SubmissionResponse")
    void getMySubmission_SubmissionExists_ReturnsResponse() {
        // Arrange
        Submission submission = TestDataFactory.createSubmission(student, block);
        Question q1 = TestDataFactory.createQuestion(1);
        Answer a1 = Answer.builder()
                .answerId(UUID.randomUUID())
                .submission(submission)
                .question(q1)
                .jarFilePath("1/run/sol.jar")
                .build();

        when(examRepository.existsById(examId)).thenReturn(true);
        when(blockRepository.findByBlockId(blockId)).thenReturn(Optional.of(block));
        when(submissionRepository.findByStudent_UserIdAndBlock_BlockId(studentId, blockId))
                .thenReturn(Optional.of(submission));
        when(answerRepository.findBySubmission_SubmissionIdOrderByQuestion_QuestionNumberAsc(submission.getSubmissionId()))
                .thenReturn(List.of(a1));
        when(questionRepository.findByExamPaper_Block_BlockIdOrderByQuestionNumberAsc(blockId))
                .thenReturn(List.of(q1));

        // Act
        SubmissionResponse response = submissionService.getMySubmission(examId, blockId, studentId);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getFileName()).isEqualTo("MySolution.zip");
        assertThat(response.getAnswers()).hasSize(1);
    }

    @Test
    @DisplayName("[A] UTCID10 — getMySubmission: sinh viên chưa nộp bài → NotFoundException")
    void getMySubmission_NoSubmission_ThrowsNotFoundException() {
        // Arrange
        when(examRepository.existsById(examId)).thenReturn(true);
        when(blockRepository.findByBlockId(blockId)).thenReturn(Optional.of(block));
        when(submissionRepository.findByStudent_UserIdAndBlock_BlockId(studentId, blockId))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> submissionService.getMySubmission(examId, blockId, studentId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Bạn chưa nộp bài");
    }

    @Test
    @DisplayName("[A] UTCID11 — getMySubmission: examId không tồn tại → NotFoundException")
    void getMySubmission_ExamNotFound_ThrowsNotFoundException() {
        // Arrange
        when(examRepository.existsById(examId)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> submissionService.getMySubmission(examId, blockId, studentId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Không tìm thấy kỳ thi");
    }

    // =========================================================================
    // downloadMySubmission() — 1 test case
    // =========================================================================

    @Test
    @DisplayName("[N] UTCID12 — downloadMySubmission: sinh viên có bài nộp → trả về InputStream file zip")
    void downloadMySubmission_SubmissionExists_ReturnsInputStream() {
        // Arrange
        Submission submission = TestDataFactory.createSubmission(student, block);
        InputStream fakeStream = new ByteArrayInputStream("fake-zip-bytes".getBytes());

        when(examRepository.existsById(examId)).thenReturn(true);
        when(blockRepository.findByBlockId(blockId)).thenReturn(Optional.of(block));
        when(submissionRepository.findByStudent_UserIdAndBlock_BlockId(studentId, blockId))
                .thenReturn(Optional.of(submission));
        when(minioService.downloadFile(any(), eq(submission.getFilePath())))
                .thenReturn(fakeStream);

        // Act
        InputStream result = submissionService.downloadMySubmission(examId, blockId, studentId);

        // Assert
        assertThat(result).isNotNull();
        verify(minioService).downloadFile("submissions", submission.getFilePath());
    }

    // =========================================================================
    // Helper
    // =========================================================================

    /** Tạo MockMultipartFile giả lập file .zip hợp lệ */
    private MockMultipartFile makeZipFile(String name, int sizeBytes) {
        byte[] content = new byte[sizeBytes];
        return new MockMultipartFile("file", name, "application/zip", content);
    }
}

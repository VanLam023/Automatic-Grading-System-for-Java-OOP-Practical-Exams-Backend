package agsfjope.backend.application.exampaperservices.impl;

import agsfjope.backend.application.dtos.responses.exampaper.ExamPaperResponse;
import agsfjope.backend.configuration.storage.MinioConfig;
import agsfjope.backend.core.entities.*;
import agsfjope.backend.core.exceptions.auth.NotFoundException;
import agsfjope.backend.core.exceptions.exampaper.ExamPaperHasSubmissionsException;
import agsfjope.backend.core.repositories.block.BlockRepository;
import agsfjope.backend.core.repositories.exam.ExamRepository;
import agsfjope.backend.core.repositories.exampaper.ExamPaperRepository;
import agsfjope.backend.core.repositories.exampaper.QuestionRepository;
import agsfjope.backend.core.repositories.exampaper.TestCaseRepository;
import agsfjope.backend.core.repositories.submission.SubmissionRepository;
import agsfjope.backend.core.repositories.auth.UserRepository;
import agsfjope.backend.infrastructure.storage.MinioService;
import agsfjope.backend.infrastructure.storage.parser.ParsedExamPaper;
import agsfjope.backend.infrastructure.storage.parser.ZipExamPaperParser;
import agsfjope.backend.testutils.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho ExamPaperServiceImpl.
 * Phân loại: [N] Normal, [B] Boundary, [A] Abnormal.
 * Pattern: AAA (Arrange - Act - Assert).
 *
 * Business rules tested:
 *  - BR-09: 1 Block = 1 ExamPaper (auto-overwrite on re-upload)
 *  - BR-11: Cannot modify/delete if submission exists
 *  - BR-16: Max file size = 20 MB
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExamPaperServiceImpl Tests")
class ExamPaperServiceImplTest {

    @Mock private ExamRepository       examRepository;
    @Mock private BlockRepository      blockRepository;
    @Mock private ExamPaperRepository  examPaperRepository;
    @Mock private QuestionRepository   questionRepository;
    @Mock private TestCaseRepository   testCaseRepository;
    @Mock private SubmissionRepository submissionRepository;
    @Mock private UserRepository       userRepository;
    @Mock private MinioService         minioService;
    @Mock private MinioConfig          minioConfig;
    @Mock private ZipExamPaperParser   parser;

    @InjectMocks
    private ExamPaperServiceImpl service;

    // ─── Shared fixtures ──────────────────────────────────────────────────────

    private UUID examId;
    private UUID blockId;
    private UUID staffId;
    private Block block;
    private User staff;
    private ExamPaper existingPaper;

    @BeforeEach
    void setUp() {
        examId  = UUID.randomUUID();
        blockId = UUID.randomUUID();

        // Exam entity
        Exam exam = new Exam();
        exam.setExamId(examId);
        exam.setSemester("SP");
        exam.setAcademicYear("2025");

        // Block entity (belongs to exam)
        block = new Block();
        block.setBlockId(blockId);
        block.setName("Block PRO192");
        block.setExam(exam);

        // Staff user — take UUID from factory (User.userId is not settable)
        staff   = TestDataFactory.createActiveStudent();
        staffId = staff.getUserId();

        // Existing paper (for overwrite/delete scenarios)
        existingPaper = new ExamPaper();
        existingPaper.setExamPaperId(UUID.randomUUID());
        existingPaper.setBlock(block);
        existingPaper.setFileName("OldDeThi.zip");
        existingPaper.setFilePath("exam-papers/Spring-2025/Block PRO192/OldDeThi.zip");
        existingPaper.setFileSizeBytes(1024L * 100);
        existingPaper.setTotalQuestions(2);
        existingPaper.setTotalTestCases(4);

        // Default MinioConfig bucket stub (used across multiple tests)
        MinioConfig.BucketConfig bucketConfig = new MinioConfig.BucketConfig();
        lenient().when(minioConfig.getBucket()).thenReturn(bucketConfig);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Tạo MultipartFile mock hợp lệ (file .zip, 1 MB) */
    private MultipartFile buildValidZipFile(String filename) throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        lenient().when(file.getOriginalFilename()).thenReturn(filename);
        lenient().when(file.getSize()).thenReturn(1024L * 1024L); // 1 MB
        lenient().when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        return file;
    }

    /** Tạo ParsedExamPaper mock đơn giản với 1 câu hỏi và 2 test cases */
    private ParsedExamPaper buildParsedExamPaper() {
        ParsedExamPaper.ParsedTestCase tc1 = new ParsedExamPaper.ParsedTestCase(
                1, "input1", "output1", new BigDecimal("5.00"));
        ParsedExamPaper.ParsedTestCase tc2 = new ParsedExamPaper.ParsedTestCase(
                2, "input2", "output2", new BigDecimal("5.00"));
        ParsedExamPaper.ParsedQuestion pq  = new ParsedExamPaper.ParsedQuestion(
                1, "Q1 Title", "Q1 Description", new BigDecimal("10.00"),
                false, true, List.of(tc1, tc2));
        return new ParsedExamPaper(List.of(pq));
    }

    // =========================================================================
    // upload()
    // =========================================================================

    @Test
    @DisplayName("[N] upload - Upload file 'DeThi.zip' (1 MB) thành công cho block 'Block PRO192', lưu ExamPaper + Questions + TestCases")
    void upload_ValidZipFile_SavesExamPaperSuccessfully() throws Exception {
        // Arrange
        MultipartFile file = buildValidZipFile("DeThi.zip");
        ParsedExamPaper parsed = buildParsedExamPaper();

        when(examRepository.existsById(examId)).thenReturn(true);
        when(blockRepository.findByBlockId(blockId)).thenReturn(Optional.of(block));
        when(submissionRepository.existsByBlock_BlockId(blockId)).thenReturn(false);
        when(examPaperRepository.findByBlock_BlockId(blockId)).thenReturn(Optional.empty());
        when(parser.parseFromTempFile(any(), eq(".zip"))).thenReturn(parsed);
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));

        ExamPaper savedPaper = new ExamPaper();
        savedPaper.setExamPaperId(UUID.randomUUID());
        savedPaper.setBlock(block);
        savedPaper.setFileName("DeThi.zip");
        savedPaper.setFileSizeBytes(1024L * 1024L);
        savedPaper.setTotalQuestions(1);
        savedPaper.setTotalTestCases(2);
        when(examPaperRepository.save(any(ExamPaper.class))).thenReturn(savedPaper);

        Question savedQ = new Question();
        savedQ.setQuestionId(UUID.randomUUID());
        savedQ.setExamPaper(savedPaper);
        savedQ.setQuestionNumber(1);
        savedQ.setTitle("Q1 Title");
        savedQ.setMaxScore(new BigDecimal("10.00"));
        when(questionRepository.save(any(Question.class))).thenReturn(savedQ);
        when(testCaseRepository.save(any(TestCase.class))).thenAnswer(inv -> inv.getArgument(0));
        when(testCaseRepository.findByQuestion_QuestionIdOrderByTestCaseNumberAsc(any())).thenReturn(List.of());

        // Act
        ExamPaperResponse response = service.upload(examId, blockId, staffId, file);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getFileName()).isEqualTo("DeThi.zip");
        assertThat(response.getTotalQuestions()).isEqualTo(1);
        verify(examPaperRepository).save(any(ExamPaper.class));
        verify(questionRepository).save(any(Question.class));
        verify(testCaseRepository, times(2)).save(any(TestCase.class));
        verify(minioService).uploadFile(any(), any(), any(), any(), anyLong());
    }

    @Test
    @DisplayName("[A] upload - Throw NotFoundException khi examId không tồn tại trong DB")
    void upload_ExamNotFound_ThrowNotFoundException() throws IOException {
        // Arrange
        MultipartFile file = buildValidZipFile("DeThi.zip");
        when(examRepository.existsById(examId)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> service.upload(examId, blockId, staffId, file))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Không tìm thấy kỳ thi với ID:");

        verify(blockRepository, never()).findByBlockId(any());
        verify(examPaperRepository, never()).save(any());
    }

    @Test
    @DisplayName("[A] upload - Throw NotFoundException khi blockId không tồn tại trong DB")
    void upload_BlockNotFound_ThrowNotFoundException() throws IOException {
        // Arrange
        MultipartFile file = buildValidZipFile("DeThi.zip");
        when(examRepository.existsById(examId)).thenReturn(true);
        when(blockRepository.findByBlockId(blockId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.upload(examId, blockId, staffId, file))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Không tìm thấy block với ID:");

        verify(examPaperRepository, never()).save(any());
    }

    @Test
    @DisplayName("[A] upload - Throw IllegalArgumentException khi block không thuộc exam")
    void upload_BlockNotBelongToExam_ThrowIllegalArgumentException() throws IOException {
        // Arrange — block có exam khác với examId đang truyền vào
        MultipartFile file = buildValidZipFile("DeThi.zip");
        Exam otherExam = new Exam();
        otherExam.setExamId(UUID.randomUUID()); // khác examId
        Block wrongBlock = new Block();
        wrongBlock.setBlockId(blockId);
        wrongBlock.setExam(otherExam);

        when(examRepository.existsById(examId)).thenReturn(true);
        when(blockRepository.findByBlockId(blockId)).thenReturn(Optional.of(wrongBlock));

        // Act & Assert
        assertThatThrownBy(() -> service.upload(examId, blockId, staffId, file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không thuộc kỳ thi");

        verify(examPaperRepository, never()).save(any());
    }

    @Test
    @DisplayName("[A] upload - Throw ExamPaperHasSubmissionsException khi block đã có submission (BR-11)")
    void upload_BlockHasSubmissions_ThrowExamPaperHasSubmissionsException() throws IOException {
        // Arrange
        MultipartFile file = buildValidZipFile("DeThi.zip");
        when(examRepository.existsById(examId)).thenReturn(true);
        when(blockRepository.findByBlockId(blockId)).thenReturn(Optional.of(block));
        when(submissionRepository.existsByBlock_BlockId(blockId)).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> service.upload(examId, blockId, staffId, file))
                .isInstanceOf(ExamPaperHasSubmissionsException.class)
                .hasMessageContaining("BR-11");

        verify(examPaperRepository, never()).save(any());
    }

    @Test
    @DisplayName("[A] upload - Throw IllegalStateException khi file rỗng (size=0)")
    void upload_EmptyFile_ThrowIllegalStateException() throws IOException {
        // Arrange
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("DeThi.zip");
        when(file.getSize()).thenReturn(0L); // file rỗng — Boundary

        when(examRepository.existsById(examId)).thenReturn(true);
        when(blockRepository.findByBlockId(blockId)).thenReturn(Optional.of(block));
        when(submissionRepository.existsByBlock_BlockId(blockId)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> service.upload(examId, blockId, staffId, file))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("File upload rỗng");
    }

    @Test
    @DisplayName("[B] upload - Throw IllegalStateException khi file vượt quá 20 MB (BR-16, Boundary: 20 MB + 1 byte)")
    void upload_FileTooLarge_ThrowIllegalStateException() throws IOException {
        // Arrange — file size = 20 MB + 1 byte (vượt ngưỡng boundary 20 MB)
        long oversizeBytes = 20L * 1024 * 1024 + 1;
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("TooLarge.zip");
        when(file.getSize()).thenReturn(oversizeBytes);

        when(examRepository.existsById(examId)).thenReturn(true);
        when(blockRepository.findByBlockId(blockId)).thenReturn(Optional.of(block));
        when(submissionRepository.existsByBlock_BlockId(blockId)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> service.upload(examId, blockId, staffId, file))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("20 MB (BR-16)");
    }

    @Test
    @DisplayName("[A] upload - Throw IllegalStateException khi file không phải .zip hoặc .rar (ví dụ: .docx)")
    void upload_InvalidFileExtension_ThrowIllegalStateException() throws IOException {
        // Arrange
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("DeThi.docx");
        when(file.getSize()).thenReturn(1024L * 512L); // 512 KB (hợp lệ về size)

        when(examRepository.existsById(examId)).thenReturn(true);
        when(blockRepository.findByBlockId(blockId)).thenReturn(Optional.of(block));
        when(submissionRepository.existsByBlock_BlockId(blockId)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> service.upload(examId, blockId, staffId, file))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Định dạng file không được hỗ trợ");
    }

    @Test
    @DisplayName("[N] upload (BR-09) - Upload đề mới khi block đã có đề cũ 'OldDeThi.zip' → xóa đề cũ rồi lưu đề mới")
    void upload_OverwriteExistingPaper_DeletesOldAndSavesNew() throws Exception {
        // Arrange
        MultipartFile file = buildValidZipFile("NewDeThi.zip");
        ParsedExamPaper parsed = buildParsedExamPaper();

        when(examRepository.existsById(examId)).thenReturn(true);
        when(blockRepository.findByBlockId(blockId)).thenReturn(Optional.of(block));
        when(submissionRepository.existsByBlock_BlockId(blockId)).thenReturn(false);
        when(examPaperRepository.findByBlock_BlockId(blockId)).thenReturn(Optional.of(existingPaper));
        when(parser.parseFromTempFile(any(), eq(".zip"))).thenReturn(parsed);
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));

        ExamPaper newSavedPaper = new ExamPaper();
        newSavedPaper.setExamPaperId(UUID.randomUUID());
        newSavedPaper.setBlock(block);
        newSavedPaper.setFileName("NewDeThi.zip");
        newSavedPaper.setFileSizeBytes(1024L * 1024L);
        newSavedPaper.setTotalQuestions(1);
        newSavedPaper.setTotalTestCases(2);
        when(examPaperRepository.save(any(ExamPaper.class))).thenReturn(newSavedPaper);

        Question savedQ = new Question();
        savedQ.setQuestionId(UUID.randomUUID());
        savedQ.setExamPaper(newSavedPaper);
        savedQ.setQuestionNumber(1);
        savedQ.setMaxScore(new BigDecimal("10.00"));
        when(questionRepository.save(any(Question.class))).thenReturn(savedQ);
        when(testCaseRepository.save(any(TestCase.class))).thenAnswer(inv -> inv.getArgument(0));
        when(testCaseRepository.findByQuestion_QuestionIdOrderByTestCaseNumberAsc(any())).thenReturn(List.of());

        // Act
        ExamPaperResponse response = service.upload(examId, blockId, staffId, file);

        // Assert — phải gọi xóa đề cũ (BR-09) rồi mới lưu đề mới
        verify(testCaseRepository).deleteByQuestion_ExamPaper_ExamPaperId(existingPaper.getExamPaperId());
        verify(questionRepository).deleteByExamPaper_ExamPaperId(existingPaper.getExamPaperId());
        verify(examPaperRepository).deleteById(existingPaper.getExamPaperId());
        verify(examPaperRepository).flush();
        assertThat(response.getFileName()).isEqualTo("NewDeThi.zip");
    }

    // =========================================================================
    // getByBlock()
    // =========================================================================

    @Test
    @DisplayName("[N] getByBlock - Lấy đề thi của block 'Block PRO192' thành công")
    void getByBlock_ExistingPaper_ReturnsResponse() {
        // Arrange
        when(examRepository.existsById(examId)).thenReturn(true);
        when(blockRepository.findByBlockId(blockId)).thenReturn(Optional.of(block));
        when(examPaperRepository.findByBlock_BlockId(blockId)).thenReturn(Optional.of(existingPaper));
        when(questionRepository.findByExamPaper_ExamPaperIdOrderByQuestionNumberAsc(existingPaper.getExamPaperId()))
                .thenReturn(List.of());

        // Act
        ExamPaperResponse response = service.getByBlock(examId, blockId);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getFileName()).isEqualTo("OldDeThi.zip");
        assertThat(response.getTotalQuestions()).isEqualTo(2);
    }

    @Test
    @DisplayName("[A] getByBlock - Throw NotFoundException khi block chưa có đề thi")
    void getByBlock_NoPaperForBlock_ThrowNotFoundException() {
        // Arrange
        when(examRepository.existsById(examId)).thenReturn(true);
        when(blockRepository.findByBlockId(blockId)).thenReturn(Optional.of(block));
        when(examPaperRepository.findByBlock_BlockId(blockId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.getByBlock(examId, blockId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("chưa có đề thi được upload");
    }

    // =========================================================================
    // deleteByBlock()
    // =========================================================================

    @Test
    @DisplayName("[N] deleteByBlock - Xóa đề thi của block 'Block PRO192' thành công khi không có submission")
    void deleteByBlock_NoPriorSubmission_DeletesSuccessfully() {
        // Arrange
        when(examRepository.existsById(examId)).thenReturn(true);
        when(blockRepository.findByBlockId(blockId)).thenReturn(Optional.of(block));
        when(examPaperRepository.findByBlock_BlockId(blockId)).thenReturn(Optional.of(existingPaper));
        when(submissionRepository.existsByBlock_BlockId(blockId)).thenReturn(false);

        // Act
        service.deleteByBlock(examId, blockId);

        // Assert — overwriteOldPaper() được gọi (xóa TC → Q → Paper → MinIO)
        verify(testCaseRepository).deleteByQuestion_ExamPaper_ExamPaperId(existingPaper.getExamPaperId());
        verify(questionRepository).deleteByExamPaper_ExamPaperId(existingPaper.getExamPaperId());
        verify(examPaperRepository).deleteById(existingPaper.getExamPaperId());
    }

    @Test
    @DisplayName("[A] deleteByBlock - Throw ExamPaperHasSubmissionsException khi block đã có submission (BR-11)")
    void deleteByBlock_HasSubmissions_ThrowExamPaperHasSubmissionsException() {
        // Arrange
        when(examRepository.existsById(examId)).thenReturn(true);
        when(blockRepository.findByBlockId(blockId)).thenReturn(Optional.of(block));
        when(examPaperRepository.findByBlock_BlockId(blockId)).thenReturn(Optional.of(existingPaper));
        when(submissionRepository.existsByBlock_BlockId(blockId)).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> service.deleteByBlock(examId, blockId))
                .isInstanceOf(ExamPaperHasSubmissionsException.class)
                .hasMessageContaining("BR-11");

        verify(examPaperRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("[A] deleteByBlock - Throw NotFoundException khi block chưa có đề thi để xóa")
    void deleteByBlock_NoPaperFound_ThrowNotFoundException() {
        // Arrange
        when(examRepository.existsById(examId)).thenReturn(true);
        when(blockRepository.findByBlockId(blockId)).thenReturn(Optional.of(block));
        when(examPaperRepository.findByBlock_BlockId(blockId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.deleteByBlock(examId, blockId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("chưa có đề thi để xóa");

        verify(examPaperRepository, never()).deleteById(any());
    }

    // =========================================================================
    // downloadByBlock()
    // =========================================================================

    @Test
    @DisplayName("[N] downloadByBlock - Download đề thi 'OldDeThi.zip' của block 'Block PRO192' thành công")
    void downloadByBlock_ExistingPaper_ReturnsInputStream() {
        // Arrange
        InputStream fakeStream = new ByteArrayInputStream(new byte[]{1, 2, 3});
        when(examRepository.existsById(examId)).thenReturn(true);
        when(blockRepository.findByBlockId(blockId)).thenReturn(Optional.of(block));
        when(examPaperRepository.findByBlock_BlockId(blockId)).thenReturn(Optional.of(existingPaper));
        when(minioService.downloadFile(any(), eq(existingPaper.getFilePath()))).thenReturn(fakeStream);

        // Act
        InputStream result = service.downloadByBlock(examId, blockId);

        // Assert
        assertThat(result).isNotNull().isSameAs(fakeStream);
        verify(minioService).downloadFile(any(), eq(existingPaper.getFilePath()));
    }

    @Test
    @DisplayName("[A] downloadByBlock - Throw NotFoundException khi block chưa có đề thi để download")
    void downloadByBlock_NoPaperFound_ThrowNotFoundException() {
        // Arrange
        when(examRepository.existsById(examId)).thenReturn(true);
        when(blockRepository.findByBlockId(blockId)).thenReturn(Optional.of(block));
        when(examPaperRepository.findByBlock_BlockId(blockId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.downloadByBlock(examId, blockId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("chưa có đề thi để download");

        verify(minioService, never()).downloadFile(any(), any());
    }
}

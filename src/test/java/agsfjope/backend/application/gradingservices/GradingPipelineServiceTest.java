package agsfjope.backend.application.gradingservices;

import agsfjope.backend.application.notificationservices.NotificationService;
import agsfjope.backend.application.ports.out.EmailService;
import agsfjope.backend.core.entities.*;
import agsfjope.backend.core.enums.GradingMode;
import agsfjope.backend.core.repositories.auth.UserRepository;
import agsfjope.backend.core.repositories.config.GradingModeConfigRepository;
import agsfjope.backend.core.repositories.exampaper.ExamPaperRepository;
import agsfjope.backend.core.repositories.exampaper.TestCaseRepository;
import agsfjope.backend.core.repositories.grading.CriteriaResultRepository;
import agsfjope.backend.core.repositories.grading.GradingCriteriaRepository;
import agsfjope.backend.core.repositories.grading.GradingResultRepository;
import agsfjope.backend.core.repositories.grading.TestCaseResultRepository;
import agsfjope.backend.core.repositories.submission.AnswerRepository;
import agsfjope.backend.core.repositories.submission.SubmissionRepository;
import agsfjope.backend.domain.grading.DeterministicOopScorer;
import agsfjope.backend.domain.grading.FinalGradingScore;
import agsfjope.backend.domain.grading.ScoreCalculator;
import agsfjope.backend.infrastructure.grading.ArchiveExtractor;
import agsfjope.backend.infrastructure.grading.ExecutionResult;
import agsfjope.backend.infrastructure.grading.JarSandboxExecutor;
import agsfjope.backend.infrastructure.grading.JavaParserAnalyzer;
import agsfjope.backend.infrastructure.grading.ReflectionAnalyzer;
import agsfjope.backend.infrastructure.grading.StaticAnalysisResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho GradingPipelineService.
 * Phân loại: [N] Normal, [B] Boundary, [A] Abnormal.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GradingPipelineService Tests")
class GradingPipelineServiceTest {

    @Mock private AnswerRepository answerRepository;
    @Mock private SubmissionRepository submissionRepository;
    @Mock private UserRepository userRepository;
    @Mock private TestCaseRepository testCaseRepository;
    @Mock private TestCaseResultRepository testCaseResultRepository;
    @Mock private GradingResultRepository gradingResultRepository;
    @Mock private ExamPaperRepository examPaperRepository;
    @Mock private GradingModeConfigRepository gradingModeConfigRepository;
    @Mock private GradingCriteriaRepository gradingCriteriaRepository;
    @Mock private CriteriaResultRepository criteriaResultRepository;
    @Mock private ArchiveExtractor archiveExtractor;
    @Mock private JarSandboxExecutor jarSandboxExecutor;
    @Mock private ScoreCalculator scoreCalculator;
    @Mock private JavaParserAnalyzer javaParserAnalyzer;
    @Mock private ReflectionAnalyzer reflectionAnalyzer;
    @Mock private DeterministicOopScorer deterministicOopScorer;
    @Mock private NotificationService notificationService;
    @Mock private EmailService emailService;
    @Mock private TransactionTemplate transactionTemplate;

    @InjectMocks
    private GradingPipelineService service;

    private Submission submission;
    private User gradedByUser;
    private Block block;
    private Exam exam;
    private User student;

    @BeforeEach
    void setUp() {
        // Mock TransactionTemplate
        lenient().when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> callback = inv.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        lenient().doAnswer(inv -> {
            Consumer<TransactionStatus> action = inv.getArgument(0);
            action.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        student = User.builder().userId(UUID.randomUUID()).email("st@ex.com").fullName("ST").build();
        gradedByUser = User.builder().userId(UUID.randomUUID()).build();
        exam = Exam.builder().examId(UUID.randomUUID()).gradingMode(GradingMode.MODE_1).build();
        block = Block.builder().blockId(UUID.randomUUID()).exam(exam).build();
        submission = Submission.builder().submissionId(UUID.randomUUID()).block(block).student(student).build();
    }

    @Test
    @DisplayName("[N] grade - Happy path chấm điểm thành công 1 submission")
    void grade_HappyPath_GradesSuccessfully() throws Exception {
        // Arrange
        UUID subId = submission.getSubmissionId();
        when(submissionRepository.findById(subId)).thenReturn(Optional.of(submission));
        when(userRepository.findById(gradedByUser.getUserId())).thenReturn(Optional.of(gradedByUser));

        ExamPaper examPaper = ExamPaper.builder().filePath("exam.zip").build();
        when(examPaperRepository.findByBlock_BlockId(block.getBlockId())).thenReturn(Optional.of(examPaper));

        GradingModeConfig modeConfig = GradingModeConfig.builder().mode(GradingMode.MODE_1).build();
        when(gradingModeConfigRepository.findByMode(GradingMode.MODE_1)).thenReturn(Optional.of(modeConfig));

        Question q = Question.builder().questionId(UUID.randomUUID()).questionNumber(1).maxScore(BigDecimal.TEN).removeSpaces(false).caseSensitive(false).build();
        Answer a = Answer.builder().answerId(UUID.randomUUID()).question(q).submission(submission).build();
        when(answerRepository.findBySubmission_SubmissionIdOrderByQuestion_QuestionNumberAsc(subId)).thenReturn(List.of(a));

        Path mockWorkDir = Path.of("/tmp/work");
        Path mockPreJar = Path.of("/tmp/work/student.jar");
        Path mockSrcDir = Path.of("/tmp/work/src");
        when(archiveExtractor.createWorkDir(anyString())).thenReturn(mockWorkDir);
        when(archiveExtractor.extractStudentJar(any(), any(), anyInt(), any(), any())).thenReturn(mockPreJar);
        when(archiveExtractor.extractStudentSources(any(), any(), anyInt(), any(), any())).thenReturn(mockSrcDir);
        when(archiveExtractor.extractExamClasses(any(), any(), anyInt(), any(), any())).thenReturn(mockWorkDir);

        TestCase tc = TestCase.builder().testCaseNumber(1).inputData("in").expectedOutput("OUTPUT: ok").score(BigDecimal.TEN).timeLimitMs(1000).build();
        when(testCaseRepository.findByQuestion_QuestionIdOrderByTestCaseNumberAsc(q.getQuestionId())).thenReturn(List.of(tc));

        ExecutionResult execResult = ExecutionResult.success("OUTPUT: ok", 100);
        when(jarSandboxExecutor.run(any(), any(), any(), anyString(), anyInt())).thenReturn(execResult);

        when(javaParserAnalyzer.analyze(any())).thenReturn(StaticAnalysisResult.failure("empty"));
        when(reflectionAnalyzer.analyze(any())).thenReturn(StaticAnalysisResult.failure("empty"));
        
        when(gradingCriteriaRepository.findByQuestion_QuestionIdOrderByDisplayOrderAsc(q.getQuestionId())).thenReturn(List.of());

        FinalGradingScore finalScore = new FinalGradingScore(List.of(), BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, true, null);
        when(scoreCalculator.calculate(any(), any())).thenReturn(finalScore);

        // Act
        service.grade(submission, gradedByUser, new HashSet<>());

        // Assert
        verify(gradingResultRepository).save(any(GradingResult.class));
        verify(submissionRepository).save(submission);
        verify(notificationService).createNotification(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("[A] grade - Submission không tồn tại -> Ném ngoại lệ IllegalStateException")
    void grade_SubmissionNotFound_ThrowsException() {
        // Arrange
        when(submissionRepository.findById(any())).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.grade(submission, gradedByUser, null))
                .isInstanceOf(IllegalStateException.class);
    }
    
    @Test
    @DisplayName("[A] grade - Block bị cancel -> Ném GradingCancelledException")
    void grade_BlockCancelled_ThrowsException() throws Exception {
        // Arrange
        UUID subId = submission.getSubmissionId();
        when(submissionRepository.findById(subId)).thenReturn(Optional.of(submission));
        when(userRepository.findById(gradedByUser.getUserId())).thenReturn(Optional.of(gradedByUser));

        ExamPaper examPaper = ExamPaper.builder().filePath("exam.zip").build();
        when(examPaperRepository.findByBlock_BlockId(block.getBlockId())).thenReturn(Optional.of(examPaper));

        GradingModeConfig modeConfig = GradingModeConfig.builder().mode(GradingMode.MODE_1).build();
        when(gradingModeConfigRepository.findByMode(GradingMode.MODE_1)).thenReturn(Optional.of(modeConfig));

        Question q = Question.builder().questionId(UUID.randomUUID()).questionNumber(1).maxScore(BigDecimal.TEN).build();
        Answer a = Answer.builder().answerId(UUID.randomUUID()).question(q).submission(submission).build();
        when(answerRepository.findBySubmission_SubmissionIdOrderByQuestion_QuestionNumberAsc(subId)).thenReturn(List.of(a));

        when(archiveExtractor.createWorkDir(anyString())).thenReturn(Path.of("/tmp/work"));
        
        Set<UUID> cancelledBlocks = new HashSet<>();
        cancelledBlocks.add(block.getBlockId());

        // Act & Assert
        assertThatThrownBy(() -> service.grade(submission, gradedByUser, cancelledBlocks))
                .isInstanceOf(GradingCancelledException.class);
    }
}

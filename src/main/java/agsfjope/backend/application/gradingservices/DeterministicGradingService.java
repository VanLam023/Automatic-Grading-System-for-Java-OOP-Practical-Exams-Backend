package agsfjope.backend.application.gradingservices;

import agsfjope.backend.core.entities.*;
import agsfjope.backend.core.enums.GradingMode;
import agsfjope.backend.core.enums.GradingResultStatus;
import agsfjope.backend.core.enums.SubmissionStatus;
import agsfjope.backend.core.repositories.exampaper.ExamPaperRepository;
import agsfjope.backend.core.repositories.grading.GradingResultRepository;
import agsfjope.backend.core.repositories.submission.AnswerRepository;
import agsfjope.backend.core.repositories.submission.SubmissionRepository;
import agsfjope.backend.domain.grading.Mode5ScoreCalculator;
import agsfjope.backend.infrastructure.grading.ArchiveExtractor;
import agsfjope.backend.infrastructure.grading.IsolatedStructureAnalyzer;
import agsfjope.backend.infrastructure.grading.IsolatedTestRunner;
import agsfjope.backend.application.notificationservices.NotificationService;
import agsfjope.backend.core.repositories.config.GradingModeConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * NEW isolated grading service for MODE_5 only.
 * Completely unrelated to GradingPipelineService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeterministicGradingService {

    private final SubmissionRepository submissionRepository;
    private final GradingResultRepository gradingResultRepository;
    private final AnswerRepository answerRepository;
    private final ExamPaperRepository examPaperRepository;
    private final GradingModeConfigRepository modeConfigRepository;
    private final ArchiveExtractor archiveExtractor;
    private final IsolatedStructureAnalyzer isolatedStructureAnalyzer;
    private final IsolatedTestRunner isolatedTestRunner;
    private final Mode5ScoreCalculator mode5ScoreCalculator;
    private final NotificationService notificationService;
    private final TransactionTemplate transactionTemplate;

    public void grade(Submission submission, User gradedByUser, Set<UUID> cancelledBlocks) {
        log.info("[MODE_5] Starting deterministic grading for submission {}", submission.getSubmissionId());
        
        UUID subId = submission.getSubmissionId();
        Block block = submission.getBlock();
        
        GradingModeConfig modeConfig = modeConfigRepository.findByMode(GradingMode.MODE_5)
                .orElseThrow(() -> new IllegalStateException("MODE_5 config not found"));

        ExamPaper examPaper = examPaperRepository.findByBlock_BlockId(block.getBlockId())
                .orElseThrow(() -> new IllegalStateException("Exam paper not found"));

        List<Answer> answers = answerRepository.findBySubmission_SubmissionIdOrderByQuestion_QuestionNumberAsc(subId);
        
        List<Mode5ScoreCalculator.Mode5QuestionInput> inputs = new ArrayList<>();
        Path subWorkDir = null;

        try {
            subWorkDir = archiveExtractor.createWorkDir("det_" + subId);
            String examExt = getFileExtension(examPaper.getFilePath());
            
            for (Answer answer : answers) {
                if (cancelledBlocks.contains(block.getBlockId())) {
                    throw new RuntimeException("Grading cancelled");
                }

                Question q = answer.getQuestion();
                Path qWorkDir = archiveExtractor.createWorkDir("det_" + subId + "_q" + q.getQuestionNumber());
                
                String subExt = getFileExtension(submission.getFilePath());
                Path jar = archiveExtractor.extractStudentJar("submissions", submission.getFilePath(), q.getQuestionNumber(), subExt, qWorkDir);
                Path src = archiveExtractor.extractStudentSources("submissions", submission.getFilePath(), q.getQuestionNumber(), subExt, qWorkDir);

                // 1. Run Tests (Isolated)
                IsolatedTestRunner.TestResultSummary tcResult = isolatedTestRunner.runTests(
                        submission, examPaper, answer, q, qWorkDir, examExt, jar);

                // 2. Run Structure Analysis (Isolated)
                IsolatedStructureAnalyzer.CheckResult structResult = (src != null) 
                        ? isolatedStructureAnalyzer.analyze(src, jar)
                        : new IsolatedStructureAnalyzer.CheckResult(0, 0, List.of("Source not found"));

                inputs.add(new Mode5ScoreCalculator.Mode5QuestionInput(
                        q.getQuestionNumber(), q.getMaxScore(), tcResult.passCount(), tcResult.totalCount(), structResult
                ));
            }

            // 3. Calculate Results
            Mode5ScoreCalculator.Mode5Result m5Result = mode5ScoreCalculator.calculate(modeConfig, inputs);
            
            // 4. Save to GradingResults table
            BigDecimal totalMax = inputs.stream().map(Mode5ScoreCalculator.Mode5QuestionInput::maxScore).reduce(BigDecimal.ZERO, BigDecimal::add);
            boolean passed = m5Result.finalScore().compareTo(new BigDecimal("4.0")) >= 0;

            transactionTemplate.executeWithoutResult(status -> {
                gradingResultRepository.deleteBySubmission_SubmissionId(subId);
                
                GradingResult result = GradingResult.builder()
                        .submission(submission)
                        .gradingMode(GradingMode.MODE_5)
                        .status(passed ? GradingResultStatus.PASS : GradingResultStatus.FAIL)
                        .totalScore(m5Result.finalScore())
                        .maxScore(totalMax)
                        .testCaseScore(m5Result.tcComponent())
                        .oopScore(BigDecimal.ZERO)
                        .gradedBy(gradedByUser)
                        .note(String.join(" | ", m5Result.notes()))
                        .build();
                
                gradingResultRepository.save(result);
                
                // Update submission status
                submission.setStatus(SubmissionStatus.GRADED);
                submissionRepository.save(submission);
            });

            // 5. Send notification (replicated from PipelineService)
            try {
                UUID studentId = submission.getStudent().getUserId();
                String examName = block.getExam() != null ? block.getExam().getName() : "kỳ thi";
                String blockName = block.getName() != null ? block.getName() : "block";
                String statusText = passed ? "✅ ĐẠT" : "❌ KHÔNG ĐẠT";
                String title = "Kết quả chấm bài: " + examName;
                String body = String.format(
                        "Bài nộp của bạn trong %s — %s đã được chấm xong.\n" +
                        "Điểm số: %.2f | Kết quả: %s\n" +
                        "Nhấn để xem chi tiết kết quả.",
                        examName, blockName, m5Result.finalScore(), statusText);

                notificationService.createNotification(studentId, title, body, "GRADING_RESULT", submission.getSubmissionId());
            } catch (Exception e) {
                log.warn("Failed to send notification for submission {}: {}", subId, e.getMessage());
            }

        } catch (Exception e) {
            log.error("[MODE_5] Critical failure for submission {}: {}", subId, e.getMessage());
            transactionTemplate.executeWithoutResult(status -> {
                submission.setStatus(SubmissionStatus.SUBMITTED);
                submissionRepository.save(submission);
            });
        } finally {
            if (subWorkDir != null) archiveExtractor.cleanupWorkDir(subWorkDir);
        }
    }

    private String getFileExtension(String filePath) {
        if (filePath == null) return ".zip";
        int idx = filePath.lastIndexOf('.');
        return idx >= 0 ? filePath.substring(idx).toLowerCase() : ".zip";
    }
}

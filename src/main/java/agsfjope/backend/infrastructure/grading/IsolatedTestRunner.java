package agsfjope.backend.infrastructure.grading;

import agsfjope.backend.core.entities.*;
import agsfjope.backend.core.enums.TestCaseStatus;
import agsfjope.backend.core.repositories.exampaper.TestCaseRepository;
import agsfjope.backend.core.repositories.grading.TestCaseResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class IsolatedTestRunner {

    private final JarSandboxExecutor jarSandboxExecutor;
    private final TestCaseRepository testCaseRepository;
    private final TestCaseResultRepository testCaseResultRepository;
    private final ArchiveExtractor archiveExtractor;

    public record TestResultSummary(int passCount, int totalCount, List<TestCaseResult> details) {}

    public TestResultSummary runTests(Submission sub, ExamPaper paper, Answer answer, Question q, Path workDir, String examExt, Path studentJar) {
        log.info("[IsolatedRunner] Running tests for Q{} of submission {}", q.getQuestionNumber(), sub.getSubmissionId());
        
        List<TestCase> testCases = testCaseRepository.findByQuestion_QuestionIdOrderByTestCaseNumberAsc(q.getQuestionId());
        List<TestCaseResult> results = new ArrayList<>();
        int passed = 0;

        // 1. Extract exam classes for verification
        Path examClassDir = null;
        Map<String, String> examChecksums = null;
        try {
            examClassDir = archiveExtractor.extractExamClasses("exam-papers", paper.getFilePath(), q.getQuestionNumber(), examExt, workDir);
            if (examClassDir != null) {
                examChecksums = jarSandboxExecutor.computeChecksums(examClassDir);
            }
        } catch (Exception e) {
            log.error("Exam class extraction failed: {}", e.getMessage());
        }

        // 2. Loop test cases
        for (TestCase tc : testCases) {
            String input = prepareInput(tc.getInputData(), q.getRemoveSpaces());
            ExecutionResult exec = jarSandboxExecutor.run(studentJar, examClassDir, examChecksums, input, tc.getTimeLimitMs());
            
            String actual = extractOutputSection(exec.stdout());
            boolean isPassed = compareOutput(actual, tc.getExpectedOutput(), q.getCaseSensitive()) && !exec.timeout() && !exec.tamperedFiles() && exec.exitCode() == 0;
            
            TestCaseResult tcr = TestCaseResult.builder()
                    .testCaseResultId(UUID.randomUUID())
                    .answer(answer)
                    .testCase(tc)
                    .actualOutput(actual)
                    .status(isPassed ? TestCaseStatus.PASS_TESTCASE : (exec.timeout() ? TestCaseStatus.TIMEOUT : TestCaseStatus.FAIL_TESTCASE))
                    .executionTimeMs((int) exec.executionTimeMs())
                    .scoreEarned(isPassed ? tc.getScore() : BigDecimal.ZERO)
                    .errorMessage(exec.errorMessage())
                    .build();
            
            results.add(tcr);
            if (isPassed) passed++;
        }

        testCaseResultRepository.saveAll(results);
        return new TestResultSummary(passed, testCases.size(), results);
    }

    private String prepareInput(String input, boolean removeSpaces) {
        if (input == null) return "\n";
        String normalized = input.replace("\r\n", "\n").replace("\r", "\n");
        if (!removeSpaces) return normalized.endsWith("\n") ? normalized : normalized + "\n";
        return normalized.lines().map(String::strip).filter(l -> !l.isEmpty())
                .map(l -> l.replaceAll("\\s+", "\n"))
                .collect(java.util.stream.Collectors.joining("\n")) + "\n";
    }

    private String extractOutputSection(String stdout) {
        if (stdout == null) return "";
        String upper = stdout.toUpperCase();
        int idx = upper.lastIndexOf("OUTPUT:");
        if (idx < 0) return stdout.stripTrailing();
        return stdout.substring(idx + "OUTPUT:".length()).stripLeading().stripTrailing();
    }

    private boolean compareOutput(String actual, String expected, boolean caseSensitive) {
        if (actual == null || expected == null) return false;
        String a = actual.stripTrailing();
        String e = expected.stripTrailing();
        return caseSensitive ? a.equals(e) : a.equalsIgnoreCase(e);
    }
}

package agsfjope.backend.domain.grading;

import java.math.BigDecimal;


/**
 * Immutable result of scoring a single question (Answer) for one submission.
 *
 * <p>Contains the raw scores (before guard rules), the final question score
 * (after guard rules applied), and the note explaining any guard rule trigger.</p>
 *
 * @param questionNumber      question number (1-based)
 * @param maxScore            maximum achievable score for this question
 * @param rawTcScore          raw test case score before guard rules
 * @param rawOopScore         raw OOP score before guard rules (0 if AI skipped)
 * @param rawStructureScore   raw structure check score (JavaParser + Reflection, 0 if not run)
 * @param finalQuestionScore  final score after guard rules applied
 * @param guardRuleTriggered  true if a guard rule forced the score to 0
 * @param note                guard rule explanation note (null if no guard triggered)
 *                            e.g. "Điểm gốc: TC=6.0, OOP=8.0 → 0đ do vi phạm OOP"
 * @param passTcCount         number of PASS_TESTCASE test cases
 * @param totalTcCount        total number of test cases
 */
public record QuestionScore(
        int questionNumber,
        BigDecimal maxScore,
        BigDecimal rawTcScore,
        BigDecimal rawOopScore,
        BigDecimal finalQuestionScore,
        boolean guardRuleTriggered,
        String note,
        int passTcCount,
        int totalTcCount
) {}

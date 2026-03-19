package agsfjope.backend.application.dtos.responses.submission;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

/**
 * Response DTO representing one answer entry within a {@link SubmissionResponse}.
 *
 * <p>Each Answer corresponds to one Question in the exam paper.</p>
 *
 * <ul>
 *   <li>{@code hasJar = false} → student did not submit a compiled .jar for this question
 *       → will receive 0 test-case score when graded</li>
 *   <li>{@code hasSource = false} → student did not submit source code for this question
 *       → AI OOP evaluation cannot be performed</li>
 * </ul>
 */
@Value
@Builder
public class AnswerResponse {

    /** Unique identifier of the answer record. */
    UUID answerId;

    /** The question number this answer corresponds to (1-based). */
    int questionNumber;

    /** Title of the question (for display). */
    String questionTitle;

    /**
     * Whether the student submitted a compiled .jar for this question.
     * Maps to {@code Answer.jarFilePath != null}.
     */
    boolean hasJar;

    /**
     * Whether the student submitted source code (.java files) for this question.
     * Maps to {@code Answer.sourceCodePath != null}.
     */
    boolean hasSource;

    /**
     * Warning message if the answer is incomplete.
     * Examples:
     * <ul>
     *   <li>"Sinh viên không có bài nộp cho câu hỏi này"</li>
     *   <li>"Sinh viên nộp bài không có file compile (.jar) cho câu này"</li>
     * </ul>
     * {@code null} if the answer is complete.
     */
    String warningMessage;
}

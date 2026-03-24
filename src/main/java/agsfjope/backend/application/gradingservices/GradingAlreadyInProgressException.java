package agsfjope.backend.application.gradingservices;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when staff attempts to trigger grading for a block
 * that is already being graded (GRADE_ALL concurrent trigger guard).
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class GradingAlreadyInProgressException extends RuntimeException {
    public GradingAlreadyInProgressException(String message) {
        super(message);
    }
}

package agsfjope.backend.infrastructure.ai;

/**
 * Input data for the AI OOP review request.
 *
 * @param questionTitle       title of the exam question
 * @param questionDescription full description of the question including UML class diagram
 *                            with +/- notation for public/private members
 * @param sourceCode          concatenated student .java source files
 * @param language            language for review comments (e.g., "Vietnamese", "English")
 */
public record AIReviewRequest(
        String questionTitle,
        String questionDescription,
        String sourceCode,
        String language
) {}

package agsfjope.backend.infrastructure.ai;

import java.math.BigDecimal;

/**
 * Input data for the AI OOP review request.
 *
 * @param questionTitle       title of the exam question
 * @param questionDescription full description of the question including UML class diagram
 *                            with +/- notation for public/private members
 * @param sourceCode          concatenated student .java source files
 * @param structuredAnalysis  pre-computed structured analysis report from JavaParser + Java Reflection;
 *                            contains OOP metrics, encapsulation/inheritance/polymorphism issues,
 *                            and hard-coded value suspects — gives AI "hard facts" to improve scoring.
 *                            May be null if static analysis was not performed or failed.
 * @param language            language for review comments (e.g., "Vietnamese", "English")
 * @param maxScore            maximum score for this question as defined in the exam paper
 */
public record AIReviewRequest(
        String questionTitle,
        String questionDescription,
        String sourceCode,
        String structuredAnalysis,
        String language,
        BigDecimal maxScore
) {}


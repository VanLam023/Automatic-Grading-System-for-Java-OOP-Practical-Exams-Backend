package agsfjope.backend.infrastructure.grading;

import java.util.Collections;
import java.util.List;

/**
 * Immutable result of static code analysis performed on a student's Java submission.
 *
 * <p>Aggregates findings from two complementary analyzers:</p>
 * <ul>
 *   <li>{@link JavaParserAnalyzer} — AST-level analysis (class structure, OOP violations,
 *       hard-coded value detection) via JavaParser library.</li>
 *   <li>{@link ReflectionAnalyzer} — Runtime metadata analysis (actual class hierarchy,
 *       interface implementations, method signatures) via Java Reflection on the student JAR.</li>
 * </ul>
 *
 * <p>Instances of this record are:
 * <ol>
 *   <li>Serialized to a human-readable report via {@link #toFormattedReport()} and embedded
 *       in the AI prompt — giving AI "hard facts" to improve scoring accuracy.</li>
 *   <li>Used directly by {@link agsfjope.backend.domain.grading.ScoreCalculator} to detect
 *       hardcoded values and force 0 score immediately, without waiting for the AI response.</li>
 * </ol>
 * </p>
 *
 * @param classCount              number of concrete (non-interface, non-abstract) classes
 * @param interfaceCount          number of interface declarations
 * @param abstractClassCount      number of abstract class declarations
 * @param classNames              list of concrete class names found in source
 * @param interfaceNames          list of interface names found in source
 * @param extendsRelations        human-readable inheritance relations, e.g. "Student extends Person"
 * @param implementsRelations     human-readable implementation relations, e.g. "Student implements IStudent"
 * @param encapsulationIssues     violations of encapsulation rules (non-private fields, missing getters/setters)
 * @param inheritanceIssues       misuse of inheritance vs composition (is-a vs has-a confusion)
 * @param polymorphismIssues      polymorphism violations (missing @Override, abstract not implemented, etc.)
 * @param hardCodedSuspects       lines with suspected hard-coded literal return values
 * @param totalFields             total number of fields across all classes
 * @param privateFieldCount       number of fields declared private
 * @param totalMethods            total number of methods across all classes
 * @param overriddenMethodCount   number of methods annotated with {@code @Override}
 * @param abstractMethodCount     number of abstract method declarations
 * @param hasAbstractClass        true if at least one abstract class is declared
 * @param hasInterface            true if at least one interface is declared
 * @param reflectionClassStructure runtime class structure from Reflection (class, superclass, interfaces, method count)
 * @param reflectionIssues        issues detected via Reflection (missing expected interfaces, wrong access modifiers, etc.)
 * @param reflectionSuccess       false if JAR loading or class inspection failed
 * @param errorMessage            null on success; error detail if either analyzer failed
 */
public record StaticAnalysisResult(

        // ── JavaParser Analysis ─────────────────────────────────────────────
        int classCount,
        int interfaceCount,
        int abstractClassCount,
        List<String> classNames,
        List<String> interfaceNames,
        List<String> extendsRelations,
        List<String> implementsRelations,
        List<String> encapsulationIssues,
        List<String> inheritanceIssues,
        List<String> polymorphismIssues,
        List<String> hardCodedSuspects,
        int totalFields,
        int privateFieldCount,
        int totalMethods,
        int overriddenMethodCount,
        int abstractMethodCount,
        boolean hasAbstractClass,
        boolean hasInterface,

        // ── Reflection Analysis ─────────────────────────────────────────────
        List<String> reflectionClassStructure,
        List<String> reflectionIssues,
        boolean reflectionSuccess,

        // ── General ─────────────────────────────────────────────────────────
        String errorMessage

) {

    // ─── FACTORY METHODS ─────────────────────────────────────────────────────

    /**
     * Factory: analysis completely failed (e.g., source files unreadable).
     * All lists are empty; numeric counters are 0.
     *
     * @param reason human-readable explanation of why analysis failed
     */
    public static StaticAnalysisResult failure(String reason) {
        return new StaticAnalysisResult(
                0, 0, 0,
                Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(),
                0, 0, 0, 0, 0,
                false, false,
                Collections.emptyList(), Collections.emptyList(),
                false,
                reason
        );
    }

    /**
     * Factory: merge JavaParser result and Reflection result into a single combined record.
     * The parser result provides all AST fields; the reflection result contributes
     * {@code reflectionClassStructure}, {@code reflectionIssues}, and {@code reflectionSuccess}.
     *
     * @param parserResult     result from {@link JavaParserAnalyzer}
     * @param reflectionResult result from {@link ReflectionAnalyzer}
     * @return merged {@code StaticAnalysisResult}
     */
    public static StaticAnalysisResult merge(StaticAnalysisResult parserResult,
                                             StaticAnalysisResult reflectionResult) {
        return new StaticAnalysisResult(
                // AST fields come from JavaParser
                parserResult.classCount(),
                parserResult.interfaceCount(),
                parserResult.abstractClassCount(),
                parserResult.classNames(),
                parserResult.interfaceNames(),
                parserResult.extendsRelations(),
                parserResult.implementsRelations(),
                parserResult.encapsulationIssues(),
                parserResult.inheritanceIssues(),
                parserResult.polymorphismIssues(),
                parserResult.hardCodedSuspects(),
                parserResult.totalFields(),
                parserResult.privateFieldCount(),
                parserResult.totalMethods(),
                parserResult.overriddenMethodCount(),
                parserResult.abstractMethodCount(),
                parserResult.hasAbstractClass(),
                parserResult.hasInterface(),

                // Runtime fields come from Reflection
                reflectionResult.reflectionClassStructure(),
                reflectionResult.reflectionIssues(),
                reflectionResult.reflectionSuccess(),

                // Combine error messages if both failed
                buildCombinedError(parserResult.errorMessage(), reflectionResult.errorMessage())
        );
    }

    // ─── FORMATTING ──────────────────────────────────────────────────────────

    /**
     * Produces a compact, human-readable structured report to be embedded in the AI prompt.
     *
     * <p>Format is designed to be easily read by both AI and humans, using
     * ASCII separators and labeled sections.</p>
     *
     * @return formatted multi-line string report
     */
    public String toFormattedReport() {
        StringBuilder sb = new StringBuilder();

        // ── Overview
        sb.append("=== STATIC CODE ANALYSIS REPORT (JavaParser + Reflection) ===\n\n");

        sb.append("── CLASS STRUCTURE ──\n");
        sb.append("  Concrete classes  : ").append(classCount).append("\n");
        sb.append("  Interfaces        : ").append(interfaceCount).append("\n");
        sb.append("  Abstract classes  : ").append(abstractClassCount).append("\n");

        if (!classNames.isEmpty()) {
            sb.append("  Class names       : ").append(String.join(", ", classNames)).append("\n");
        }
        if (!interfaceNames.isEmpty()) {
            sb.append("  Interface names   : ").append(String.join(", ", interfaceNames)).append("\n");
        }

        // ── Relationships
        sb.append("\n── INHERITANCE & IMPLEMENTATION ──\n");
        if (!extendsRelations.isEmpty()) {
            extendsRelations.forEach(r -> sb.append("  ").append(r).append("\n"));
        } else {
            sb.append("  (no extends relationships found)\n");
        }
        if (!implementsRelations.isEmpty()) {
            implementsRelations.forEach(r -> sb.append("  ").append(r).append("\n"));
        } else {
            sb.append("  (no implements relationships found)\n");
        }

        // ── Field & Method Summary
        sb.append("\n── FIELD & METHOD SUMMARY ──\n");
        sb.append("  Total fields    : ").append(totalFields)
          .append("  (private: ").append(privateFieldCount).append(")\n");
        sb.append("  Total methods   : ").append(totalMethods)
          .append("  (overridden: ").append(overriddenMethodCount)
          .append(", abstract: ").append(abstractMethodCount).append(")\n");

        // ── OOP Issues (only print if violations exist)
        if (!encapsulationIssues.isEmpty()) {
            sb.append("\n── ENCAPSULATION ISSUES ──\n");
            encapsulationIssues.forEach(v -> sb.append("  ⚠ ").append(v).append("\n"));
        }
        if (!inheritanceIssues.isEmpty()) {
            sb.append("\n── INHERITANCE ISSUES ──\n");
            inheritanceIssues.forEach(v -> sb.append("  ⚠ ").append(v).append("\n"));
        }
        if (!polymorphismIssues.isEmpty()) {
            sb.append("\n── POLYMORPHISM ISSUES ──\n");
            polymorphismIssues.forEach(v -> sb.append("  ⚠ ").append(v).append("\n"));
        }

        // ── Hard-coded suspects (always include — critical for anti-cheat)
        sb.append("\n── HARD-CODED VALUE SUSPECTS (from AST) ──\n");
        if (!hardCodedSuspects.isEmpty()) {
            sb.append("  *** POTENTIAL HARDCODE DETECTED ***\n");
            hardCodedSuspects.forEach(h -> sb.append("  ❌ ").append(h).append("\n"));
        } else {
            sb.append("  (none detected by static analysis)\n");
        }

        // ── Reflection results (runtime verification)
        sb.append("\n── RUNTIME STRUCTURE (Java Reflection) ──\n");
        if (reflectionSuccess) {
            if (!reflectionClassStructure.isEmpty()) {
                reflectionClassStructure.forEach(s -> sb.append("  ").append(s).append("\n"));
            }
            if (!reflectionIssues.isEmpty()) {
                sb.append("\n  Runtime issues:\n");
                reflectionIssues.forEach(i -> sb.append("  ⚠ ").append(i).append("\n"));
            }
        } else {
            sb.append("  (Reflection analysis unavailable — JAR may be missing or unreadable)\n");
        }

        sb.append("\n=== END STATIC ANALYSIS ===\n");
        return sb.toString();
    }

    // ─── PRIVATE HELPERS ─────────────────────────────────────────────────────

    /** Combines two nullable error messages into one, or returns null if both are null. */
    private static String buildCombinedError(String parserErr, String reflectionErr) {
        if (parserErr == null && reflectionErr == null) return null;
        if (parserErr == null) return "Reflection: " + reflectionErr;
        if (reflectionErr == null) return "Parser: " + parserErr;
        return "Parser: " + parserErr + " | Reflection: " + reflectionErr;
    }
}

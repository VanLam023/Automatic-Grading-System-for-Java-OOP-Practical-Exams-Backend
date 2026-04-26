package agsfjope.backend.domain.grading;

/**
 * A single deterministic OOP structure check result.
 *
 * <p>Produced by {@link agsfjope.backend.infrastructure.grading.StructureAnalyzer}
 * using JavaParser (AST) and Java Reflection (runtime). Unlike AI review,
 * these checks are 100% deterministic — same code always produces the same result.</p>
 *
 * @param category   check category: FIELD_MODIFIER, GETTER_SETTER, INHERITANCE,
 *                   INTERFACE, ABSTRACT, CONSTRUCTOR, HARDCODE
 * @param checkName  human-readable check name, e.g. "field_private_name"
 * @param className  the class being checked, e.g. "Product"
 * @param passed     true if the check passed
 * @param detail     explanation, e.g. "Field 'name' is PUBLIC, expected PRIVATE"
 */
public record StructureCheck(
        String category,
        String checkName,
        String className,
        boolean passed,
        String detail
) {}

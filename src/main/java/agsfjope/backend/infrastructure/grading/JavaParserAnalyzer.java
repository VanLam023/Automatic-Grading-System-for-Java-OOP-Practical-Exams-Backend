package agsfjope.backend.infrastructure.grading;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ReturnStmt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Analyzes Java source files using JavaParser to extract OOP structure metrics
 * and detect potential violations before sending code to the AI reviewer.
 *
 * <h3>Analysis scope:</h3>
 * <ul>
 *   <li>Class/interface/abstract class counts and names</li>
 *   <li>Encapsulation: non-private fields, missing getter/setter</li>
 *   <li>Inheritance: is-a vs has-a misuse (ArrayList used for is-a, extends used for has-a)</li>
 *   <li>Polymorphism: {@code @Override} annotations, abstract method coverage</li>
 *   <li>Hard-coded value suspects: {@code return} statements containing numeric/string literals</li>
 * </ul>
 *
 * <p>Returns a {@link StaticAnalysisResult} populated with JavaParser findings only.
 * The Reflection-side fields are empty — call {@link ReflectionAnalyzer} separately,
 * then merge with {@link StaticAnalysisResult#merge}.</p>
 *
 * <p>All failures are caught and logged — never propagated.
 * A {@link StaticAnalysisResult#failure} is returned if analysis cannot proceed.</p>
 */
@Slf4j
@Component
public class JavaParserAnalyzer {

    // ─── PUBLIC API ──────────────────────────────────────────────────────────

    /**
     * Analyzes all {@code .java} files in the given source directory.
     *
     * @param srcDir directory containing the student's {@code .java} source files;
     *               may be {@code null} if the student did not submit source files
     * @return {@link StaticAnalysisResult} populated with AST-level findings;
     *         returns {@link StaticAnalysisResult#failure} if srcDir is null or unreadable
     */
    public StaticAnalysisResult analyze(Path srcDir) {
        // Guard: no source directory — skip analysis gracefully
        if (srcDir == null || !Files.exists(srcDir)) {
            log.warn("[PARSER] srcDir is null or does not exist — skipping JavaParser analysis");
            return StaticAnalysisResult.failure("No source directory available");
        }

        try {
            // Collect all .java files in the source directory (non-recursive for simplicity)
            List<Path> javaFiles = collectJavaFiles(srcDir);
            if (javaFiles.isEmpty()) {
                log.warn("[PARSER] No .java files found in {}", srcDir);
                return StaticAnalysisResult.failure("No .java files found in source directory");
            }

            log.debug("[PARSER] Analyzing {} .java file(s) in {}", javaFiles.size(), srcDir);

            // Aggregated results across all parsed files
            int classCount        = 0;
            int interfaceCount    = 0;
            int abstractClassCount = 0;
            int totalFields       = 0;
            int privateFieldCount = 0;
            int totalMethods      = 0;
            int overriddenCount   = 0;
            int abstractMethodCount = 0;

            List<String> classNames          = new ArrayList<>();
            List<String> interfaceNames      = new ArrayList<>();
            List<String> extendsRelations    = new ArrayList<>();
            List<String> implementsRelations = new ArrayList<>();
            List<String> encapsulationIssues = new ArrayList<>();
            List<String> inheritanceIssues   = new ArrayList<>();
            List<String> polymorphismIssues  = new ArrayList<>();
            List<String> hardCodedSuspects   = new ArrayList<>();

            JavaParser parser = new JavaParser();

            for (Path javaFile : javaFiles) {
                // Parse file — skip silently if parse fails (student may have syntax errors)
                ParseResult<CompilationUnit> parseResult = parser.parse(javaFile);
                if (!parseResult.isSuccessful() || parseResult.getResult().isEmpty()) {
                    log.warn("[PARSER] Failed to parse {}: {}",
                            javaFile.getFileName(),
                            parseResult.getProblems());
                    continue;
                }

                CompilationUnit cu = parseResult.getResult().get();

                // ── Step A: Analyse each top-level type declaration ──────────────
                for (TypeDeclaration<?> type : cu.getTypes()) {

                    String typeName = type.getNameAsString();

                    if (type instanceof ClassOrInterfaceDeclaration classDecl) {

                        if (classDecl.isInterface()) {
                            // Interface declaration
                            interfaceCount++;
                            interfaceNames.add(typeName);

                        } else if (classDecl.isAbstract()) {
                            // Abstract class declaration
                            abstractClassCount++;
                            classNames.add(typeName + " (abstract)");
                            checkInheritanceRelations(classDecl, typeName, extendsRelations, implementsRelations);

                        } else {
                            // Concrete class
                            classCount++;
                            classNames.add(typeName);
                            checkInheritanceRelations(classDecl, typeName, extendsRelations, implementsRelations);
                        }

                        // Check encapsulation for all class types
                        checkEncapsulation(classDecl, typeName, encapsulationIssues);

                        // Check inheritance misuse (is-a vs has-a)
                        checkInheritanceMisuse(classDecl, typeName, inheritanceIssues);

                        // Count fields and methods
                        totalFields       += classDecl.getFields().size();
                        privateFieldCount += countPrivateFields(classDecl);
                        totalMethods      += classDecl.getMethods().size();

                        // Count @Override methods and abstract methods
                        for (MethodDeclaration method : classDecl.getMethods()) {
                            if (hasOverrideAnnotation(method)) overriddenCount++;
                            if (method.isAbstract()) abstractMethodCount++;
                        }

                        // Check polymorphism issues
                        checkPolymorphism(classDecl, typeName, polymorphismIssues);

                        // Detect hard-coded return values
                        detectHardCodes(classDecl, typeName, hardCodedSuspects);
                    }
                    // Note: EnumDeclaration, AnnotationDeclaration are intentionally skipped —
                    // they are rarely relevant to OOP grading criteria.
                }
            }

            log.debug("[PARSER] Done. classes={}, interfaces={}, abstract={}, hardCodedSuspects={}",
                    classCount, interfaceCount, abstractClassCount, hardCodedSuspects.size());

            return new StaticAnalysisResult(
                    classCount, interfaceCount, abstractClassCount,
                    classNames, interfaceNames,
                    extendsRelations, implementsRelations,
                    encapsulationIssues, inheritanceIssues, polymorphismIssues,
                    hardCodedSuspects,
                    totalFields, privateFieldCount,
                    totalMethods, overriddenCount, abstractMethodCount,
                    abstractClassCount > 0,
                    interfaceCount > 0,
                    // Reflection fields are empty — filled by ReflectionAnalyzer
                    Collections.emptyList(), Collections.emptyList(), false,
                    null
            );

        } catch (Exception e) {
            log.error("[PARSER] Unexpected error during JavaParser analysis: {}", e.getMessage(), e);
            return StaticAnalysisResult.failure("JavaParser error: " + e.getMessage());
        }
    }

    // ─── PRIVATE HELPERS ─────────────────────────────────────────────────────

    /**
     * Collects all {@code .java} files recursively under {@code srcDir}.
     * Skips files that cannot be read (e.g., permission errors).
     */
    private List<Path> collectJavaFiles(Path srcDir) throws IOException {
        List<Path> result = new ArrayList<>();
        try (var walk = Files.walk(srcDir)) {
            walk.filter(p -> p.toString().endsWith(".java"))
                .sorted()
                .forEach(result::add);
        }
        return result;
    }

    /**
     * Records extends and implements relationships for a class or interface declaration.
     * Format: "ClassName extends SuperClass" or "ClassName implements IFace1, IFace2"
     */
    private void checkInheritanceRelations(ClassOrInterfaceDeclaration classDecl,
                                            String typeName,
                                            List<String> extendsRelations,
                                            List<String> implementsRelations) {
        // extends
        classDecl.getExtendedTypes().forEach(ext ->
                extendsRelations.add(typeName + " extends " + ext.getNameAsString()));

        // implements (may be multiple)
        if (!classDecl.getImplementedTypes().isEmpty()) {
            List<String> ifaces = classDecl.getImplementedTypes().stream()
                    .map(impl -> impl.getNameAsString())
                    .toList();
            implementsRelations.add(typeName + " implements " + String.join(", ", ifaces));
        }
    }

    /**
     * Checks encapsulation rules for a class:
     * <ul>
     *   <li>All non-static fields should be {@code private}.</li>
     *   <li>For each non-private field, a matching getter/setter pair should exist.</li>
     * </ul>
     */
    private void checkEncapsulation(ClassOrInterfaceDeclaration classDecl,
                                     String className,
                                     List<String> issues) {
        List<String> methodNames = classDecl.getMethods().stream()
                .map(m -> m.getNameAsString().toLowerCase())
                .toList();

        for (FieldDeclaration field : classDecl.getFields()) {
            // Static fields (constants) are exempt from encapsulation rules
            if (field.isStatic()) continue;

            for (VariableDeclarator var : field.getVariables()) {
                String fieldName = var.getNameAsString();

                if (!field.isPrivate()) {
                    // Non-private field is a direct encapsulation violation
                    String modifier = field.isPublic() ? "public"
                            : field.isProtected() ? "protected" : "package-private";
                    issues.add("Field '" + fieldName + "' in " + className
                            + " is " + modifier + " (should be private)");
                } else {
                    // Private field — check getter/setter exist
                    String getterName = "get" + capitalize(fieldName);
                    String setterName = "set" + capitalize(fieldName);
                    boolean hasGetter = methodNames.contains(getterName.toLowerCase())
                            || methodNames.contains("is" + capitalize(fieldName).toLowerCase());
                    boolean hasSetter = methodNames.contains(setterName.toLowerCase());

                    if (!hasGetter) {
                        issues.add("Private field '" + fieldName + "' in " + className
                                + " is missing getter (" + getterName + ")");
                    }
                    if (!hasSetter) {
                        issues.add("Private field '" + fieldName + "' in " + className
                                + " is missing setter (" + setterName + ")");
                    }
                }
            }
        }
    }

    /**
     * Detects inheritance misuse (is-a vs has-a confusion):
     * <ul>
     *   <li>Using an {@code extends} clause where the parent is a collection type
     *       (e.g., {@code class X extends ArrayList}) — has-a should use a field instead.</li>
     *   <li>Using a collection field with a name containing a class name that was also extended
     *       — this heuristic is lightweight and may produce false positives.</li>
     * </ul>
     */
    private void checkInheritanceMisuse(ClassOrInterfaceDeclaration classDecl,
                                         String className,
                                         List<String> issues) {
        // Heuristic 1: extending a collection type directly (is clearly wrong for has-a)
        Set<String> collectionTypes = Set.of(
                "ArrayList", "LinkedList", "HashSet", "TreeSet", "HashMap", "TreeMap",
                "Vector", "Stack", "ArrayDeque", "List", "Set", "Map", "Collection");

        classDecl.getExtendedTypes().forEach(ext -> {
            String parentName = ext.getNameAsString();
            if (collectionTypes.contains(parentName)) {
                issues.add("Class '" + className + "' extends " + parentName
                        + " — this is a has-a relationship misimplemented as is-a (extends). "
                        + "Use a field of type " + parentName + " instead.");
            }
        });

        // Heuristic 2: a field whose type matches any class declared in this compilation unit
        // but the class also 'extends' something else at the same time —
        // this is intentionally kept lightweight to avoid over-flagging.
        // More complex cross-file analysis is left to the AI reviewer.
    }

    /**
     * Checks polymorphism usage:
     * <ul>
     *   <li>Methods that override a parent method but lack {@code @Override} annotation.</li>
     *   <li>Concrete class extending abstract class — all abstract methods should be overridden.</li>
     * </ul>
     *
     * <p>Note: full override detection requires class hierarchy context across files,
     * which is not available in single-file AST analysis. We apply a lightweight heuristic:
     * any non-private, non-static method in a concrete class that has the same name as
     * a known interface method name pattern is flagged as a candidate.</p>
     */
    private void checkPolymorphism(ClassOrInterfaceDeclaration classDecl,
                                    String className,
                                    List<String> issues) {
        // Check: if class implements interface(s), all interface methods should have @Override
        if (!classDecl.getImplementedTypes().isEmpty() || !classDecl.getExtendedTypes().isEmpty()) {
            for (MethodDeclaration method : classDecl.getMethods()) {
                // Public non-static methods in a class that extends/implements something
                // are very likely override candidates — flag if no @Override annotation
                if (!method.isStatic()
                        && !method.isAbstract()
                        && method.isPublic()
                        && !hasOverrideAnnotation(method)) {
                    issues.add("Method '" + method.getNameAsString() + "()' in class '"
                            + className + "' may be overriding a supertype method "
                            + "but has no @Override annotation.");
                }
            }
        }
    }

    /**
     * Detects hard-coded value suspects: {@code return} statements that directly
     * return a numeric or string literal instead of a computation or field access.
     *
     * <p>These are candidates — the AI reviewer makes the final determination.</p>
     *
     * <h3>Examples of flagged patterns:</h3>
     * <ul>
     *   <li>{@code return 100;}</li>
     *   <li>{@code return "Student[S001, John, 3.5]";}</li>
     * </ul>
     *
     * <h3>NOT flagged (legitimate constants):</h3>
     * <ul>
     *   <li>{@code return "PASS";} — status label from spec is acceptable</li>
     *   <li>{@code return -1;} — sentinel values are generally legitimate</li>
     * </ul>
     */
    private void detectHardCodes(ClassOrInterfaceDeclaration classDecl,
                                  String className,
                                  List<String> suspects) {
        for (MethodDeclaration method : classDecl.getMethods()) {
            method.findAll(ReturnStmt.class).forEach(ret -> {
                ret.getExpression().ifPresent(expr -> {
                    if (isLiteralReturn(expr)) {
                        String literalValue = expr.toString();
                        suspects.add("In " + className + "." + method.getNameAsString()
                                + "(): return " + literalValue + ";");
                    }
                });
            });
        }
    }

    /**
     * Returns true if the expression is a "bare" literal return that is a hardcode suspect.
     * Filters out: boolean literals, null, -1 sentinel, empty string.
     */
    private boolean isLiteralReturn(Expression expr) {
        // Integer or long literal — but NOT -1 (common sentinel) and NOT 0
        if (expr instanceof IntegerLiteralExpr lit) {
            int val = Integer.parseInt(lit.getValue());
            return val != 0 && val != -1;
        }
        if (expr instanceof LongLiteralExpr lit) {
            String v = lit.getValue().replace("L", "").replace("l", "");
            long val = Long.parseLong(v);
            return val != 0L && val != -1L;
        }
        // Double/float literal — any non-zero value is suspicious
        if (expr instanceof DoubleLiteralExpr) return true;

        // String literal — flag only if it's more than a short keyword/status label
        if (expr instanceof StringLiteralExpr str) {
            String val = str.asString();
            // Skip empty string and very short labels (PASS, FAIL, etc.)
            return val.length() > 6;
        }

        // Unary minus wrapping a positive integer (e.g. return -5;)
        if (expr instanceof UnaryExpr unary
                && unary.getOperator() == UnaryExpr.Operator.MINUS
                && unary.getExpression() instanceof IntegerLiteralExpr innerLit) {
            int val = Integer.parseInt(innerLit.getValue());
            return val != 1; // -1 is legitimate sentinel, everything else is suspicious
        }

        return false;
    }

    /** Counts fields declared {@code private} (including final). */
    private int countPrivateFields(ClassOrInterfaceDeclaration classDecl) {
        return (int) classDecl.getFields().stream()
                .filter(FieldDeclaration::isPrivate)
                .count();
    }

    /** Returns true if the method has a {@code @Override} annotation. */
    private boolean hasOverrideAnnotation(MethodDeclaration method) {
        return method.getAnnotations().stream()
                .anyMatch(a -> a.getNameAsString().equals("Override"));
    }

    /** Capitalizes the first character of a string (for getter/setter name construction). */
    private String capitalize(String name) {
        if (name == null || name.isEmpty()) return name;
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}

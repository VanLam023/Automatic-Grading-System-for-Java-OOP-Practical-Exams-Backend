package agsfjope.backend.infrastructure.grading;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.reflect.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * Analyzes the compiled class structure of a student's JAR using Java Reflection.
 *
 * <h3>Purpose:</h3>
 * <p>While {@link JavaParserAnalyzer} operates on source text (which may not always be available
 * or fully compilable), this analyzer works on the actual compiled bytecode. It provides
 * ground-truth runtime verification of:</p>
 * <ul>
 *   <li>Actual class hierarchy (superclass, interfaces implemented at runtime)</li>
 *   <li>Method signatures (name, return type, parameter types, access modifiers)</li>
 *   <li>Field access modifiers (cross-check with static analysis)</li>
 * </ul>
 *
 * <h3>Safety guarantees:</h3>
 * <ul>
 *   <li>Uses an <strong>isolated {@link URLClassLoader}</strong> — closed immediately after analysis.</li>
 *   <li><strong>NEVER invokes any student code</strong> — only loads class metadata (no {@code static} initializers
 *       are triggered because we use {@code Class.forName(name, false, loader)}).</li>
 *   <li>Fails gracefully: any error returns {@link StaticAnalysisResult#failure}, never throws.</li>
 * </ul>
 *
 * <p>Returns a partial {@link StaticAnalysisResult} containing only the Reflection fields.
 * Merge with the JavaParser result using {@link StaticAnalysisResult#merge}.</p>
 */
@Slf4j
@Component
public class ReflectionAnalyzer {

    /**
     * Maximum number of classes to inspect from the student JAR.
     * Prevents DoS if student submits a JAR with thousands of classes.
     */
    private static final int MAX_CLASSES = 50;

    // ─── PUBLIC API ──────────────────────────────────────────────────────────

    /**
     * Inspects the compiled class structure of the student JAR using Java Reflection.
     *
     * @param studentJar path to the student's compiled {@code .jar} file;
     *                   may be {@code null} if the student did not submit a JAR
     * @return partial {@link StaticAnalysisResult} with only reflection fields populated;
     *         returns {@link StaticAnalysisResult#failure} if JAR is null or unreadable
     */
    public StaticAnalysisResult analyze(Path studentJar) {
        // Guard: no JAR available — reflection not possible
        if (studentJar == null || !Files.exists(studentJar)) {
            log.warn("[REFLECT] studentJar is null or does not exist — skipping Reflection analysis");
            return buildEmptyReflectionResult("No student JAR available");
        }

        log.debug("[REFLECT] Starting Reflection analysis on JAR: {}", studentJar.getFileName());

        List<String> classStructure = new ArrayList<>();
        List<String> reflectionIssues = new ArrayList<>();

        // Isolated URLClassLoader — scoped to this try block only
        // Using try-with-resources ensures the classloader is closed even on exception
        try (URLClassLoader loader = new URLClassLoader(
                new URL[]{studentJar.toUri().toURL()},
                // Parent: ONLY the bootstrap ClassLoader — prevents student code from
                // accessing application beans or Spring context
                null
        )) {
            List<String> classNames = listClassNamesInJar(studentJar);

            if (classNames.isEmpty()) {
                log.warn("[REFLECT] No classes found in JAR: {}", studentJar.getFileName());
                return buildEmptyReflectionResult("No classes found in student JAR");
            }

            log.debug("[REFLECT] Found {} class(es) in JAR (capped at {})", classNames.size(), MAX_CLASSES);

            int inspected = 0;
            for (String className : classNames) {
                if (inspected >= MAX_CLASSES) {
                    classStructure.add("(inspection capped at " + MAX_CLASSES + " classes)");
                    break;
                }
                inspected++;

                try {
                    // false = do NOT initialize (no static initializers run — SAFE)
                    Class<?> clazz = Class.forName(className, false, loader);
                    classStructure.add(buildClassSummary(clazz));
                    checkFieldModifiers(clazz, reflectionIssues);

                } catch (ClassNotFoundException e) {
                    log.debug("[REFLECT] Could not load class {}: {}", className, e.getMessage());
                    reflectionIssues.add("Could not load class: " + className);
                } catch (Exception e) {
                    // LinkageError, NoClassDefFoundError, etc. — class might depend on
                    // missing student classes; skip and continue
                    log.debug("[REFLECT] Error inspecting class {}: {}", className, e.getMessage());
                    reflectionIssues.add("Error inspecting: " + className + " — " + e.getMessage());
                }
            }

        } catch (IOException e) {
            log.error("[REFLECT] Cannot open student JAR {}: {}", studentJar.getFileName(), e.getMessage());
            return buildEmptyReflectionResult("Cannot open student JAR: " + e.getMessage());
        } catch (Exception e) {
            log.error("[REFLECT] Unexpected error during Reflection analysis: {}", e.getMessage(), e);
            return buildEmptyReflectionResult("Reflection error: " + e.getMessage());
        }

        log.debug("[REFLECT] Done. structureEntries={}, issues={}",
                classStructure.size(), reflectionIssues.size());

        // Return partial result — AST fields are empty (filled by JavaParserAnalyzer)
        return new StaticAnalysisResult(
                0, 0, 0,
                Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(),
                0, 0, 0, 0, 0,
                false, false,
                classStructure, reflectionIssues, true,
                null
        );
    }

    // ─── PRIVATE HELPERS ─────────────────────────────────────────────────────

    /**
     * Lists all class names contained in the student JAR by reading its manifest entries.
     * Does NOT load any class — purely reads the JAR file index.
     *
     * @param jarPath path to the JAR file
     * @return list of fully qualified class names (dot-separated) found in the JAR
     * @throws IOException if the JAR cannot be read
     */
    private List<String> listClassNamesInJar(Path jarPath) throws IOException {
        List<String> names = new ArrayList<>();
        try (JarInputStream jis = new JarInputStream(Files.newInputStream(jarPath))) {
            JarEntry entry;
            while ((entry = jis.getNextJarEntry()) != null) {
                String entryName = entry.getName();

                // Only process .class files; skip inner classes ($) to avoid duplication
                if (entryName.endsWith(".class") && !entryName.contains("$")) {
                    // Convert path to fully qualified class name: "a/b/Foo.class" → "a.b.Foo"
                    String className = entryName
                            .replace('/', '.')
                            .replace(".class", "");
                    names.add(className);
                }
            }
        }
        return names;
    }

    /**
     * Builds a human-readable one-line summary of a class for inclusion in the AI prompt.
     *
     * <p>Format: {@code [Modifier] ClassName [extends SuperClass] [implements IFace1, IFace2] — N method(s), M field(s)}</p>
     */
    private String buildClassSummary(Class<?> clazz) {
        StringBuilder sb = new StringBuilder();

        // Access modifier
        int mod = clazz.getModifiers();
        if (Modifier.isPublic(mod))    sb.append("public ");
        if (Modifier.isAbstract(mod) && !clazz.isInterface()) sb.append("abstract ");
        if (Modifier.isFinal(mod))     sb.append("final ");

        // Kind: interface or class
        if (clazz.isInterface()) sb.append("interface ");
        else                     sb.append("class ");

        sb.append(clazz.getSimpleName());

        // Superclass (skip Object — not informative)
        Class<?> superclass = clazz.getSuperclass();
        if (superclass != null && !superclass.equals(Object.class)) {
            sb.append(" extends ").append(superclass.getSimpleName());
        }

        // Implemented interfaces
        Class<?>[] ifaces = clazz.getInterfaces();
        if (ifaces.length > 0) {
            sb.append(" implements ");
            StringJoiner sj = new StringJoiner(", ");
            for (Class<?> iface : ifaces) sj.add(iface.getSimpleName());
            sb.append(sj);
        }

        // Method and field counts
        int methodCount = clazz.getDeclaredMethods().length;
        int fieldCount  = clazz.getDeclaredFields().length;
        sb.append(" — ").append(methodCount).append(" method(s), ")
          .append(fieldCount).append(" field(s)");

        return sb.toString();
    }

    /**
     * Cross-checks field access modifiers detected at runtime via Reflection.
     * Flags any non-private, non-static fields as encapsulation violations.
     *
     * <p>This runtime check supplements the static JavaParser check — catching cases
     * where the student submitted a pre-compiled JAR without matching source code.</p>
     *
     * @param clazz  the class to inspect
     * @param issues list to append violations to
     */
    private void checkFieldModifiers(Class<?> clazz, List<String> issues) {
        for (Field field : clazz.getDeclaredFields()) {
            int mod = field.getModifiers();
            // Skip: static fields (constants), synthetic fields (compiler-generated)
            if (Modifier.isStatic(mod) || field.isSynthetic()) continue;

            if (!Modifier.isPrivate(mod)) {
                String visibility = Modifier.isPublic(mod) ? "public"
                        : Modifier.isProtected(mod) ? "protected"
                        : "package-private";
                issues.add("[Runtime] Field '" + field.getName() + "' in class '"
                        + clazz.getSimpleName() + "' is " + visibility
                        + " (encapsulation violation — confirmed by Reflection)");
            }
        }
    }

    /**
     * Convenience: builds a {@link StaticAnalysisResult} where only the failure/empty state
     * is set for the reflection-side fields. All AST fields remain 0/empty.
     */
    private StaticAnalysisResult buildEmptyReflectionResult(String reason) {
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
}

package agsfjope.backend.infrastructure.grading.criteria;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;

import java.util.*;

/**
 * Lightweight wrapper around parsed Java source ASTs for criterion checking.
 *
 * <p>Pre-indexes all class/interface declarations by name so each
 * {@link CriterionHandler} can do a simple O(1) lookup instead of
 * re-parsing the source files on every criterion check.</p>
 *
 * <p>Built once per answer by {@code DeterministicOopScorer} and passed
 * to every handler. Immutable after construction.</p>
 */
public class SourceCodeContext {

    /** All parsed compilation units from the student's .java files. */
    private final List<CompilationUnit> compilationUnits;

    /** Class declarations indexed by simple name (case-sensitive). */
    private final Map<String, ClassOrInterfaceDeclaration> classByName;

    /** Interface declarations indexed by simple name (case-sensitive). */
    private final Map<String, ClassOrInterfaceDeclaration> interfaceByName;

    /** Whether the student's .jar was successfully loaded (for Reflection fallback). */
    private final boolean jarAvailable;

    /** Loaded class objects from .jar, indexed by simple name. Available when jarAvailable=true. */
    private final Map<String, Class<?>> loadedClasses;

    public SourceCodeContext(List<CompilationUnit> compilationUnits,
                             boolean jarAvailable,
                             Map<String, Class<?>> loadedClasses) {
        this.compilationUnits = List.copyOf(compilationUnits);
        this.jarAvailable = jarAvailable;
        this.loadedClasses = Collections.unmodifiableMap(loadedClasses != null ? loadedClasses : Map.of());

        // Build indexes
        Map<String, ClassOrInterfaceDeclaration> classIdx = new LinkedHashMap<>();
        Map<String, ClassOrInterfaceDeclaration> ifaceIdx  = new LinkedHashMap<>();

        for (CompilationUnit cu : compilationUnits) {
            for (TypeDeclaration<?> type : cu.getTypes()) {
                if (type instanceof ClassOrInterfaceDeclaration coid) {
                    if (coid.isInterface()) {
                        ifaceIdx.put(coid.getNameAsString(), coid);
                    } else {
                        classIdx.put(coid.getNameAsString(), coid);
                    }
                }
            }
        }

        this.classByName     = Collections.unmodifiableMap(classIdx);
        this.interfaceByName = Collections.unmodifiableMap(ifaceIdx);
    }

    // ─── Lookups ──────────────────────────────────────────────────────────────

    /** Returns true if a concrete class with the given name was parsed. */
    public boolean hasClass(String name) {
        return classByName.containsKey(name);
    }

    /** Returns true if an interface with the given name was parsed. */
    public boolean hasInterface(String name) {
        return interfaceByName.containsKey(name);
    }

    /** Returns the class declaration, or empty if not found. */
    public Optional<ClassOrInterfaceDeclaration> findClass(String name) {
        return Optional.ofNullable(classByName.get(name));
    }

    /** Returns the interface declaration, or empty if not found. */
    public Optional<ClassOrInterfaceDeclaration> findInterface(String name) {
        return Optional.ofNullable(interfaceByName.get(name));
    }

    /**
     * Finds a field in the given class by name.
     * Searches all {@link VariableDeclarator}s within all {@link FieldDeclaration}s.
     */
    public Optional<FieldDeclaration> findField(String className, String fieldName) {
        return findClass(className)
                .flatMap(cls -> cls.getFields().stream()
                        .filter(f -> f.getVariables().stream()
                                .anyMatch(v -> v.getNameAsString().equals(fieldName)))
                        .findFirst());
    }

    /**
     * Finds a method in the given class by name and parameter count.
     * Returns the first matching method; does not validate parameter types.
     */
    public Optional<MethodDeclaration> findMethod(String className, String methodName) {
        return findClass(className)
                .flatMap(cls -> cls.getMethods().stream()
                        .filter(m -> m.getNameAsString().equals(methodName))
                        .findFirst());
    }

    /** Returns the loaded Class<?> from the student JAR. Empty if jar unavailable. */
    public Optional<Class<?>> loadedClass(String simpleName) {
        return Optional.ofNullable(loadedClasses.get(simpleName));
    }

    public boolean isJarAvailable() { return jarAvailable; }

    public List<CompilationUnit> getCompilationUnits() { return compilationUnits; }

    /** All concrete class names parsed from source. */
    public Set<String> classNames() { return classByName.keySet(); }

    /** All interface names parsed from source. */
    public Set<String> interfaceNames() { return interfaceByName.keySet(); }
}

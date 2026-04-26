package agsfjope.backend.infrastructure.grading;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Isolated analyzer for MODE_5 deterministic structure checks.
 * Completely separate from the AI pipeline.
 */
@Slf4j
@Service
public class IsolatedStructureAnalyzer {

    public record CheckResult(int passedCount, int totalCount, List<String> details) {
        public BigDecimal calculateScore(BigDecimal maxScore) {
            if (totalCount == 0) return BigDecimal.ZERO;
            return BigDecimal.valueOf(passedCount)
                    .divide(BigDecimal.valueOf(totalCount), 4, RoundingMode.HALF_UP)
                    .multiply(maxScore)
                    .setScale(2, RoundingMode.HALF_UP);
        }
    }

    public CheckResult analyze(Path srcDir, Path jarPath) {
        List<String> details = new ArrayList<>();
        int passed = 0;
        int total = 0;

        try {
            // 1. Static Analysis (JavaParser)
            List<Path> javaFiles = findJavaFiles(srcDir);
            for (Path p : javaFiles) {
                CompilationUnit cu = StaticJavaParser.parse(p);
                List<ClassOrInterfaceDeclaration> classes = cu.findAll(ClassOrInterfaceDeclaration.class);

                for (ClassOrInterfaceDeclaration clazz : classes) {
                    // Check Encapsulation: fields should be private
                    for (FieldDeclaration field : clazz.getFields()) {
                        total++;
                        if (field.isPrivate()) {
                            passed++;
                        } else {
                            details.add("Violation: Field '" + field.getVariable(0).getNameAsString() + 
                                       "' in class " + clazz.getNameAsString() + " is not private.");
                        }
                    }
                }
            }

            // 2. Runtime Analysis (Reflection)
            if (jarPath != null && Files.exists(jarPath)) {
                try (URLClassLoader loader = new URLClassLoader(new URL[]{jarPath.toUri().toURL()}, 
                        this.getClass().getClassLoader())) {
                    
                    for (Path p : javaFiles) {
                        String className = getFullClassName(srcDir, p);
                        try {
                            Class<?> clazz = loader.loadClass(className);
                            
                            // Check Inheritance/Interface implementation if applicable
                            // (This is a generic placeholder - in a real exam we'd check against a schema)
                            total++;
                            if (clazz.getSuperclass() != Object.class || clazz.getInterfaces().length > 0) {
                                passed++;
                            } else {
                                details.add("Note: Class " + className + " does not use inheritance/interfaces.");
                            }

                        } catch (ClassNotFoundException e) {
                            log.warn("Could not load class for reflection: {}", className);
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.error("IsolatedStructureAnalyzer error: {}", e.getMessage());
            details.add("Analyzer error: " + e.getMessage());
        }

        return new CheckResult(passed, total, details);
    }

    private List<Path> findJavaFiles(Path dir) throws Exception {
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    private String getFullClassName(Path srcDir, Path javaFile) {
        String relative = srcDir.relativize(javaFile).toString();
        return relative.replace(File.separator, ".")
                       .replace(".java", "");
    }
}

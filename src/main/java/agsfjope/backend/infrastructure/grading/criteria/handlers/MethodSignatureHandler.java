package agsfjope.backend.infrastructure.grading.criteria.handlers;

import agsfjope.backend.core.entities.Answer;
import agsfjope.backend.core.entities.CriteriaResult;
import agsfjope.backend.core.entities.GradingCriteria;
import agsfjope.backend.infrastructure.grading.criteria.AbstractCriterionHandler;
import agsfjope.backend.infrastructure.grading.criteria.SourceCodeContext;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class MethodSignatureHandler extends AbstractCriterionHandler {

    @Override
    public CriteriaResult check(SourceCodeContext context, GradingCriteria criteria, Answer answer) {
        Map<String, Object> params  = criteria.getCheckParams();
        String className            = paramRequired(params, "className", criteria.getCriteriaCode());
        String methodName           = paramRequired(params, "methodName", criteria.getCriteriaCode());
        String expectedReturn       = param(params, "returnType");
        String accessModifier       = param(params, "accessModifier");
        List<String> expectedParams = paramList(params, "paramTypes");
        boolean requireOverride     = paramBool(params, "requireOverride", false);

        if (context.isJarAvailable()) {
            return checkViaReflection(context, answer, criteria, className, methodName,
                    expectedReturn, expectedParams, accessModifier, requireOverride);
        }
        return checkViaParser(context, answer, criteria, className, methodName,
                expectedReturn, expectedParams, accessModifier, requireOverride);
    }

    private CriteriaResult checkViaReflection(SourceCodeContext context, Answer answer,
                                               GradingCriteria criteria, String className,
                                               String methodName, String expectedReturn,
                                               List<String> expectedParams, String accessModifier,
                                               boolean requireOverride) {
        Optional<Class<?>> clsOpt = context.loadedClass(className);
        if (clsOpt.isEmpty()) {
            // Fallback to parser — also handles @Override check (annotation not in bytecode)
            return checkViaParser(context, answer, criteria, className, methodName,
                    expectedReturn, expectedParams, accessModifier, requireOverride);
        }

        Class<?> cls = clsOpt.get();
        for (java.lang.reflect.Method method : cls.getDeclaredMethods()) {
            if (!method.getName().equals(methodName)) continue;

            if (!expectedParams.isEmpty()) {
                Class<?>[] actual = method.getParameterTypes();
                if (!paramTypesMatch(actual, expectedParams)) continue;
            }

            if (expectedReturn != null) {
                String actualReturn = method.getReturnType().getSimpleName();
                if (!normalizeType(actualReturn).equalsIgnoreCase(normalizeType(expectedReturn))) {
                    return fail(answer, criteria,
                            "Phương thức " + methodName + " có kiểu trả về chưa đúng. "
                            + "Yêu cầu: " + expectedReturn + ". Hiện tại: " + actualReturn + ".");
                }
            }

            if (accessModifier != null && !accessModifier.isBlank()) {
                int mods = method.getModifiers();
                if (!matchesModifier(mods, accessModifier)) {
                    String current = java.lang.reflect.Modifier.isPublic(mods) ? "public"
                            : java.lang.reflect.Modifier.isProtected(mods) ? "protected"
                            : java.lang.reflect.Modifier.isPrivate(mods) ? "private" : "package-private";
                    return fail(answer, criteria,
                            "Phương thức " + methodName + " có phạm vi truy cập chưa đúng. "
                            + "Yêu cầu: " + accessModifier + ". Hiện tại: " + current + ".");
                }
            }

            // @Override is a SOURCE-retention annotation — it is NOT present in bytecode.
            // We delegate the @Override check to the parser path which reads the .java AST.
            if (requireOverride) {
                return checkViaParser(context, answer, criteria, className, methodName,
                        expectedReturn, expectedParams, accessModifier, requireOverride);
            }

            return pass(answer, criteria, "Phương thức " + methodName + " được khai báo đúng.");
        }

        return fail(answer, criteria,
                "Không tìm thấy phương thức " + methodName + " trong class " + className + ".");
    }

    private boolean matchesModifier(int mods, String expected) {
        return switch (expected.toLowerCase().trim()) {
            case "public"    -> java.lang.reflect.Modifier.isPublic(mods);
            case "protected" -> java.lang.reflect.Modifier.isProtected(mods);
            case "private"   -> java.lang.reflect.Modifier.isPrivate(mods);
            default          -> true;
        };
    }

    private boolean paramTypesMatch(Class<?>[] actual, List<String> expected) {
        if (actual.length != expected.size()) return false;
        for (int i = 0; i < actual.length; i++) {
            if (!normalizeType(actual[i].getSimpleName()).equalsIgnoreCase(normalizeType(expected.get(i)))) {
                return false;
            }
        }
        return true;
    }

    private CriteriaResult checkViaParser(SourceCodeContext context, Answer answer,
                                          GradingCriteria criteria, String className,
                                          String methodName, String expectedReturn,
                                          List<String> expectedParams, String accessModifier,
                                          boolean requireOverride) {
        Optional<ClassOrInterfaceDeclaration> clsOpt = context.findClass(className);
        if (clsOpt.isEmpty()) {
            return fail(answer, criteria, "Không tìm thấy class " + className + ".");
        }

        ClassOrInterfaceDeclaration cls = clsOpt.get();

        List<MethodDeclaration> sameNameMethods = cls.getMethods().stream()
                .filter(m -> m.getNameAsString().equals(methodName))
                .toList();

        String lastMismatchReason = null;

        for (MethodDeclaration method : sameNameMethods) {

            if (!expectedParams.isEmpty()) {
                List<Parameter> actualParams = method.getParameters();
                if (actualParams.size() != expectedParams.size()) {
                    lastMismatchReason = "Phương thức " + methodName + " có số lượng tham số chưa đúng.";
                    continue;
                }
                boolean paramsOk = true;
                for (int i = 0; i < actualParams.size(); i++) {
                    if (!normalizeType(actualParams.get(i).getTypeAsString())
                            .equalsIgnoreCase(normalizeType(expectedParams.get(i)))) {
                        paramsOk = false;
                        break;
                    }
                }
                if (!paramsOk) {
                    lastMismatchReason = "Phương thức " + methodName + " có kiểu tham số chưa đúng.";
                    continue;
                }
            }

            if (expectedReturn != null) {
                String actualReturn = method.getTypeAsString();
                if (!normalizeType(actualReturn).equalsIgnoreCase(normalizeType(expectedReturn))) {
                    lastMismatchReason = "Phương thức " + methodName + " có kiểu trả về chưa đúng. "
                        + "Yêu cầu: " + expectedReturn + ". Hiện tại: " + actualReturn + ".";
                    continue;
                }
            }

            if (accessModifier != null && !accessModifier.isBlank()) {
                boolean modOk = method.getModifiers().stream()
                        .anyMatch(m -> m.getKeyword().asString().equalsIgnoreCase(accessModifier.trim()));
                if (!modOk) {
                    String current = method.getModifiers().stream()
                            .map(m -> m.getKeyword().asString())
                            .collect(Collectors.joining(", "));
                    lastMismatchReason = "Phương thức " + methodName + " có phạm vi truy cập chưa đúng. "
                        + "Yêu cầu: " + accessModifier + ". Hiện tại: " + (current.isBlank() ? "package-private" : current) + ".";
                    continue;
                }
            }

            // Check @Override annotation via AST (SOURCE-retention — only visible in .java, not bytecode)
            if (requireOverride) {
                boolean hasOverride = method.getAnnotations().stream()
                        .anyMatch(a -> a.getNameAsString().equals("Override"));
                if (!hasOverride) {
                    lastMismatchReason = "Phương thức " + methodName + " thiếu annotation @Override.";
                    continue;
                }
            }

            return pass(answer, criteria, "Phương thức " + methodName + " được khai báo đúng.");
        }

            if (!sameNameMethods.isEmpty() && lastMismatchReason != null) {
                return fail(answer, criteria, lastMismatchReason);
            }

        String found = cls.getMethods().stream()
                .map(m -> m.getNameAsString() + "(" + m.getParameters().stream()
                        .map(Parameter::getTypeAsString).collect(Collectors.joining(", ")) + ")")
                .collect(Collectors.joining(", "));

        return fail(answer, criteria,
                "Không tìm thấy phương thức " + methodName + " trong class " + className + ". "
                + (found.isBlank() ? "Không có phương thức nào." : "Các phương thức hiện có: " + found + "."));
    }

    private String normalizeType(String type) {
        return type.replaceAll("<.*>", "").replace("[]", "").trim();
    }
}

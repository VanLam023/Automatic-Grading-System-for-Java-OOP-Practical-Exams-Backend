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

        if (context.isJarAvailable()) {
            return checkViaReflection(context, answer, criteria, className, methodName, expectedReturn, expectedParams, accessModifier);
        }
        return checkViaParser(context, answer, criteria, className, methodName, expectedReturn, expectedParams, accessModifier);
    }

    private CriteriaResult checkViaReflection(SourceCodeContext context, Answer answer,
                                               GradingCriteria criteria, String className,
                                               String methodName, String expectedReturn,
                                               List<String> expectedParams, String accessModifier) {
        Optional<Class<?>> clsOpt = context.loadedClass(className);
        if (clsOpt.isEmpty()) {
            return checkViaParser(context, answer, criteria, className, methodName, expectedReturn, expectedParams, accessModifier);
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
                                          List<String> expectedParams, String accessModifier) {
        Optional<ClassOrInterfaceDeclaration> clsOpt = context.findClass(className);
        if (clsOpt.isEmpty()) {
            return fail(answer, criteria, "Không tìm thấy class " + className + ".");
        }

        ClassOrInterfaceDeclaration cls = clsOpt.get();

        for (MethodDeclaration method : cls.getMethods()) {
            if (!method.getNameAsString().equals(methodName)) continue;

            if (!expectedParams.isEmpty()) {
                List<Parameter> actualParams = method.getParameters();
                if (actualParams.size() != expectedParams.size()) continue;
                boolean paramsOk = true;
                for (int i = 0; i < actualParams.size(); i++) {
                    if (!normalizeType(actualParams.get(i).getTypeAsString())
                            .equalsIgnoreCase(normalizeType(expectedParams.get(i)))) {
                        paramsOk = false;
                        break;
                    }
                }
                if (!paramsOk) continue;
            }

            if (expectedReturn != null) {
                String actualReturn = method.getTypeAsString();
                if (!normalizeType(actualReturn).equalsIgnoreCase(normalizeType(expectedReturn))) {
                    return fail(answer, criteria,
                            "Phương thức " + methodName + " có kiểu trả về chưa đúng. "
                            + "Yêu cầu: " + expectedReturn + ". Hiện tại: " + actualReturn + ".");
                }
            }

            if (accessModifier != null && !accessModifier.isBlank()) {
                boolean modOk = method.getModifiers().stream()
                        .anyMatch(m -> m.getKeyword().asString().equalsIgnoreCase(accessModifier.trim()));
                if (!modOk) {
                    String current = method.getModifiers().stream()
                            .map(m -> m.getKeyword().asString())
                            .collect(Collectors.joining(", "));
                    return fail(answer, criteria,
                            "Phương thức " + methodName + " có phạm vi truy cập chưa đúng. "
                            + "Yêu cầu: " + accessModifier + ". Hiện tại: " + (current.isBlank() ? "package-private" : current) + ".");
                }
            }

            return pass(answer, criteria, "Phương thức " + methodName + " được khai báo đúng.");
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

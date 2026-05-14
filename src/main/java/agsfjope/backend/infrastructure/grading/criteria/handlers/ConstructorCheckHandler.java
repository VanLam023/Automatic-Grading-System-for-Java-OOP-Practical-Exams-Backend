package agsfjope.backend.infrastructure.grading.criteria.handlers;

import agsfjope.backend.core.entities.Answer;
import agsfjope.backend.core.entities.CriteriaResult;
import agsfjope.backend.core.entities.GradingCriteria;
import agsfjope.backend.infrastructure.grading.criteria.AbstractCriterionHandler;
import agsfjope.backend.infrastructure.grading.criteria.SourceCodeContext;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.Parameter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class ConstructorCheckHandler extends AbstractCriterionHandler {

    @Override
    public CriteriaResult check(SourceCodeContext context, GradingCriteria criteria, Answer answer) {
        Map<String, Object> params  = criteria.getCheckParams();
        String className            = paramRequired(params, "className", criteria.getCriteriaCode());
        List<String> expectedParams = paramList(params, "paramTypes");

        if (context.isJarAvailable()) {
            return checkViaReflection(context, answer, criteria, className, expectedParams);
        }
        return checkViaParser(context, answer, criteria, className, expectedParams);
    }

    private CriteriaResult checkViaReflection(SourceCodeContext context, Answer answer,
                                               GradingCriteria criteria, String className,
                                               List<String> expectedParams) {
        Optional<Class<?>> clsOpt = context.loadedClass(className);
        if (clsOpt.isEmpty()) {
            return checkViaParser(context, answer, criteria, className, expectedParams);
        }

        Class<?> cls = clsOpt.get();
        String sig = className + "(" + String.join(", ", expectedParams) + ")";
        for (java.lang.reflect.Constructor<?> ctor : cls.getDeclaredConstructors()) {
            if (paramTypesMatch(ctor.getParameterTypes(), expectedParams)) {
                return pass(answer, criteria, "Đã tìm thấy constructor " + sig + " trong class " + className + ".");
            }
        }
        return fail(answer, criteria, "Không tìm thấy constructor " + sig + " trong class " + className + ".");
    }

    private boolean paramTypesMatch(Class<?>[] actual, List<String> expected) {
        if (actual.length != expected.size()) return false;
        for (int i = 0; i < actual.length; i++) {
            if (!normalizeTypeName(actual[i].getSimpleName()).equalsIgnoreCase(normalizeTypeName(expected.get(i)))) {
                return false;
            }
        }
        return true;
    }

    private CriteriaResult checkViaParser(SourceCodeContext context, Answer answer,
                                          GradingCriteria criteria, String className,
                                          List<String> expectedParams) {
        Optional<ClassOrInterfaceDeclaration> clsOpt = context.findClass(className);
        if (clsOpt.isEmpty()) {
            return fail(answer, criteria, "Không tìm thấy class " + className + ".");
        }

        ClassOrInterfaceDeclaration cls = clsOpt.get();
        String sig = className + "(" + String.join(", ", expectedParams) + ")";

        for (ConstructorDeclaration ctor : cls.getConstructors()) {
            if (constructorParamsMatch(ctor, expectedParams)) {
                return pass(answer, criteria, "Đã tìm thấy constructor " + sig + " trong class " + className + ".");
            }
        }

        String found = cls.getConstructors().stream()
                .map(c -> className + "(" + c.getParameters().stream()
                        .map(p -> p.getTypeAsString())
                        .collect(Collectors.joining(", ")) + ")")
                .collect(Collectors.joining(", "));

        return fail(answer, criteria,
                "Không tìm thấy constructor " + sig + " trong class " + className + ". "
                + (found.isBlank()
                        ? "Chưa có constructor nào được khai báo."
                        : "Các constructor hiện có: " + found + "."));
    }

    private boolean constructorParamsMatch(ConstructorDeclaration ctor, List<String> expected) {
        List<Parameter> params = ctor.getParameters();
        if (params.size() != expected.size()) return false;
        for (int i = 0; i < params.size(); i++) {
            if (!normalizeTypeName(params.get(i).getTypeAsString()).equalsIgnoreCase(normalizeTypeName(expected.get(i)))) return false;
        }
        return true;
    }

    private String normalizeTypeName(String type) {
        return type.replaceAll("<.*>", "").replace("[]", "").trim();
    }
}

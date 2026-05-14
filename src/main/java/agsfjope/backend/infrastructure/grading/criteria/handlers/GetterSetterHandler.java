package agsfjope.backend.infrastructure.grading.criteria.handlers;

import agsfjope.backend.core.entities.Answer;
import agsfjope.backend.core.entities.CriteriaResult;
import agsfjope.backend.core.entities.GradingCriteria;
import agsfjope.backend.infrastructure.grading.criteria.AbstractCriterionHandler;
import agsfjope.backend.infrastructure.grading.criteria.SourceCodeContext;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.Map;

@Component
public class GetterSetterHandler extends AbstractCriterionHandler {

    @Override
    public CriteriaResult check(SourceCodeContext context, GradingCriteria criteria, Answer answer) {
        Map<String, Object> params = criteria.getCheckParams();
        String className      = paramRequired(params, "className", criteria.getCriteriaCode());
        String fieldName      = paramRequired(params, "fieldName", criteria.getCriteriaCode());
        String accessModifier = param(params, "accessModifier");

        Optional<ClassOrInterfaceDeclaration> clsOpt = context.findClass(className);
        if (clsOpt.isEmpty()) {
            return fail(answer, criteria,
                    "Không tìm thấy class " + className + " nên không thể kiểm tra getter/setter.");
        }

        ClassOrInterfaceDeclaration cls = clsOpt.get();
        String capitalized  = capitalize(fieldName);
        String getterName   = "get" + capitalized;
        String isGetterName = "is" + capitalized;
        String setterName   = "set" + capitalized;

        Optional<MethodDeclaration> getterOpt = cls.getMethods().stream()
                .filter(m -> m.getNameAsString().equalsIgnoreCase(getterName)
                          || m.getNameAsString().equalsIgnoreCase(isGetterName))
                .findFirst();
        Optional<MethodDeclaration> setterOpt = cls.getMethods().stream()
                .filter(m -> m.getNameAsString().equalsIgnoreCase(setterName))
                .findFirst();

        boolean hasGetter = getterOpt.isPresent();
        boolean hasSetter = setterOpt.isPresent();

        if (accessModifier != null && !accessModifier.isBlank()) {
            String mod = accessModifier.toLowerCase().trim();
            if (hasGetter && !hasModifier(getterOpt.get(), mod)) hasGetter = false;
            if (hasSetter && !hasModifier(setterOpt.get(), mod)) hasSetter = false;
        }

        BigDecimal half = criteria.getMaxScore()
                .divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);

        if (hasGetter && hasSetter) {
            return pass(answer, criteria,
                    "Đã khai báo đầy đủ getter (" + getterName + ") và setter (" + setterName
                    + ") cho thuộc tính " + fieldName + ".");
        }
        if (hasGetter) {
            return partial(answer, criteria, half,
                    "Thiếu setter (" + setterName + ") cho thuộc tính " + fieldName
                    + ". Đã tìm thấy: " + getterName + ".");
        }
        if (hasSetter) {
            return partial(answer, criteria, half,
                    "Thiếu getter (" + getterName + ") cho thuộc tính " + fieldName
                    + ". Đã tìm thấy: " + setterName + ".");
        }
        return fail(answer, criteria,
                "Chưa tìm thấy getter/setter cho các thuộc tính yêu cầu. "
                + "Không tìm thấy class " + className + " nên không thể kiểm tra getter/setter.");
    }

    private boolean hasModifier(MethodDeclaration method, String modifier) {
        return method.getModifiers().stream()
                .anyMatch(m -> m.getKeyword().asString().equalsIgnoreCase(modifier));
    }

    private String capitalize(String name) {
        if (name == null || name.isEmpty()) return name;
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}

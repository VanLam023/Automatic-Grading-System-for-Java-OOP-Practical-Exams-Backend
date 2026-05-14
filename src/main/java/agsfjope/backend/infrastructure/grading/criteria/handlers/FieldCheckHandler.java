package agsfjope.backend.infrastructure.grading.criteria.handlers;

import agsfjope.backend.core.entities.Answer;
import agsfjope.backend.core.entities.CriteriaResult;
import agsfjope.backend.core.entities.GradingCriteria;
import agsfjope.backend.infrastructure.grading.criteria.AbstractCriterionHandler;
import agsfjope.backend.infrastructure.grading.criteria.SourceCodeContext;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class FieldCheckHandler extends AbstractCriterionHandler {

    @Override
    public CriteriaResult check(SourceCodeContext context, GradingCriteria criteria, Answer answer) {
        Map<String, Object> params = criteria.getCheckParams();
        String className = paramRequired(params, "className", criteria.getCriteriaCode());

        Optional<ClassOrInterfaceDeclaration> classOpt = context.findClass(className);
        if (classOpt.isEmpty()) {
            return fail(answer, criteria, "Không tìm thấy class " + className + ", không thể kiểm tra thuộc tính.");
        }

        ClassOrInterfaceDeclaration cls = classOpt.get();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) params.get("fields");
        if (fields != null && !fields.isEmpty()) {
            return checkMultipleFields(answer, criteria, cls, fields);
        }

        String fieldName      = paramRequired(params, "fieldName", criteria.getCriteriaCode());
        String fieldType      = param(params, "fieldType");
        String accessModifier = param(params, "accessModifier");
        return checkSingleField(answer, criteria, cls, fieldName, fieldType, accessModifier);
    }

    private CriteriaResult checkSingleField(Answer answer, GradingCriteria criteria,
                                             ClassOrInterfaceDeclaration cls,
                                             String fieldName, String fieldType, String accessModifier) {
        Optional<FieldDeclaration> fieldOpt = findFieldByName(cls, fieldName);
        if (fieldOpt.isEmpty()) {
            return fail(answer, criteria,
                    "Không tìm thấy thuộc tính " + fieldName + " trong class " + cls.getNameAsString() + ".");
        }

        FieldDeclaration field = fieldOpt.get();
        List<String> issues = new ArrayList<>();

        if (fieldType != null) {
            String actualType = field.getElementType().asString();
            if (!normalizeType(actualType).equals(normalizeType(fieldType))) {
                issues.add("kiểu dữ liệu là '" + actualType + "', yêu cầu '" + fieldType + "'");
            }
        }

        if (accessModifier != null) {
            if (!hasModifier(field, accessModifier)) {
                String actual = field.isPublic() ? "public"
                        : field.isProtected() ? "protected"
                        : field.isPrivate() ? "private" : "package-private";
                issues.add("phạm vi truy cập là '" + actual + "', yêu cầu '" + accessModifier + "'");
            }
        }

        if (issues.isEmpty()) {
            return pass(answer, criteria, "Thuộc tính " + fieldName + " được khai báo đúng.");
        }
        return fail(answer, criteria, "Thuộc tính " + fieldName + " chưa đúng: " + String.join(", ", issues) + ".");
    }

    private CriteriaResult checkMultipleFields(Answer answer, GradingCriteria criteria,
                                                ClassOrInterfaceDeclaration cls,
                                                List<Map<String, Object>> fields) {
        int total = fields.size();
        BigDecimal perField = criteria.getMaxScore()
                .divide(BigDecimal.valueOf(total), 6, RoundingMode.HALF_UP);

        List<String> passed = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        for (Map<String, Object> fieldDef : fields) {
            String name     = param(fieldDef, "fieldName");
            String type     = param(fieldDef, "fieldType");
            String modifier = param(fieldDef, "accessModifier");

            Optional<FieldDeclaration> opt = findFieldByName(cls, name);
            if (opt.isEmpty()) {
                failed.add(name + " (không tìm thấy)");
                continue;
            }

            FieldDeclaration field = opt.get();
            boolean ok = true;

            if (type != null && !normalizeType(field.getElementType().asString()).equals(normalizeType(type))) {
                ok = false;
                failed.add(name + " (kiểu dữ liệu sai, yêu cầu " + type + ")");
            }
            if (modifier != null && !hasModifier(field, modifier)) {
                ok = false;
                failed.add(name + " (phạm vi truy cập không phải " + modifier + ")");
            }

            if (ok) passed.add(name);
        }

        if (failed.isEmpty()) {
            return pass(answer, criteria, "Tất cả " + total + " thuộc tính đều đúng.");
        }
        if (passed.isEmpty()) {
            return fail(answer, criteria, "Các thuộc tính chưa đúng: " + String.join(", ", failed) + ".");
        }

        BigDecimal earned = perField.multiply(BigDecimal.valueOf(passed.size()))
                                    .setScale(4, RoundingMode.HALF_UP);
        String feedback = "Đúng " + passed.size() + "/" + total + " thuộc tính. "
                + "Các thuộc tính chưa đúng: " + String.join(", ", failed) + ".";
        return partial(answer, criteria, earned, feedback);
    }

    private Optional<FieldDeclaration> findFieldByName(ClassOrInterfaceDeclaration cls, String name) {
        return cls.getFields().stream()
                .filter(f -> f.getVariables().stream()
                        .anyMatch(v -> v.getNameAsString().equals(name)))
                .findFirst();
    }

    private boolean hasModifier(FieldDeclaration field, String modifier) {
        return switch (modifier.toLowerCase()) {
            case "private"   -> field.isPrivate();
            case "protected" -> field.isProtected();
            case "public"    -> field.isPublic();
            default          -> false;
        };
    }

    private String normalizeType(String type) {
        int idx = type.indexOf('<');
        return idx >= 0 ? type.substring(0, idx).trim() : type.trim();
    }
}

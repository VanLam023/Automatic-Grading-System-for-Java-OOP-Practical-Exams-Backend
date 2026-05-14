package agsfjope.backend.infrastructure.grading.criteria.handlers;

import agsfjope.backend.core.entities.Answer;
import agsfjope.backend.core.entities.CriteriaResult;
import agsfjope.backend.core.entities.GradingCriteria;
import agsfjope.backend.infrastructure.grading.criteria.AbstractCriterionHandler;
import agsfjope.backend.infrastructure.grading.criteria.SourceCodeContext;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * NAMING_CONVENTION — kiểm tra tên thuộc tính và/hoặc phương thức theo quy tắc camelCase.
 *
 * <p>Quy tắc camelCase trong Java:
 * <ul>
 *   <li><b>Thuộc tính / biến</b>: bắt đầu bằng chữ thường, chỉ gồm chữ cái và chữ số,
 *       ví dụ: {@code studentName}, {@code totalPrice}.</li>
 *   <li><b>Phương thức</b>: tương tự — bắt đầu bằng chữ thường,
 *       ví dụ: {@code getName()}, {@code calculateTotal()}.</li>
 *   <li><b>Hằng số</b> (static final): SCREAMING_SNAKE_CASE — luôn được bỏ qua.</li>
 * </ul>
 *
 * <p>Tham số bắt buộc: {@code className}</p>
 * <p>Tham số tùy chọn:
 * <ul>
 *   <li>{@code checkFields}  (boolean, mặc định {@code true})  — kiểm tra tên thuộc tính</li>
 *   <li>{@code checkMethods} (boolean, mặc định {@code false}) — kiểm tra tên phương thức</li>
 * </ul>
 */
@Component
public class NamingConventionHandler extends AbstractCriterionHandler {

    @Override
    public CriteriaResult check(SourceCodeContext context, GradingCriteria criteria, Answer answer) {
        Map<String, Object> params = criteria.getCheckParams();
        String className     = paramRequired(params, "className", criteria.getCriteriaCode());
        boolean checkFields  = paramBool(params, "checkFields",  true);
        boolean checkMethods = paramBool(params, "checkMethods", false);

        Optional<ClassOrInterfaceDeclaration> clsOpt = context.findClass(className);
        if (clsOpt.isEmpty()) {
            return fail(answer, criteria, "Không tìm thấy class " + className + ".");
        }

        ClassOrInterfaceDeclaration cls = clsOpt.get();
        List<String> fieldViolations  = new ArrayList<>();
        List<String> methodViolations = new ArrayList<>();

        // ── Kiểm tra tên thuộc tính ───────────────────────────────────────────
        if (checkFields) {
            for (FieldDeclaration field : cls.getFields()) {
                // Bỏ qua hằng số static final (SCREAMING_SNAKE_CASE chấp nhận được)
                if (field.isStatic() && field.isFinal()) continue;
                for (VariableDeclarator var : field.getVariables()) {
                    String name = var.getNameAsString();
                    if (!isCamelCase(name)) fieldViolations.add(name);
                }
            }
        }

        // ── Kiểm tra tên phương thức ──────────────────────────────────────────
        if (checkMethods) {
            for (MethodDeclaration method : cls.getMethods()) {
                String name = method.getNameAsString();
                if (!isCamelCase(name)) methodViolations.add(name);
            }
        }

        // ── Tổng hợp kết quả ──────────────────────────────────────────────────
        boolean hasFieldViolations  = !fieldViolations.isEmpty();
        boolean hasMethodViolations = !methodViolations.isEmpty();

        if (!hasFieldViolations && !hasMethodViolations) {
            StringBuilder msg = new StringBuilder();
            if (checkFields && checkMethods) {
                msg.append("Tất cả thuộc tính và phương thức trong class ").append(className)
                   .append(" đặt tên đúng theo quy tắc camelCase.");
            } else if (checkFields) {
                msg.append("Tất cả thuộc tính trong class ").append(className)
                   .append(" đặt tên đúng theo quy tắc camelCase.");
            } else {
                msg.append("Tất cả phương thức trong class ").append(className)
                   .append(" đặt tên đúng theo quy tắc camelCase.");
            }
            return pass(answer, criteria, msg.toString());
        }

        StringBuilder msg = new StringBuilder();
        if (hasFieldViolations) {
            msg.append("Các thuộc tính chưa đúng quy tắc camelCase: ")
               .append(String.join(", ", fieldViolations)).append(". ");
        }
        if (hasMethodViolations) {
            msg.append("Các phương thức chưa đúng quy tắc camelCase: ")
               .append(String.join(", ", methodViolations)).append(". ");
        }
        return fail(answer, criteria, msg.toString().trim());
    }

    /**
     * camelCase: bắt đầu bằng chữ thường, chỉ chứa chữ cái và chữ số.
     */
    private boolean isCamelCase(String name) {
        if (name == null || name.isEmpty()) return false;
        if (!Character.isLowerCase(name.charAt(0))) return false;
        return name.chars().allMatch(Character::isLetterOrDigit);
    }
}

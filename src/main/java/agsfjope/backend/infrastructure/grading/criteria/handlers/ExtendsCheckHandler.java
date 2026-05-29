package agsfjope.backend.infrastructure.grading.criteria.handlers;

import agsfjope.backend.core.entities.Answer;
import agsfjope.backend.core.entities.CriteriaResult;
import agsfjope.backend.core.entities.GradingCriteria;
import agsfjope.backend.infrastructure.grading.criteria.AbstractCriterionHandler;
import agsfjope.backend.infrastructure.grading.criteria.SourceCodeContext;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
public class ExtendsCheckHandler extends AbstractCriterionHandler {

    @Override
    public CriteriaResult check(SourceCodeContext context, GradingCriteria criteria, Answer answer) {
        Map<String, Object> params = criteria.getCheckParams();
        String className   = paramRequired(params, "className", criteria.getCriteriaCode());
        String parentClass = paramRequired(params, "parentClass", criteria.getCriteriaCode());

        Optional<ClassOrInterfaceDeclaration> clsOpt = context.findClass(className);
        if (clsOpt.isEmpty()) {
            return fail(answer, criteria, "Không tìm thấy class " + className + ".");
        }

        ClassOrInterfaceDeclaration cls = clsOpt.get();

        // Chuẩn hóa tên class cha cần kiểm tra (xóa mọi khoảng trắng để so sánh generics chính xác)
        String targetParentNormalized = parentClass.replaceAll("\\s+", "");

        boolean extendsCorrectly = cls.getExtendedTypes().stream()
                .anyMatch(ext -> {
                    String actualName = ext.getNameAsString(); // ví dụ: ArrayList
                    String actualFull = ext.toString();       // ví dụ: ArrayList<Book>
                    
                    return actualName.equalsIgnoreCase(parentClass.trim()) 
                           || actualFull.replaceAll("\\s+", "").equalsIgnoreCase(targetParentNormalized);
                });

        if (extendsCorrectly) {
            return pass(answer, criteria, "Class " + className + " kế thừa đúng từ " + parentClass + ".");
        }

        String actualExtends = cls.getExtendedTypes().stream()
                .map(ext -> ext.toString())
                .reduce((a, b) -> a + ", " + b)
                .orElse("không có");

        return fail(answer, criteria,
                "Class " + className + " chưa kế thừa từ " + parentClass + ". "
                + "Class hiện đang kế thừa: " + actualExtends + ".");
    }
}

package agsfjope.backend.infrastructure.grading.criteria.handlers;

import agsfjope.backend.core.entities.Answer;
import agsfjope.backend.core.entities.CriteriaResult;
import agsfjope.backend.core.entities.GradingCriteria;
import agsfjope.backend.infrastructure.grading.criteria.AbstractCriterionHandler;
import agsfjope.backend.infrastructure.grading.criteria.SourceCodeContext;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ImplementsCheckHandler extends AbstractCriterionHandler {

    @Override
    public CriteriaResult check(SourceCodeContext context, GradingCriteria criteria, Answer answer) {
        Map<String, Object> params  = criteria.getCheckParams();
        String className            = paramRequired(params, "className", criteria.getCriteriaCode());
        String interfaceName        = paramRequired(params, "interfaceName", criteria.getCriteriaCode());

        Optional<ClassOrInterfaceDeclaration> clsOpt = context.findClass(className);
        if (clsOpt.isEmpty()) {
            return fail(answer, criteria, "Không tìm thấy class " + className + ".");
        }

        ClassOrInterfaceDeclaration cls = clsOpt.get();

        boolean implementsCorrectly = cls.getImplementedTypes().stream()
                .anyMatch(impl -> impl.getNameAsString().equals(interfaceName));

        if (implementsCorrectly) {
            return pass(answer, criteria,
                    "Class " + className + " implements đúng interface " + interfaceName + ".");
        }

        List<String> actualImpls = cls.getImplementedTypes().stream()
                .map(impl -> impl.getNameAsString())
                .toList();

        return fail(answer, criteria,
                "Class " + className + " chưa implements interface " + interfaceName + ". "
                + (actualImpls.isEmpty()
                        ? "Chưa implements bất kỳ interface nào."
                        : "Các interface hiện tại: " + actualImpls + "."));
    }
}

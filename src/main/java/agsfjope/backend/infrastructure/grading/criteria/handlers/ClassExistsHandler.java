package agsfjope.backend.infrastructure.grading.criteria.handlers;

import agsfjope.backend.core.entities.Answer;
import agsfjope.backend.core.entities.CriteriaResult;
import agsfjope.backend.core.entities.GradingCriteria;
import agsfjope.backend.infrastructure.grading.criteria.AbstractCriterionHandler;
import agsfjope.backend.infrastructure.grading.criteria.SourceCodeContext;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ClassExistsHandler extends AbstractCriterionHandler {

    @Override
    public CriteriaResult check(SourceCodeContext context, GradingCriteria criteria, Answer answer) {
        Map<String, Object> params = criteria.getCheckParams();
        String className = paramRequired(params, "className", criteria.getCriteriaCode());

        if (context.hasClass(className)) {
            return pass(answer, criteria, "Đã tìm thấy class " + className + " trong mã nguồn.");
        }

        if (context.isJarAvailable()) {
            boolean existsInBytecode = context.loadedClass(className).isPresent();
            if (existsInBytecode) {
                boolean isIface = context.loadedClass(className)
                        .map(Class::isInterface).orElse(false);
                return pass(answer, criteria,
                        "Đã tìm thấy " + className + " là "
                        + (isIface ? "interface" : "class") + " được biên dịch sẵn (.class).");
            }
        }

        return fail(answer, criteria, "Không tìm thấy class " + className + " trong mã nguồn.");
    }
}

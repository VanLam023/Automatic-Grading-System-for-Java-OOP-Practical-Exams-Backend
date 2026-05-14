package agsfjope.backend.infrastructure.grading.criteria.handlers;

import agsfjope.backend.core.entities.Answer;
import agsfjope.backend.core.entities.CriteriaResult;
import agsfjope.backend.core.entities.GradingCriteria;
import agsfjope.backend.infrastructure.grading.criteria.AbstractCriterionHandler;
import agsfjope.backend.infrastructure.grading.criteria.SourceCodeContext;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class InterfaceExistsHandler extends AbstractCriterionHandler {

    @Override
    public CriteriaResult check(SourceCodeContext context, GradingCriteria criteria, Answer answer) {
        Map<String, Object> params = criteria.getCheckParams();

        String interfaceName = param(params, "interfaceName");
        if (interfaceName == null || interfaceName.isBlank()) {
            interfaceName = param(params, "className");
        }
        if (interfaceName == null || interfaceName.isBlank()) {
            throw new IllegalArgumentException(
                    "Criterion " + criteria.getCriteriaCode()
                    + " thiếu tham số bắt buộc 'interfaceName' (hoặc 'className')");
        }

        if (context.hasInterface(interfaceName)) {
            return pass(answer, criteria, "Đã tìm thấy interface " + interfaceName + " trong mã nguồn.");
        }

        if (context.isJarAvailable()) {
            boolean existsInJar = context.loadedClass(interfaceName)
                    .map(Class::isInterface)
                    .orElse(false);
            if (existsInJar) {
                return pass(answer, criteria,
                        "Đã tìm thấy interface " + interfaceName + " được biên dịch sẵn (.class).");
            }
        }

        return fail(answer, criteria,
                "Không tìm thấy interface " + interfaceName + " trong mã nguồn.");
    }
}

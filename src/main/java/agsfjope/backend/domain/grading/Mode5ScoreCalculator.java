package agsfjope.backend.domain.grading;

import agsfjope.backend.core.entities.GradingModeConfig;
import agsfjope.backend.infrastructure.grading.IsolatedStructureAnalyzer.CheckResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Isolated calculator for MODE_5 scores.
 */
@Slf4j
@Component
public class Mode5ScoreCalculator {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public record Mode5QuestionInput(
            int questionNumber,
            BigDecimal maxScore,
            int passTcCount,
            int totalTcCount,
            CheckResult structureResult
    ) {}

    public record Mode5Result(
            BigDecimal finalScore,
            BigDecimal tcComponent,
            BigDecimal structComponent,
            List<String> notes
    ) {}

    public Mode5Result calculate(GradingModeConfig config, List<Mode5QuestionInput> inputs) {
        BigDecimal sumTcRaw = BigDecimal.ZERO;
        BigDecimal sumStructRaw = BigDecimal.ZERO;
        List<String> notes = new ArrayList<>();

        for (Mode5QuestionInput input : inputs) {
            // TC Raw: (pass/total) * maxScore
            BigDecimal tcRaw = input.totalTcCount() == 0 ? BigDecimal.ZERO :
                    BigDecimal.valueOf(input.passTcCount())
                            .divide(BigDecimal.valueOf(input.totalTcCount()), 4, ROUNDING)
                            .multiply(input.maxScore());
            
            sumTcRaw = sumTcRaw.add(tcRaw);

            // Structure Raw
            BigDecimal structRaw = input.structureResult().calculateScore(input.maxScore());
            sumStructRaw = sumStructRaw.add(structRaw);

            if (!input.structureResult().details().isEmpty()) {
                notes.add("Q" + input.questionNumber() + " violations: " + 
                        String.join("; ", input.structureResult().details()));
            }
        }

        BigDecimal tcWeight = config.getTestCaseWeight().divide(new BigDecimal("100"), 4, ROUNDING);
        BigDecimal structWeight = config.getStructureWeight().divide(new BigDecimal("100"), 4, ROUNDING);

        BigDecimal tcTotal = sumTcRaw.multiply(tcWeight).setScale(SCALE, ROUNDING);
        BigDecimal structTotal = sumStructRaw.multiply(structWeight).setScale(SCALE, ROUNDING);
        BigDecimal finalScore = tcTotal.add(structTotal).setScale(SCALE, ROUNDING);

        return new Mode5Result(finalScore, tcTotal, structTotal, notes);
    }
}

package agsfjope.backend.application.dtos.requests.appeal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewAppealRequest {

    @NotNull(message = "Vui lòng nhập điểm số mới")
    @DecimalMin(value = "0.0", inclusive = true, message = "Điểm tối thiểu là 0.0")
    @DecimalMax(value = "10.0", inclusive = true, message = "Điểm tối đa là 10.0")
    private BigDecimal newScore;

    // Ví dụ: {"q1": 2.0, "q2": 2.5, "q3": 3.0}
    private java.util.Map<String, BigDecimal> newQuestionScores;

    @NotBlank(message = "Lý do/nhận xét chấm lại không được để trống")
    private String lecturerComment;
}

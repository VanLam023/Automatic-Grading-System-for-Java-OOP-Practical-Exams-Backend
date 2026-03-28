package agsfjope.backend.application.dtos.requests.appeal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request body khi sinh viên tạo đơn phúc khảo.
 *
 * <p>Sinh viên chỉ cần cung cấp submissionId (bài nộp cần phúc khảo)
 * và lý do phúc khảo. Phí sẽ được đọc từ {@code SystemConfigs}.</p>
 */
@Data
@NoArgsConstructor
public class CreateAppealRequest {

    /**
     * UUID bài nộp (Submission) cần phúc khảo.
     * Phải thuộc về sinh viên đang đăng nhập (BR-02).
     * Submission phải có status GRADED (BR-03).
     */
    @NotNull(message = "submissionId không được để trống")
    private UUID submissionId;

    /**
     * Lý do sinh viên muốn phúc khảo.
     * Bắt buộc, tối đa 2000 ký tự.
     */
    @NotBlank(message = "Lý do phúc khảo không được để trống")
    @Size(max = 2000, message = "Lý do phúc khảo không được vượt quá 2000 ký tự")
    private String reason;
}

package agsfjope.backend.application.dtos.requests.appeal;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request body để phân công giảng viên cho đơn phúc khảo.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignAppealRequest {

    @NotNull(message = "Vui lòng chọn giảng viên")
    private UUID lecturerId;

    @NotNull(message = "Vui lòng chọn deadline chấm phúc khảo")
    private java.time.OffsetDateTime deadlineAt;
}

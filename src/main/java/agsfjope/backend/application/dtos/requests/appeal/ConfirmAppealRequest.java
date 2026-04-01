package agsfjope.backend.application.dtos.requests.appeal;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body cho API xác nhận đơn phúc khảo (Exam Staff quyết định Approve hoặc Deny).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmAppealRequest {

    /** 
     * true = Approve (Cập nhật điểm)
     * false = Deny (Giữ nguyên điểm gốc)
     */
    @NotNull(message = "Vui lòng truyền quyết định approve/deny")
    private Boolean isApprove;
}

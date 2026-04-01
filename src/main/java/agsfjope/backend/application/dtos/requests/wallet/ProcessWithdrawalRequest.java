package agsfjope.backend.application.dtos.requests.wallet;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request Admin xử lý yêu cầu rút tiền (duyệt hoặc từ chối).
 */
@Data
public class ProcessWithdrawalRequest {

    @NotNull(message = "Quyết định không được để trống")
    private Boolean isApproved;

    /** Ghi chú của admin (bắt buộc khi từ chối) */
    private String adminNote;
}

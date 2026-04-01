package agsfjope.backend.application.dtos.requests.wallet;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Request nạp tiền vào ví.
 */
@Data
public class DepositRequest {

    @NotNull(message = "Số tiền nạp không được để trống")
    @DecimalMin(value = "10000", message = "Số tiền tối thiểu là 10,000 VND")
    private BigDecimal amount;

    @NotBlank(message = "returnUrl không được để trống")
    private String returnUrl;

    @NotBlank(message = "cancelUrl không được để trống")
    private String cancelUrl;
}

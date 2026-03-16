package agsfjope.backend.application.dtos.requests.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * DTO for updating PayOS integration configuration.
 */
@Data
public class UpdatePayosConfigRequest {

    /**
     * PayOS client ID. Sensitive credential.
     */
    @NotBlank(message = "Client ID không được để trống")
    private String clientId;

    /**
     * PayOS API key. Sensitive credential.
     */
    @NotBlank(message = "API key không được để trống")
    private String apiKey;

    /**
     * PayOS checksum key. Sensitive credential.
     */
    @NotBlank(message = "Checksum key không được để trống")
    private String checksumKey;

    /**
     * Appeal fee amount.
     */
    @NotNull(message = "Phí phúc khảo không được để trống")
    @Min(value = 0, message = "Phí phúc khảo phải lớn hơn hoặc bằng 0")
    private BigDecimal appealFee;

    /**
     * Payment timeout in minutes.
     */
    @NotNull(message = "Payment timeout không được để trống")
    @Min(value = 1, message = "Payment timeout phải lớn hơn hoặc bằng 1")
    private Integer paymentTimeoutMin;
}

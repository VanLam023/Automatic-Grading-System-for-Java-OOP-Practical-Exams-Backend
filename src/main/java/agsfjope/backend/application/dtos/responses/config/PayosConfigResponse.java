package agsfjope.backend.application.dtos.responses.config;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Response DTO for PayOS configuration data.
 */
@Data
@Builder
public class PayosConfigResponse {

    /**
     * Masked PayOS client ID.
     */
    private String clientIdMasked;

    /**
     * Masked PayOS API key.
     */
    private String apiKeyMasked;

    /**
     * Masked PayOS checksum key.
     */
    private String checksumKeyMasked;

    /**
     * Appeal fee amount.
     */
    private BigDecimal appealFee;

    /**
     * Payment timeout in minutes.
     */
    private Integer paymentTimeoutMin;
}

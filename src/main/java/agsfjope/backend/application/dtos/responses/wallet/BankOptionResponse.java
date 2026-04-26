package agsfjope.backend.application.dtos.responses.wallet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO trả về 1 ngân hàng để frontend hiển thị dropdown rút tiền.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankOptionResponse {
    private String code;
    private String name;
    private String shortName;
    private String bin;
    private String logo;
}

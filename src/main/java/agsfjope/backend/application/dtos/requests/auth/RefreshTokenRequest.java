package agsfjope.backend.application.dtos.requests.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO representing the request body for the Refresh Token API (SD_01_2).
 * Client sends the existing refresh token string to obtain a new access token.
 */
@Data
public class RefreshTokenRequest {

    /** The raw refresh token string previously issued at login. Cannot be blank. */
    @NotBlank(message = "Refresh token không được để trống")
    private String refreshToken;
}

package agsfjope.backend.application.dtos.responses.auth;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * DTO representing the response body returned after a successful Login.
 * Contains the issued JWT tokens and basic user information for the client to store.
 */
@Data
@Builder
public class LoginResponse {

    /** JWT Access Token - used to authenticate subsequent API requests. Valid for 4 hours. */
    private String accessToken;

    /** UUID Refresh Token - used to request a new access token when the current one expires. */
    private String refreshToken;

    /** Token type, always "Bearer" for JWT. */
    private String tokenType;

    /** Access Token validity in seconds (14400 = 4 hours). */
    private long expiresIn;

    /** The unique ID of the logged-in user. */
    private UUID userId;

    /** The full name of the logged-in user (for display in UI). */
    private String fullName;

    /** The role name of the logged-in user (e.g. "ADMIN", "STUDENT"). */
    private String roleName;
}

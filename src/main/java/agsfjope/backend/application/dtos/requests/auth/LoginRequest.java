package agsfjope.backend.application.dtos.requests.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO representing the request body for the Login API.
 * Contains user credentials sent from the client.
 */
@Data
public class LoginRequest {

    /** The user's unique username. Cannot be blank. */
    @NotBlank(message = "Tên đăng nhập không được để trống")
    private String username;

    /** The user's plain-text password. Cannot be blank. */
    @NotBlank(message = "Mật khẩu không được để trống")
    private String password;
}

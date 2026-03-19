package agsfjope.backend.application.dtos.responses.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO returned after Admin manually creates a single user account.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserResponse {

    private String username;
    private String email;
    private String fullName;
    private String roleName;
    private String mssv;

    /**
     * Always {@code false} on creation — user must click activation link to activate.
     */
    private boolean active;
}

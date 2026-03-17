package agsfjope.backend.infrastructure.security;

import agsfjope.backend.core.entities.User;
import agsfjope.backend.core.exceptions.auth.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utility class for retrieving the currently authenticated user from Spring Security context.
 * Used throughout the application layer to obtain the caller's User entity.
 */
public class SecurityUtils {

    private SecurityUtils() {
        // Utility class — do not instantiate
    }

    /**
     * Returns the {@link User} entity of the currently authenticated user.
     * Throws {@link UnauthorizedException} if no valid authentication is present in the context.
     *
     * @return currently authenticated User entity
     * @throws UnauthorizedException if the user is not authenticated
     */
    public static User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new UnauthorizedException("Người dùng chưa đăng nhập hoặc phiên đã hết hạn");
        }

        return userDetails.getUser();
    }
}

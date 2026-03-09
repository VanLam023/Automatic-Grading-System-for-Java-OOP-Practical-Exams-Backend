package agsfjope.backend.infrastructure.security;

import agsfjope.backend.core.entities.User;
import agsfjope.backend.core.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Implementation of Spring Security's UserDetailsService.
 * This is the bridge Spring Security uses to load a user from our database
 * when processing requests that have a JWT token in the Authorization header.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    /** Injected via Lombok @RequiredArgsConstructor */
    private final UserRepository userRepository;

    /**
     * Loads and returns a UserDetails object for the given username.
     * Called automatically by Spring Security's JwtAuthenticationFilter.
     * @param username the username extracted from the JWT token
     * @return a CustomUserDetails wrapping the found User entity
     * @throws UsernameNotFoundException if no user with the given username exists in the DB
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Query the database for the user by username
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy người dùng: " + username));

        // Wrap the User entity in our CustomUserDetails class for Spring Security
        return new CustomUserDetails(user);
    }
}

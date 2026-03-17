package agsfjope.backend.infrastructure.security.jwt;

import agsfjope.backend.infrastructure.security.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT Authentication Filter — intercepts every incoming HTTP request exactly ONCE.
 * Checks the Authorization header for a Bearer JWT token.
 * If the token is valid, it sets the authentication context so Spring Security
 * knows the current request is made by an authenticated user.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;

    /**
     * Core filter logic executed for every single incoming HTTP request.
     * Flow:
     *   1. Read the Authorization header.
     *   2. Extract the Bearer token if present.
     *   3. Validate the token using JwtTokenProvider.
     *   4. If valid, load the UserDetails from DB and set the security context.
     *   5. Pass the request down the filter chain regardless of the result.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Step 1: Read the Authorization header from the request
        final String authHeader = request.getHeader("Authorization");

        // Step 2: If header is missing or doesn't start with "Bearer ", skip this filter
        // This allows public endpoints (like /api/auth/login) to pass through without a token
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Step 3: Extract the JWT token by removing "Bearer " prefix (7 characters)
        final String jwt = authHeader.substring(7);

        // Step 4: Wrap in try-catch — expired or malformed tokens must NOT crash the filter chain.
        // For public endpoints (e.g. /auth/login), Spring Security will still allow the request through.
        try {
            final String username = jwtTokenProvider.extractUsername(jwt);

            // Only proceed to authenticate if username is found AND user is not already authenticated
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                // Load the full UserDetails from the database to verify the user still exists and is active
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // Validate the token against the loaded user (checks signature and expiry)
                if (jwtTokenProvider.isTokenValid(jwt)) {

                    // Create a Spring Security authentication token with the user's details and authorities
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null, // credentials are null because we already verified via JWT
                            userDetails.getAuthorities()
                    );

                    // Attach request-level details (IP address, session ID, etc.) to the authentication object
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // Set the authentication object in the Spring Security context for this request
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (JwtException | IllegalArgumentException e) {
            // Token is expired, malformed, or has an invalid signature.
            // Do NOT set authentication — Spring Security will treat this as an unauthenticated request.
            // For permitAll() endpoints (login, register), the request still proceeds normally.
            SecurityContextHolder.clearContext();
        } catch (Exception e) {
            // Any unexpected error in loading user details/auth context should not crash request processing.
            // Clear security context so downstream security rules can return 401/403 instead of 500.
            SecurityContextHolder.clearContext();
        }

        // Step 5: Always pass the request to the next filter in the chain
        filterChain.doFilter(request, response);
    }
}

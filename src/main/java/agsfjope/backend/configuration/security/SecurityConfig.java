package agsfjope.backend.configuration.security;

import agsfjope.backend.infrastructure.security.CustomUserDetailsService;
import agsfjope.backend.infrastructure.security.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Main Spring Security configuration class.
 * Sets up stateless JWT-based authentication, defines which endpoints are public vs protected,
 * and wires the JwtAuthenticationFilter into the security filter chain.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomUserDetailsService userDetailsService;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    /**
     * CORS configuration — allows the React frontend to call the API.
     * Allowed origins are loaded from application.yml / env vars.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Allow defined origins from .env, but also add common localhost ports to bypass CORS logic during dev
        config.setAllowedOriginPatterns(List.of("*")); 
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * Defines the HTTP security rules for all incoming requests.
     * Key decisions:
     * - CSRF disabled: Not needed because the API is stateless (no browser sessions).
     * - Stateless session: No server-side session created; each request must carry its own JWT.
     * - Public endpoints: Login API and Swagger UI are accessible without authentication.
     * - Protected endpoints: All other routes require a valid Bearer JWT token.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Enable CORS with our custom configuration
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // Disable CSRF protection — not needed for REST APIs with JWT
            .csrf(AbstractHttpConfigurer::disable)

            // Configure authorization rules per URL pattern
            .authorizeHttpRequests(auth -> auth
                // Public endpoints: no token required
                .requestMatchers(
                    "/api/auth/login",              // Login endpoint
                    "/api/auth/refresh",            // Refresh Token endpoint
                    "/api/auth/register",           // Student self-registration
                    "/api/auth/verify-account",     // Email activation link
                    "/api/auth/forgot-password",    // Forgot Password — send reset email
                    "/api/auth/verify-reset-token", // Verify reset token validity
                    "/api/auth/reset-password",     // Confirm new password
                    "/swagger-ui/**",               // Swagger UI
                    "/swagger-ui.html",
                    "/v3/api-docs/**",              // OpenAPI JSON spec
                    "/api-docs/**",
                    "/error"                        // Spring Boot default error routing
                ).permitAll()
                // /api/auth/logout requires a valid Bearer JWT (JwtAuthenticationFilter must pass first)
                .requestMatchers("/api/auth/logout").authenticated()
                // All other endpoints require authentication (valid JWT token)
                .anyRequest().authenticated()
            )

            // Set session management to STATELESS — Spring Security will NOT create HTTP sessions
            // Every request must be self-contained with a valid JWT token
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Register our custom authentication provider (loads users from DB via UserDetailsService)
            .authenticationProvider(authenticationProvider())

            // Insert JwtAuthenticationFilter BEFORE the default Spring Security username/password filter
            // This ensures JWT is validated first before any other authentication attempt
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Configures the authentication provider that Spring Security uses to verify credentials.
     * Uses DaoAuthenticationProvider which loads user from DB via UserDetailsService
     * and compares passwords using BCryptPasswordEncoder.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        // Tell Spring Security how to load users from DB
        provider.setUserDetailsService(userDetailsService);
        // Tell Spring Security how to hash/compare passwords
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * Exposes the AuthenticationManager as a Spring Bean.
     * Required by AuthServiceImpl to programmatically authenticate users.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Defines BCrypt as the password hashing algorithm.
     * Every password stored in the DB must be hashed with BCrypt.
     * Used in AuthServiceImpl to call passwordEncoder.matches(rawPassword, hashedPassword).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

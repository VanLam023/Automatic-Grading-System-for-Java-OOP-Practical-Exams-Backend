package agsfjope.backend.infrastructure.security.jwt;

import agsfjope.backend.core.entities.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Provides utility methods for generating and validating JSON Web Tokens (JWT).
 * Handles both Access Token (short-lived JWT) and Refresh Token (long-lived UUID).
 */
@Component
public class JwtTokenProvider {

    /** Secret key used to sign the JWT, loaded from .env -> application.yml */
    @Value("${JWT_SECRET}")
    private String jwtSecret;

    /** Access token validity in milliseconds (4 hours = 4 * 60 * 60 * 1000) */
    private static final long ACCESS_TOKEN_EXPIRY_MS = 4 * 60 * 60 * 1000L;

    /** Access token validity in seconds (used in LoginResponse.expiresIn field) */
    public static final long ACCESS_TOKEN_EXPIRY_SECONDS = 4 * 60 * 60L;

    /**
     * Builds the HMAC-SHA signing key from the raw secret string from .env file.
     * @return a Key object used by jjwt to sign and verify tokens
     */
    private Key getSigningKey() {
        // Convert the plain-text secret into a valid cryptographic key for HMAC-SHA256
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    /**
     * Generates a JWT Access Token for the given user.
     * The token contains the username as the subject, and user ID + role as custom claims.
     * Signed with HMAC-SHA256 and valid for 4 hours.
     * @param user the authenticated User entity
     * @return a signed JWT string
     */
    public String generateAccessToken(User user) {
        // Build custom claims to embed inside the JWT payload
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getUserId().toString());
        claims.put("role", user.getRole().getName());

        return Jwts.builder()
                .setClaims(claims)
                // Subject of the token is the username
                .setSubject(user.getUsername())
                // Token issue time
                .setIssuedAt(new Date(System.currentTimeMillis()))
                // Expiry time: current time + 4 hours
                .setExpiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRY_MS))
                // Sign the token with our secret key using HMAC-SHA256 algorithm
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Generates a Refresh Token as a random UUID string.
     * The actual UUID is stored in the DB (in the RefreshTokens table) for validation later.
     * @return a random UUID string representing the refresh token
     */
    public String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }

    /**
     * Extracts the username (subject claim) from a given JWT Access Token.
     * @param token the raw JWT string from the Authorization header
     * @return the username embedded in the token
     */
    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    /**
     * Validates a JWT Access Token by checking its signature and expiry date.
     * @param token the raw JWT string
     * @return true if the token is valid, false otherwise
     */
    public boolean isTokenValid(String token) {
        try {
            // parseClaimsJws will throw an exception if the token is expired or signature is invalid
            extractAllClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Internal helper that parses a JWT string and extracts all claims from the payload.
     * Throws an exception if the token is malformed, expired, or signature is invalid.
     * @param token the raw JWT string
     * @return Claims object containing all payload data
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Activation Token (used for email verification during registration)
    // ─────────────────────────────────────────────────────────────────────────

    /** Activation token validity: 24 hours */
    private static final long ACTIVATION_TOKEN_EXPIRY_MS = 24 * 60 * 60 * 1000L;

    /**
     * Generates a short-lived JWT to be embedded in the email-verification link.
     * The token subject is the user's email address.
     * Token type claim is "activation" to distinguish it from access tokens.
     *
     * @param email the registering user's FPT email address
     * @return a signed JWT string valid for 24 hours
     */
    public String generateActivationToken(String email) {
        Map<String, Object> claims = new HashMap<>();
        // Mark as activation token to avoid being mistakenly accepted as access token
        claims.put("token_type", "activation");

        return Jwts.builder()
                .setClaims(claims)
                // Store the email as the subject so we can look up the user on verification
                .setSubject(email)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                // Valid for 24 hours from the moment of registration
                .setExpiration(new Date(System.currentTimeMillis() + ACTIVATION_TOKEN_EXPIRY_MS))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Parses an activation token and extracts the email address (subject claim).
     * Will throw an exception if the token is expired or has an invalid signature.
     *
     * @param token the activation JWT from the email link query param
     * @return the email address embedded as the token subject
     */
    public String getEmailFromActivationToken(String token) {
        // extractAllClaims already throws if expired or invalid
        return extractAllClaims(token).getSubject();
    }
}

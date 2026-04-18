package agsfjope.backend.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class CookieUtils {

    @Value("${app.security.cookie.secure:false}")
    private boolean secureCookie; // false for localhost HTTP, true for HTTPS in production

    @Value("${app.security.cookie.same-site:Lax}")
    private String sameSite;

    public ResponseCookie createAccessTokenCookie(String token, long maxAgeSeconds) {
        return ResponseCookie.from("accessToken", token)
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .maxAge(maxAgeSeconds)
                .sameSite(sameSite)
                .build();
    }

    public ResponseCookie createRefreshTokenCookie(String token, long maxAgeDays) {
        long maxAgeSeconds = maxAgeDays * 24 * 60 * 60;
        return ResponseCookie.from("refreshToken", token)
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .maxAge(maxAgeSeconds)
                .sameSite(sameSite)
                .build();
    }

    public ResponseCookie[] clearCookies() {
        ResponseCookie accessCookie = ResponseCookie.from("accessToken", "")
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .maxAge(0) // 0 means delete
                .sameSite(sameSite)
                .build();

        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .maxAge(0)
                .sameSite(sameSite)
                .build();

        return new ResponseCookie[]{accessCookie, refreshCookie};
    }
}

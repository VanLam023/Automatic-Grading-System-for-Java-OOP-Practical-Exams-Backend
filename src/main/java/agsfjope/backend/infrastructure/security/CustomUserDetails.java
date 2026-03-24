package agsfjope.backend.infrastructure.security;

import agsfjope.backend.core.entities.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Wraps our custom User entity inside a Spring Security-compatible UserDetails object.
 * Spring Security requires every authenticated user to be represented through UserDetails.
 * By creating this wrapper, we bridge our domain User entity with Spring Security's system.
 */
public class CustomUserDetails implements UserDetails {

    /** The actual User entity from the database */
    private final User user;
    private final String authority;

    public CustomUserDetails(User user) {
        this.user = user;
        // Resolve role name immediately to avoid LazyInitialization issues later in the security filter chain.
        String roleName = (user.getRole() != null) ? user.getRole().getName() : "";
        this.authority = "ROLE_" + roleName;
    }

    /**
     * Returns the list of roles/permissions the user has.
     * Spring Security uses this to control access to endpoints via @PreAuthorize, etc.
     * Convention: prefix role names with "ROLE_" so Spring Security recognises them.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Wrap the single role name into a GrantedAuthority list
        return List.of(new SimpleGrantedAuthority(authority));
    }

    /** Returns the hashed password stored in the DB for Spring Security to compare against */
    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    /** Returns the username from the DB as the primary identifier for Spring Security */
    @Override
    public String getUsername() {
        return user.getUsername();
    }

    /** Account is considered non-expired as long as it exists in the DB */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * Returns true ONLY if the account is NOT locked.
     * Tied to the isLocked field in the User entity from the database.
     */
    @Override
    public boolean isAccountNonLocked() {
        return !Boolean.TRUE.equals(user.getIsLocked());
    }

    /** Credentials (password) are considered non-expired */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * Returns true ONLY if the account has been activated/verified.
     * Tied to the isActive field in the User entity from the database.
     */
    @Override
    public boolean isEnabled() {
        return Boolean.TRUE.equals(user.getIsActive());
    }

    /** Exposes the underlying User entity to other classes like AuthServiceImpl */
    public User getUser() {
        return user;
    }
}

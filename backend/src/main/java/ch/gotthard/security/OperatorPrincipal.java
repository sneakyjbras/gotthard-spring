package ch.gotthard.security;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Adapts {@link OperatorSummary} to Spring Security's {@link UserDetails} contract. This — not
 * {@link ch.gotthard.domain.model.Operator} — is what becomes the session's {@code Authentication}
 * principal: it deliberately carries no password hash, so nothing worth stealing sits in the
 * session store for as long as the session lives.
 */
record OperatorPrincipal(OperatorSummary operator) implements UserDetails {

    @Override
    public String getUsername() {
        return operator.username();
    }

    /** Never consulted: authentication happens in {@link OperatorAuthenticationProvider} before this type exists. */
    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + operator.role()));
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}

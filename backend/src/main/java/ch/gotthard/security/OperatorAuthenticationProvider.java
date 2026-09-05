package ch.gotthard.security;

import ch.gotthard.domain.model.Operator;
import ch.gotthard.domain.model.OperatorCredentials;
import ch.gotthard.domain.repository.OperatorCredentialsRepository;
import java.util.Optional;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Verifies operator credentials without ever letting the password hash leave this class:
 * {@link OperatorLookupRepository} resolves identity by username, {@link
 * OperatorCredentialsRepository} resolves the hash by the operator id that returned, and only the
 * resulting {@link OperatorPrincipal} — never the hash — becomes the authenticated principal that
 * gets persisted into the session.
 */
@Component
final class OperatorAuthenticationProvider implements AuthenticationProvider {

    /**
     * A real argon2id hash of a value nobody typed, fed through {@link PasswordEncoder#matches} on
     * every login attempt against a username that does not exist. Without it, an unknown username
     * would skip the (deliberately slow) Argon2 verification entirely, and the response time alone
     * would tell an attacker which usernames are real.
     */
    private static final String UNKNOWN_USER_GUARD_HASH =
            "$argon2id$v=19$m=16384,t=2,p=1$Rk6Q+GrfcIVa7ZU9kCsULA$xIFgthFrpXxUezbF/vkiz5+P15LGmRErusQY1YaEESw";

    private final OperatorLookupRepository operators;
    private final OperatorCredentialsRepository credentials;
    private final PasswordEncoder passwordEncoder;

    OperatorAuthenticationProvider(
            OperatorLookupRepository operators,
            OperatorCredentialsRepository credentials,
            PasswordEncoder passwordEncoder) {
        this.operators = operators;
        this.credentials = credentials;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName();
        String rawPassword = String.valueOf(authentication.getCredentials());
        Operator operator = verify(username, rawPassword);
        OperatorPrincipal principal = new OperatorPrincipal(OperatorSummary.from(operator));
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private Operator verify(String username, String rawPassword) {
        Optional<Operator> operator = operators.findByUsername(username);
        String hash = operator.flatMap(this::hashFor).orElse(UNKNOWN_USER_GUARD_HASH);
        if (!passwordEncoder.matches(rawPassword, hash) || operator.isEmpty()) {
            throw new BadCredentialsException("Invalid username or password");
        }
        return operator.get();
    }

    private Optional<String> hashFor(Operator operator) {
        return credentials.findById(operator.getOperatorId()).map(OperatorCredentials::getPasswordHash);
    }
}

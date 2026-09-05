package ch.gotthard.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The cookie-backed CSRF token is loaded lazily: nothing writes the {@code XSRF-TOKEN} cookie until
 * something reads {@link CsrfToken#getToken()}. This filter forces that read on every request, so
 * the cookie is always present for the SPA to pick up — most importantly on the first {@code
 * GET /api/auth/csrf} bootstrap call, before any session exists.
 *
 * <p>Registered after {@code BasicAuthenticationFilter} so it runs later than Spring Security's own
 * {@code CsrfFilter}, which is what actually places the deferred {@link CsrfToken} on the request in
 * the first place.
 */
final class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            csrfToken.getToken();
        }
        filterChain.doFilter(request, response);
    }
}

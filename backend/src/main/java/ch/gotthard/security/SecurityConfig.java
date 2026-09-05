package ch.gotthard.security;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.boot.web.server.Cookie;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.server.servlet.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Operator authentication: session-based login backed by argon2id-verified credentials — see
 * {@link OperatorAuthenticationProvider} for hashing, {@link AuthController} for the endpoints this
 * wires up.
 *
 * <p><b>Hashed, never encrypted.</b> {@link #passwordEncoder()} is Spring Security's {@code
 * Argon2PasswordEncoder}; encryption is reversible and this is a bank.
 *
 * <p><b>CSRF stays on, deliberately.</b> The session cookie below is {@code SameSite=Strict}, which
 * already blocks the cross-site form submissions CSRF exists to stop, in every modern browser. But
 * {@code SameSite} is a client-side promise this server cannot verify, and a bank's internal tool is
 * exactly the case defence in depth is for. The double-submit cookie ({@link
 * CookieCsrfTokenRepository}) costs one bootstrap call ({@code GET /api/auth/csrf}, permitted below)
 * and adds a second, server-checked layer that does not depend on every client's cookie handling
 * being correct.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** argon2id via Spring Security's own recommended parameters. Never MD5/SHA/BCrypt, and never reversible encryption. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    AuthenticationManager authenticationManager(OperatorAuthenticationProvider operatorAuthenticationProvider) {
        return new ProviderManager(operatorAuthenticationProvider);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository securityContextRepository)
            throws Exception {
        http.securityContext(context -> context.securityContextRepository(securityContextRepository))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/api/auth/login")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf")
                        .permitAll()
                        .requestMatchers(EndpointRequest.to("health"))
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(handling -> handling.authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))
                .logout(logout -> logout.logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                        .deleteCookies("JSESSIONID")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true));
        return http.build();
    }

    /** A REST API has no login page to redirect to, so an unauthenticated request gets 401 and a small JSON body instead of Spring Security's default redirect. */
    @Bean
    AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, exception) ->
                writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
    }

    /** Authenticated but not permitted — distinct from {@link #authenticationEntryPoint()}, which is for not being authenticated at all. */
    @Bean
    AccessDeniedHandler accessDeniedHandler() {
        return (request, response, exception) ->
                writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Access denied");
    }

    /**
     * {@code HttpOnly} and {@code SameSite=Strict} on the session cookie. Mutates the live {@code
     * Session} object the server already holds, rather than replacing it, so nothing else the
     * platform configures on it is overwritten.
     */
    @Bean
    WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> sessionCookiePolicy() {
        return factory -> {
            Cookie cookie = factory.getSettings().getSession().getCookie();
            cookie.setHttpOnly(true);
            cookie.setSameSite(Cookie.SameSite.STRICT);
        };
    }

    private static void writeJsonError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"%s\"}".formatted(message));
    }
}

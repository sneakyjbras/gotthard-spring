package ch.gotthard.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.gotthard.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Exercises the real HTTP contract against a real PostgreSQL container (Flyway needs to run
 * {@code V1__baseline.sql} and {@code V2__seed_operators.sql}, and the schema needs pgvector):
 * login through the actual {@link OperatorAuthenticationProvider} against the argon2id hashes the
 * seed migration writes, session persistence across requests, logout, and rejection of
 * unauthenticated access to a protected endpoint.
 *
 * <p>Every state-changing request goes through the real {@code GET /api/auth/csrf} bootstrap, the
 * same round trip the SPA performs — no test-only CSRF bypass.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
final class AuthenticationIntegrationTest {

    private static final String OPERATOR_USERNAME = "e.rossi";
    private static final String OPERATOR_PASSWORD = "Operator-Demo-2026";
    private static final String SUPERVISOR_USERNAME = "m.keller";
    private static final String SUPERVISOR_PASSWORD = "Supervisor-Demo-2026";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void given_correctPassword_when_login_then_authenticatesAndReturnsTheOperator() throws Exception {
        mockMvc.perform(loginRequest(OPERATOR_USERNAME, OPERATOR_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(OPERATOR_USERNAME))
                .andExpect(jsonPath("$.displayName").value("Elena Rossi"))
                .andExpect(jsonPath("$.role").value("OPERATOR"));
    }

    @Test
    void given_wrongPassword_when_login_then_rejected() throws Exception {
        mockMvc.perform(loginRequest(OPERATOR_USERNAME, "not-the-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid username or password"));
    }

    @Test
    void given_unknownUsername_when_login_then_rejected() throws Exception {
        mockMvc.perform(loginRequest("nobody", "whatever-2026"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid username or password"));
    }

    @Test
    void given_noSession_when_accessingProtectedEndpoint_then_rejected() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Authentication required"));
    }

    @Test
    void given_loggedInSession_when_me_then_returnsThatSameOperator() throws Exception {
        MockHttpSession session = login(SUPERVISOR_USERNAME, SUPERVISOR_PASSWORD);

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(SUPERVISOR_USERNAME))
                .andExpect(jsonPath("$.role").value("SUPERVISOR"));
    }

    @Test
    void given_loggedInSession_when_logout_then_sessionNoLongerAuthenticatesMe() throws Exception {
        MockHttpSession session = login(OPERATOR_USERNAME, OPERATOR_PASSWORD);
        String csrfToken = fetchCsrfToken();

        mockMvc.perform(post("/api/auth/logout")
                        .session(session)
                        .cookie(new Cookie("XSRF-TOKEN", csrfToken))
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(loginRequest(username, password))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }

    private MockHttpServletRequestBuilder loginRequest(String username, String password) throws Exception {
        String csrfToken = fetchCsrfToken();
        return post("/api/auth/login")
                .cookie(new Cookie("XSRF-TOKEN", csrfToken))
                .header("X-XSRF-TOKEN", csrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"%s","password":"%s"}
                        """
                        .formatted(username, password));
    }

    private String fetchCsrfToken() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        return cookie.getValue();
    }
}

package ch.gotthard.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.gotthard.TestcontainersConfiguration;
import ch.gotthard.ai.retrieval.EmbeddingModel;
import ch.gotthard.domain.model.PolicyChunk;
import ch.gotthard.domain.query.QueryFixtures;
import ch.gotthard.domain.repository.PolicyChunkRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * The three analysis routes over real HTTP, through the real filter chain, against a real database.
 *
 * <p>Nothing is mocked and there is no test-only bypass: the session comes from the same {@code
 * POST /api/auth/login} the console performs, and the operator recorded on the analysis is the one
 * that session carries — the assertion worth making, because attribution taken from a request body
 * would be attribution a client could forge.
 *
 * <p>CSRF is left on rather than disabled, so what these tests exercise is the sequence the console
 * actually performs: fetch the token, then send it as cookie and header on every write.
 *
 * <p>The key is pinned empty for the same reason as in {@code AiAnalysisServiceTest}: the adapter is
 * chosen from a property, and a developer's exported {@code ANTHROPIC_API_KEY} must never turn a
 * test run into a billed API call.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "gotthard.ai.api-key=")
@Transactional
class AnalysisApiIntegrationTest {

    private static final OffsetDateTime ANCHOR = OffsetDateTime.parse("2026-03-15T12:00:00Z");
    private static final String OPERATOR_USERNAME = "e.rossi";
    private static final String OPERATOR_PASSWORD = "Operator-Demo-2026";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PolicyChunkRepository policyChunks;

    @Autowired
    private EmbeddingModel embeddingModel;

    private QueryFixtures fixtures;

    /** Same reasoning as the service test: the corpus is seeded as text, and retrieval needs vectors. */
    @BeforeEach
    void openJdbcAccessAndEmbedTheCorpus() {
        fixtures = new QueryFixtures(new NamedParameterJdbcTemplate(dataSource));
        final List<PolicyChunk> unembedded = policyChunks.findByEmbeddingIsNull();
        unembedded.forEach(chunk -> chunk.setEmbedding(embeddingModel.embed(chunk.getBody())));
        policyChunks.saveAll(unembedded);
        policyChunks.flush();
    }

    /**
     * The CSRF token is supplied deliberately. Without it the write is stopped by {@code CsrfFilter}
     * with a 403 before authentication is ever consulted — true, but it would prove the wrong thing.
     * A well-formed write with no session is what actually shows the route is closed by default.
     */
    @Test
    void given_noSession_when_runningAnAnalysis_then_theRequestIsRefused() throws Exception {
        final String csrfToken = fetchCsrfToken();

        mockMvc.perform(post("/api/customers/{id}/analysis", UUID.randomUUID())
                        .cookie(new Cookie("XSRF-TOKEN", csrfToken))
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Authentication required"));
    }

    /** And a write with a session but no token is still refused — CSRF is not switched off for these routes. */
    @Test
    void given_aSessionButNoCsrfToken_when_runningAnAnalysis_then_theWriteIsStillRefused() throws Exception {
        mockMvc.perform(post("/api/customers/{id}/analysis", UUID.randomUUID()).session(login().http()))
                .andExpect(status().isForbidden());
    }

    @Test
    void given_noSession_when_readingTheHistory_then_theRequestIsRefused() throws Exception {
        mockMvc.perform(get("/api/customers/{id}/analyses", UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void given_noSession_when_readingOneAnalysis_then_theRequestIsRefused() throws Exception {
        mockMvc.perform(get("/api/analyses/{id}", UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void given_aLoggedInOperator_when_runningAnAnalysis_then_itComesBackAttributedToThatOperator() throws Exception {
        enableStructuringRule();
        final UUID customerId = structuringCustomer();

        mockMvc.perform(analysis(customerId, login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisId").exists())
                .andExpect(jsonPath("$.customer.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.requestedBy.username").value(OPERATOR_USERNAME))
                .andExpect(jsonPath("$.computedScore").value(30.00))
                .andExpect(jsonPath("$.computedLevel").value("MEDIUM"))
                .andExpect(jsonPath("$.assessedLevel").value("MEDIUM"))
                .andExpect(jsonPath("$.levelsDiverged").value(false))
                .andExpect(jsonPath("$.provider").value("stub"))
                .andExpect(jsonPath("$.summary").isNotEmpty())
                .andExpect(jsonPath("$.recommendations").isNotEmpty())
                .andExpect(jsonPath("$.citations").isNotEmpty());
    }

    @Test
    void given_twoAnalysesOfOneCustomer_when_readingTheHistory_then_theNewestIsFirst() throws Exception {
        enableStructuringRule();
        final UUID customerId = structuringCustomer();
        final Session session = login();
        final String first = analysisId(customerId, session);
        final String second = analysisId(customerId, session);

        mockMvc.perform(get("/api/customers/{id}/analyses", customerId).session(session.http()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].analysisId").value(second))
                .andExpect(jsonPath("$[1].analysisId").value(first))
                .andExpect(jsonPath("$[0].citations").isEmpty());
    }

    @Test
    void given_anAnalysisId_when_readingIt_then_theCitedPolicyComesWithIt() throws Exception {
        enableStructuringRule();
        final Session session = login();
        final String analysisId = analysisId(structuringCustomer(), session);

        mockMvc.perform(get("/api/analyses/{id}", analysisId).session(session.http()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisId").value(analysisId))
                .andExpect(jsonPath("$.promptVersion").isNotEmpty())
                .andExpect(jsonPath("$.citations[0].rank").value(1))
                .andExpect(jsonPath("$.citations[0].document").isNotEmpty())
                .andExpect(jsonPath("$.citations[0].body").isNotEmpty());
    }

    @Test
    void given_anAnalysisIdThatNamesNothing_when_readingIt_then_itIsNotFound() throws Exception {
        mockMvc.perform(get("/api/analyses/{id}", UUID.randomUUID()).session(login().http()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void given_anIdentifierThatIsNotAUuid_when_runningAnAnalysis_then_itIsARequestError() throws Exception {
        final Session session = login();

        mockMvc.perform(post("/api/customers/{id}/analysis", "not-a-uuid")
                        .cookie(new Cookie("XSRF-TOKEN", session.csrfToken()))
                        .header("X-XSRF-TOKEN", session.csrfToken())
                        .session(session.http()))
                .andExpect(status().isBadRequest());
    }

    private String analysisId(final UUID customerId, final Session session) throws Exception {
        final MvcResult result = mockMvc.perform(analysis(customerId, session))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.analysisId");
    }

    private static MockHttpServletRequestBuilder analysis(final UUID customerId, final Session session) {
        return post("/api/customers/{id}/analysis", customerId)
                .param("from", ANCHOR.minusDays(30).toString())
                .param("to", ANCHOR.toString())
                .cookie(new Cookie("XSRF-TOKEN", session.csrfToken()))
                .header("X-XSRF-TOKEN", session.csrfToken())
                .session(session.http());
    }

    private Session login() throws Exception {
        final String csrfToken = fetchCsrfToken();
        final MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .cookie(new Cookie("XSRF-TOKEN", csrfToken))
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}"""
                                .formatted(OPERATOR_USERNAME, OPERATOR_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        final MockHttpSession httpSession =
                (MockHttpSession) result.getRequest().getSession(false);
        assertThat(httpSession).isNotNull();
        return new Session(httpSession, csrfToken);
    }

    private String fetchCsrfToken() throws Exception {
        final Cookie cookie = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isNoContent())
                .andReturn()
                .getResponse()
                .getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        return cookie.getValue();
    }

    private void enableStructuringRule() {
        fixtures.clearRiskRules();
        fixtures.riskRule("R-01", "Near-threshold structuring", "ALL", "30.00", true);
    }

    private UUID structuringCustomer() {
        final UUID customer = fixtures.customer("CH");
        fixtures.payment(customer, ANCHOR.minusDays(6), "9500.00", "ACC-A", "CH");
        fixtures.payment(customer, ANCHOR.minusDays(4), "9600.00", "ACC-A", "CH");
        fixtures.payment(customer, ANCHOR.minusDays(2), "9700.00", "ACC-A", "CH");
        fixtures.payment(customer, ANCHOR, "9800.00", "ACC-A", "CH");
        return customer;
    }

    /** What one logged-in operator needs to make a write: the session, and the token that goes with it. */
    private record Session(MockHttpSession http, String csrfToken) {}
}

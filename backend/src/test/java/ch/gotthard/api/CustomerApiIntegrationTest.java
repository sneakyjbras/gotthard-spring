package ch.gotthard.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.gotthard.TestcontainersConfiguration;
import ch.gotthard.domain.query.QueryFixtures;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * The three routes over real HTTP, against a real database, through the real filter chain.
 *
 * <p>Nothing is mocked and no test-only bypass is used: the session comes from the same {@code
 * POST /api/auth/login} the console performs, and the numbers come from the same window functions
 * everything else uses. What this proves that the slice cannot is that the endpoints are protected
 * by default — {@code SecurityConfig} authenticates anything not explicitly permitted, so a route
 * added without a thought about security is closed rather than open.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class CustomerApiIntegrationTest {

    private static final OffsetDateTime ANCHOR = OffsetDateTime.parse("2026-03-15T12:00:00Z");
    private static final String OPERATOR_USERNAME = "e.rossi";
    private static final String OPERATOR_PASSWORD = "Operator-Demo-2026";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    private QueryFixtures fixtures;

    @BeforeEach
    void openJdbcAccess() {
        fixtures = new QueryFixtures(new NamedParameterJdbcTemplate(dataSource));
    }

    @Test
    void given_noSession_when_gettingACustomer_then_theRequestIsRefused() throws Exception {
        mockMvc.perform(get("/api/customers/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Authentication required"));
    }

    @Test
    void given_noSession_when_gettingActivity_then_theRequestIsRefused() throws Exception {
        mockMvc.perform(get("/api/customers/{id}/activity", UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void given_noSession_when_gettingRisk_then_theRequestIsRefused() throws Exception {
        mockMvc.perform(get("/api/customers/{id}/risk", UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void given_aLoggedInOperator_when_searchingByReference_then_theCustomerComesBack() throws Exception {
        final UUID customerId = fixtures.customer("CH");

        mockMvc.perform(get("/api/customers/{reference}", fixtures.referenceOf(customerId))
                        .session(login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.country").value("CH"));
    }

    @Test
    void given_aLoggedInOperator_when_searchingForNobody_then_itIsNotFound() throws Exception {
        mockMvc.perform(get("/api/customers/{reference}", "CH-0000-0000").session(login()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    /**
     * Two card authorisations of 20 and 30 and a 1 000 euro transfer at 0.94: three transactions,
     * 990 francs, worked out by hand and asserted as a literal.
     */
    @Test
    void given_aCustomerWithActivity_when_gettingActivity_then_theOverviewIsInTheReportingCurrency() throws Exception {
        final UUID customerId = fixtures.customer("CH");
        fixtures.card(customerId, ANCHOR.minusDays(5), "20.00", "Alpha", "5411", true, null);
        fixtures.card(customerId, ANCHOR.minusDays(1), "30.00", "Beta", "5411", true, null);
        fixtures.payment(customerId, ANCHOR.minusDays(2), "1000.00", "EUR", "ACC-E", "DE");

        mockMvc.perform(get("/api/customers/{id}/activity", customerId)
                        .param("from", ANCHOR.minusDays(30).toString())
                        .param("to", ANCHOR.toString())
                        .session(login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionCount").value(3))
                .andExpect(jsonPath("$.totalVolume.amount").value(990.00))
                .andExpect(jsonPath("$.totalVolume.currency").value("CHF"))
                .andExpect(jsonPath("$.recentTransactions[0].counterparty").value("Beta"));
    }

    /**
     * The structuring fixture from {@code RiskEvaluationServiceTest}, over HTTP: four payments to one
     * beneficiary, R-01 weighted at 30, two transactions completing the pattern.
     */
    @Test
    void given_aCustomerWorthFlagging_when_gettingRisk_then_theScoreAndFindingsComeBack() throws Exception {
        fixtures.clearRiskRules();
        fixtures.riskRule("R-01", "Near-threshold structuring", "ALL", "30.00", true);
        final UUID customerId = fixtures.customer("CH");
        fixtures.payment(customerId, ANCHOR.minusDays(6), "9500.00", "ACC-A", "CH");
        fixtures.payment(customerId, ANCHOR.minusDays(4), "9600.00", "ACC-A", "CH");
        fixtures.payment(customerId, ANCHOR.minusDays(2), "9700.00", "ACC-A", "CH");
        fixtures.payment(customerId, ANCHOR, "9800.00", "ACC-A", "CH");

        mockMvc.perform(get("/api/customers/{id}/risk", customerId)
                        .param("from", ANCHOR.minusDays(30).toString())
                        .param("to", ANCHOR.toString())
                        .session(login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(30.00))
                .andExpect(jsonPath("$.level").value("MEDIUM"))
                .andExpect(jsonPath("$.transactionsEvaluated").value(4))
                .andExpect(jsonPath("$.findings.length()").value(2));

        assertThat(fixtures.countAssessments()).isEqualTo(2);
    }

    @Test
    void given_anIdentifierThatIsNotAUuid_when_gettingRisk_then_itIsARequestError() throws Exception {
        mockMvc.perform(get("/api/customers/{id}/risk", "not-a-uuid").session(login()))
                .andExpect(status().isBadRequest());
    }

    private MockHttpSession login() throws Exception {
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
        final MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
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
}

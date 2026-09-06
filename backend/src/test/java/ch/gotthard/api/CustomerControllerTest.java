package ch.gotthard.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.gotthard.core.model.ActivityType;
import ch.gotthard.core.model.Money;
import ch.gotthard.core.model.RiskLevel;
import ch.gotthard.service.ActivityOverview;
import ch.gotthard.service.ActivityOverviewService;
import ch.gotthard.service.ActivityWindow;
import ch.gotthard.service.ChannelActivity;
import ch.gotthard.service.CustomerNotFoundException;
import ch.gotthard.service.CustomerRiskReport;
import ch.gotthard.service.CustomerSearchService;
import ch.gotthard.service.CustomerView;
import ch.gotthard.service.RiskEvaluationService;
import ch.gotthard.service.RiskFinding;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The web layer on its own: routing, parameter binding, status codes and the JSON that comes back.
 *
 * <p>The use cases are mocked, which is the point — this suite proves what the controller does with
 * an answer, not what the answer is. What the answer is has its own tests, against a real database.
 *
 * <p>Filters are off here so the slice tests routing rather than the filter chain; that every route
 * needs an authenticated session is proved against the real chain in {@code
 * CustomerApiIntegrationTest}.
 */
@WebMvcTest(CustomerController.class)
@AutoConfigureMockMvc(addFilters = false)
class CustomerControllerTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final OffsetDateTime ONBOARDED_AT = OffsetDateTime.parse("2024-01-05T09:30:00Z");
    private static final OffsetDateTime WINDOW_FROM = OffsetDateTime.parse("2026-03-01T00:00:00Z");
    private static final OffsetDateTime WINDOW_TO = OffsetDateTime.parse("2026-03-15T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerSearchService customerSearch;

    @MockitoBean
    private ActivityOverviewService activityOverview;

    @MockitoBean
    private RiskEvaluationService riskEvaluation;

    @Test
    void given_aKnownCustomer_when_gettingThatCustomer_then_theIdentityComesBack() throws Exception {
        given(customerSearch.find("CH-4410-8821")).willReturn(customer());

        mockMvc.perform(get("/api/customers/CH-4410-8821"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(CUSTOMER_ID.toString()))
                .andExpect(jsonPath("$.reference").value("CH-4410-8821"))
                .andExpect(jsonPath("$.fullName").value("Jane Doe"));
    }

    @Test
    void given_anUnknownCustomer_when_gettingThatCustomer_then_itIsNotFound() throws Exception {
        willThrow(new CustomerNotFoundException("CH-0000-0000"))
                .given(customerSearch)
                .find("CH-0000-0000");

        mockMvc.perform(get("/api/customers/CH-0000-0000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("No customer with id or reference CH-0000-0000"));
    }

    @Test
    void given_noWindow_when_gettingActivity_then_theDefaultLookbackIsApplied() throws Exception {
        given(activityOverview.overview(any(), any())).willReturn(overview());

        mockMvc.perform(get("/api/customers/{id}/activity", CUSTOMER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionCount").value(5))
                .andExpect(jsonPath("$.totalVolume.amount").value(6990.00))
                .andExpect(jsonPath("$.totalVolume.currency").value("CHF"))
                .andExpect(jsonPath("$.channels[0].channel").value("CARD"));

        final ArgumentCaptor<ActivityWindow> window = ArgumentCaptor.forClass(ActivityWindow.class);
        verify(activityOverview).overview(any(), window.capture());
        assertDefaultLookback(window.getValue());
    }

    @Test
    void given_bothEndsOfAWindow_when_gettingActivity_then_theyReachTheUseCaseUnchanged() throws Exception {
        given(activityOverview.overview(any(), any())).willReturn(overview());

        mockMvc.perform(get("/api/customers/{id}/activity", CUSTOMER_ID)
                        .param("from", WINDOW_FROM.toString())
                        .param("to", WINDOW_TO.toString()))
                .andExpect(status().isOk());

        final ArgumentCaptor<ActivityWindow> window = ArgumentCaptor.forClass(ActivityWindow.class);
        verify(activityOverview).overview(any(), window.capture());
        assertThat(window.getValue()).isEqualTo(new ActivityWindow(WINDOW_FROM, WINDOW_TO));
    }

    @Test
    void given_aWindowThatEndsBeforeItStarts_when_gettingActivity_then_itIsARequestError() throws Exception {
        mockMvc.perform(get("/api/customers/{id}/activity", CUSTOMER_ID)
                        .param("from", WINDOW_TO.toString())
                        .param("to", WINDOW_FROM.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void given_anIdentifierThatIsNotAUuid_when_gettingActivity_then_itIsARequestError() throws Exception {
        mockMvc.perform(get("/api/customers/not-a-uuid/activity"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("'not-a-uuid' is not a valid customerId"));
    }

    @Test
    void given_aScoredCustomer_when_gettingRisk_then_theScoreLevelAndFindingsComeBack() throws Exception {
        given(riskEvaluation.evaluate(any(), any())).willReturn(report());

        mockMvc.perform(get("/api/customers/{id}/risk", CUSTOMER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(30.00))
                .andExpect(jsonPath("$.level").value("MEDIUM"))
                .andExpect(jsonPath("$.transactionsEvaluated").value(4))
                .andExpect(jsonPath("$.findings[0].ruleCode").value("R-01"))
                .andExpect(jsonPath("$.findings[0].contribution").value(30.00));
    }

    @Test
    void given_anUnknownCustomer_when_gettingRisk_then_itIsNotFound() throws Exception {
        willThrow(new CustomerNotFoundException(CUSTOMER_ID.toString()))
                .given(riskEvaluation)
                .evaluate(any(), any());

        mockMvc.perform(get("/api/customers/{id}/risk", CUSTOMER_ID)).andExpect(status().isNotFound());
    }

    private static void assertDefaultLookback(final ActivityWindow window) {
        assertThat(window.from()).isEqualTo(window.to().minus(ActivityWindow.DEFAULT_LOOKBACK));
    }

    private static CustomerView customer() {
        return new CustomerView(CUSTOMER_ID, "CH-4410-8821", "Jane Doe", "CH", "RETAIL", ONBOARDED_AT);
    }

    private static ActivityOverview overview() {
        return new ActivityOverview(
                customer(),
                WINDOW_FROM,
                WINDOW_TO,
                5,
                Money.of("CHF", "6990.00"),
                1,
                List.of(new ChannelActivity(ActivityType.CARD, 2, Money.of("CHF", "50.00"), 0, WINDOW_FROM, WINDOW_TO)),
                List.of());
    }

    private static CustomerRiskReport report() {
        return new CustomerRiskReport(
                customer(),
                WINDOW_FROM,
                WINDOW_TO,
                4,
                new BigDecimal("30.00"),
                RiskLevel.MEDIUM,
                List.of(new RiskFinding(
                        UUID.randomUUID(),
                        WINDOW_TO,
                        ActivityType.PAYMENT,
                        "R-01",
                        "Near-threshold structuring",
                        new BigDecimal("30.00"))));
    }
}

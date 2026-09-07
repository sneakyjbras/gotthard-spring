package ch.gotthard.ai.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import ch.gotthard.ai.retrieval.RetrievedChunk;
import ch.gotthard.core.model.RiskLevel;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Prompt assembly, with no context and no database — it is string building, and it should stay
 * cheap enough to test that way.
 *
 * <p>The ordering assertions are the ones that matter. Caching is a prefix match, so "the
 * instructions come before the policy, which comes before anything that changes per call" is not a
 * cosmetic property of this class: get it wrong and every request pays full price for the whole
 * prompt, silently, with nothing failing.
 */
class AnalysisPromptAssemblerTest {

    private static final OffsetDateTime WINDOW_FROM = OffsetDateTime.parse("2026-02-13T12:00:00Z");
    private static final OffsetDateTime WINDOW_TO = OffsetDateTime.parse("2026-03-15T12:00:00Z");

    @Test
    void given_anyRequest_when_assembled_then_thePromptCarriesTheVersionThatWillBePersisted() {
        assertThat(AnalysisPromptAssembler.assemble(request()).version()).isEqualTo(AnalysisPromptAssembler.VERSION);
    }

    /** {@code ai_analyses.prompt_version} is VARCHAR(16); a longer constant would fail at insert time. */
    @Test
    void given_theVersionConstant_when_measured_then_itFitsTheColumnItIsStoredIn() {
        assertThat(AnalysisPromptAssembler.VERSION).isNotBlank().hasSizeLessThanOrEqualTo(16);
    }

    @Test
    void given_aRequest_when_assembled_then_stableTextPrecedesVolatileTextSoThePrefixCanCache() {
        final AnalysisPrompt prompt = AnalysisPromptAssembler.assemble(request());
        final String rendered = prompt.rendered();

        assertThat(rendered.indexOf(prompt.instructions()))
                .isLessThan(rendered.indexOf(prompt.policy()))
                .isNotNegative();
        assertThat(rendered.indexOf(prompt.policy())).isLessThan(rendered.indexOf(prompt.signals()));
    }

    @Test
    void given_aRequest_when_assembled_then_theInstructionsMentionNoCustomerFigureOrTimestamp() {
        final AnalysisPrompt prompt = AnalysisPromptAssembler.assemble(request());

        assertThat(prompt.instructions()).doesNotContain("CH-7002-4488", "2026-02-13", "76.00", "Sandra Wyss");
    }

    @Test
    void given_aFiredRule_when_assembled_then_itsHumanReadableThresholdLogicIsInThePrompt() {
        assertThat(AnalysisPromptAssembler.assemble(request()).signals())
                .contains("R-01")
                .contains("Near-Threshold Structuring")
                .contains("Condition: Three or more transactions just under the reporting threshold");
    }

    @Test
    void given_retrievedPolicy_when_assembled_then_eachChunkIsNumberedAndAttributedToItsDocument() {
        final String policy = AnalysisPromptAssembler.assemble(request()).policy();

        assertThat(policy)
                .contains("[1] AML-001 §3")
                .contains("Detection criteria")
                .contains("R-01");
        assertThat(policy).contains("fires when a customer places three or more near-threshold amounts");
    }

    @Test
    void given_aRequest_when_assembled_then_theSignalsStateTheWindowTheActivityAndTheComputedOutcome() {
        final String signals = AnalysisPromptAssembler.assemble(request()).signals();

        assertThat(signals).contains("CH-7002-4488", "Sandra Wyss", "RETAIL");
        assertThat(signals).contains("2026-02-13T12:00:00Z to 2026-03-15T12:00:00Z, 5 transactions evaluated");
        assertThat(signals).contains("| PAYMENT | 5 | CHF 47550.00 | 0 |");
        assertThat(signals).contains("Score 76.00, level CRITICAL");
    }

    /** A clean customer must produce an honest prompt, not one padded with unrelated policy. */
    @Test
    void given_nothingFiredAndNothingRetrieved_when_assembled_then_thePromptSaysSoRatherThanGoingQuiet() {
        final AnalysisPrompt prompt = AnalysisPromptAssembler.assemble(cleanRequest());

        assertThat(prompt.policy()).contains("No rule fired").contains("Do not cite any");
        assertThat(prompt.signals()).contains("None. Nothing in this window tripped a rule.");
    }

    @Test
    void given_aCustomerWithNoTransactions_when_assembled_then_theActivityBlockSaysNothingHappened() {
        assertThat(AnalysisPromptAssembler.assemble(cleanRequest()).signals())
                .contains("Nothing at all — the customer did not transact.");
    }

    private static AnalysisRequest request() {
        return new AnalysisRequest(
                new AnalysedCustomer("CH-7002-4488", "Sandra Wyss", "CH", "RETAIL"),
                WINDOW_FROM,
                WINDOW_TO,
                new ComputedRisk(new BigDecimal("76.00"), RiskLevel.CRITICAL, 5),
                List.of(new ChannelLine(
                        "PAYMENT", 5, "CHF", new BigDecimal("47550.00"), 0, WINDOW_TO.minusDays(6), WINDOW_TO)),
                List.of(new FiredRule(
                        "R-01",
                        "Near-Threshold Structuring",
                        "Three or more transactions just under the reporting threshold within 7 days,"
                                + " together totalling 10,000 or more.",
                        3,
                        new BigDecimal("44.00"),
                        WINDOW_TO)),
                List.of(new RetrievedChunk(
                        UUID.fromString("9859f823-dbb9-5923-ad6b-f77723698509"),
                        "AML-001",
                        "Detection criteria",
                        "3",
                        "R-01 fires when a customer places three or more near-threshold amounts within a"
                                + " rolling seven-day window.",
                        0.83,
                        "R-01")));
    }

    private static AnalysisRequest cleanRequest() {
        return new AnalysisRequest(
                new AnalysedCustomer("CH-7002-4471", "Livia Baumann", "CH", "RETAIL"),
                WINDOW_FROM,
                WINDOW_TO,
                new ComputedRisk(new BigDecimal("0.00"), RiskLevel.LOW, 0),
                List.of(),
                List.of(),
                List.of());
    }
}

package ch.gotthard.ai.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RuleQueryVocabularyTest {

    /** The seven rule codes the merged risk core defines — see core/risk/rules/StandardRules. */
    private static final List<String> KNOWN_RULE_CODES =
            List.of("R-01", "R-02", "R-03", "R-04", "R-05", "R-06", "R-07");

    @Test
    void given_everyKnownRuleCode_when_describe_then_returnsANonBlankPhrase() {
        KNOWN_RULE_CODES.forEach(ruleCode -> {
            Optional<String> described = RuleQueryVocabulary.describe(ruleCode);
            assertThat(described).as("query text for %s", ruleCode).isPresent();
            assertThat(described.orElseThrow()).isNotBlank();
        });
    }

    @Test
    void given_anUnknownRuleCode_when_describe_then_returnsEmpty() {
        assertThat(RuleQueryVocabulary.describe("R-99")).isEmpty();
    }

    @Test
    void given_everyKnownRuleCode_when_described_then_eachPhraseIsDistinct() {
        List<String> phrases = KNOWN_RULE_CODES.stream()
                .map(RuleQueryVocabulary::describe)
                .map(Optional::orElseThrow)
                .toList();

        assertThat(phrases).doesNotHaveDuplicates();
    }
}

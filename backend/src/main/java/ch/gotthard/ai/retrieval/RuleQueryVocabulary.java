package ch.gotthard.ai.retrieval;

import java.util.Map;
import java.util.Optional;

/**
 * What each fired rule code means, in the words a compliance analyst would use to look up the
 * relevant policy — the text {@link PgVectorKnowledgeRetriever} embeds and searches with.
 *
 * <p>This is deliberately a small, explicit, hand-written table rather than something read off {@code
 * risk_rules.threshold_logic} at query time, for two reasons. First, coupling: {@code risk_rules} is
 * seeded by a sibling migration this module does not own, and retrieval has to work correctly — and be
 * tested — without depending on another migration having already run. Second, {@code
 * ch.gotthard.core.risk.Rule}'s own Javadoc says the human-readable form of a rule's condition
 * "is this class's Javadoc" — there is no accessor for it at runtime, by design, so there is nothing to
 * read reflectively even if this class wanted to. A rule's Javadoc and its entry below describe the
 * same condition in two independent places, kept in step by convention — exactly how {@code
 * risk_rules.threshold_logic} already relates to the Java rule classes.
 *
 * <p>The phrasing below is not incidental: each entry deliberately echoes the vocabulary of the one
 * policy document written for that rule (see {@code docs/policy/}), because {@link
 * HashingEmbeddingModel} has no semantic understanding of its own — shared wording is the entire
 * retrieval signal.
 */
public final class RuleQueryVocabulary {

    private static final Map<String, String> QUERIES = Map.ofEntries(
            Map.entry(
                    "R-01",
                    "Structuring: three or more near-threshold amounts within seven days, split just"
                            + " below the reporting threshold and aggregating to it or more. Smurfing to evade"
                            + " currency transaction reporting."),
            Map.entry(
                    "R-02",
                    "Cross-border payment corridor reaching an elevated-risk jurisdiction: repeated"
                            + " transfers to the same high-risk destination country, or a single week's volume"
                            + " of five thousand or more to a beneficiary bank there."),
            Map.entry(
                    "R-03",
                    "Card-not-present fraud and card testing: three or more declined card-not-present"
                            + " authorisations within an hour, spread across two or more merchants, consistent"
                            + " with a stolen card number being validated."),
            Map.entry(
                    "R-04",
                    "Rapid exchange disposal: crypto moved on to an exchange within an hour of arriving"
                            + " in the wallet, with ten thousand or more leaving the wallet over the day — a"
                            + " pass-through wallet used for layering."),
            Map.entry(
                    "R-05",
                    "Flagged wallet proximity: the sending wallet sits within two hops of an address on"
                            + " the watch list in the blockchain transaction graph, including exposure through a"
                            + " mixer or tumbler used to break the trail."),
            Map.entry(
                    "R-06",
                    "Dormancy then burst: an account silent for ninety days suddenly transacts five or"
                            + " more times within twenty-four hours — the pattern of a taken-over or rented"
                            + " account reactivated after a long dormancy."),
            Map.entry(
                    "R-07",
                    "Quasi-cash concentration: spend at quasi-cash, money-transfer and gambling"
                            + " merchant category codes reaching two thousand five hundred or more within one"
                            + " day, a merchant category cash-out pattern."));

    private RuleQueryVocabulary() {}

    /** The canonical search phrase for a fired rule code, or empty for a code this table does not know. */
    public static Optional<String> describe(final String ruleCode) {
        return Optional.ofNullable(QUERIES.get(ruleCode));
    }
}
